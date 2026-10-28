package io.confluent.intellijplugin.registry

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.model.DataModel
import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil
import io.confluent.intellijplugin.core.util.asSilent
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.registry.confluent.ConfluentRegistryClient
import io.confluent.intellijplugin.registry.serde.BdtAvroSchemaProvider
import io.confluent.intellijplugin.registry.serde.BdtJsonSchemaProvider
import io.confluent.intellijplugin.registry.serde.BdtProtobufSchemaProvider
import io.confluent.kafka.schemaregistry.AbstractSchemaProvider
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object KafkaRegistryUtil {
    /** Upper bound for a Schema Registry call awaited by the caller (client timeouts alone can add up across retries). */
    val CALL_TIMEOUT: Duration
        get() = BdIdeRegistryUtil.SCHEMA_REGISTRY_CALL_TIMEOUT_MS.milliseconds

    /**
     * Exposes a schema write as a [Promise]. A timeout is reported as [SchemaWriteTimeoutException], because the
     * request may already have reached the registry when the caller stopped waiting.
     */
    fun Deferred<Unit>.asSchemaWritePromise(): Promise<Unit> {
        val promise = AsyncPromise<Unit>().asSilent()
        invokeOnCompletion { cause ->
            when (cause) {
                null -> promise.setResult(Unit)
                is TimeoutCancellationException -> promise.setError(SchemaWriteTimeoutException(CALL_TIMEOUT, cause))
                else -> promise.setError(cause)
            }
        }
        return promise
    }

    /**
     * Runs [update] with a one-shot listener on [versionModel] that calls [onVersionsChanged]. The listener is dropped
     * again if the write fails, so a failed or timed out update doesn't leave it attached.
     */
    fun updateSchemaAwaitingNewVersion(
        versionModel: DataModel<*>,
        onVersionsChanged: () -> Unit,
        update: () -> Promise<Unit>,
    ): Promise<Unit> {
        val listener = object : DataModelListener {
            override fun onChanged() {
                onVersionsChanged()
                versionModel.removeListener(this)
            }
        }
        versionModel.addListener(listener)
        return update().onError { versionModel.removeListener(listener) }
    }

    fun getRegistrySchemaProviders(): List<AbstractSchemaProvider> =
        listOf(BdtAvroSchemaProvider(), BdtProtobufSchemaProvider(), BdtJsonSchemaProvider())

    val protobufLanguage: Language
        get() = Language.findLanguageByID("protobuf") ?: PlainTextLanguage.INSTANCE

    // We need to disable loggers in schemaregistry, because there was a lot ot error messages (for example AvroSchemaProvider) in case of exceptions,
    // while we are processing that exceptions on our own. And every error message in log produces IDE error notification.
    fun disableLoggers() {
        Logger.getLogger("io.confluent.intellijplugin.schemaregistry").level = Level.OFF
    }

    suspend fun getSchemaType(
        schemaName: String,
        dataManager: BaseClusterDataManager
    ): KafkaRegistryFormat? = dataManager.getCachedOrLoadSchema(schemaName).type

    @RequiresBackgroundThread
    suspend fun loadSchema(
        schemaName: String,
        fieldType: KafkaFieldType,
        dataManager: BaseClusterDataManager
    ): ParsedSchema? {
        if (fieldType !in KafkaFieldType.registryValues)
            return null

        val versionInfo = dataManager.getLatestVersionInfo(schemaName) ?: return null
        return dataManager.parseSchemaForDisplay(versionInfo).getOrThrow()
    }

    /**
     * Parse schema text using default schema providers.
     * Works without any registry client.
     */
    fun parseSchema(
        schemaType: KafkaRegistryFormat,
        newText: @NlsSafe String,
        references: List<SchemaReference> = emptyList()
    ): Result<ParsedSchema> = parseSchemaWithProviders(schemaType, newText, references, getRegistrySchemaProviders())

    /**
     * Parse schema text using schema providers from a ConfluentRegistryClient.
     * Falls back to default providers if client is null.
     */
    fun parseSchema(
        schemaType: KafkaRegistryFormat,
        newText: @NlsSafe String,
        client: ConfluentRegistryClient?,
        references: List<SchemaReference> = emptyList()
    ): Result<ParsedSchema> {
        val providers = client?.internalClient?.schemaProviders?.values ?: getRegistrySchemaProviders()
        return parseSchemaWithProviders(schemaType, newText, references, providers)
    }

    /** Parse schema text with pre-fetched resolved references used by CCloud path. */
    fun parseSchema(
        schemaType: KafkaRegistryFormat,
        newText: @NlsSafe String,
        references: List<SchemaReference>,
        resolvedReferences: Map<String, String>
    ): Result<ParsedSchema> {
        val providers = listOf(
            BdtAvroSchemaProvider(resolvedReferences),
            BdtProtobufSchemaProvider(resolvedReferences),
            BdtJsonSchemaProvider(resolvedReferences)
        )
        return parseSchemaWithProviders(schemaType, newText, references, providers)
    }

    private fun parseSchemaWithProviders(
        schemaType: KafkaRegistryFormat,
        newText: @NlsSafe String,
        references: List<SchemaReference>,
        providers: Collection<io.confluent.kafka.schemaregistry.SchemaProvider>
    ): Result<ParsedSchema> {
        return try {
            val provider = providers.firstOrNull {
                it.schemaType() == schemaType.name
            } ?: error("Schema type is not found $schemaType")

            val schema = Schema(null, null, null, schemaType.name, references, newText)
            val value = provider.parseSchemaOrElseThrow(schema, true, false)
            Result.success(value)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun getPrettySchema(schemaType: String, schema: String): String = if (schemaType == ProtobufSchema.TYPE) {
        schema
    } else {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonObject = gson.fromJson(schema.ifBlank { null } ?: "{}", JsonElement::class.java)
        gson.toJson(jsonObject)
    }

    fun parseRecordName(schema: ParsedSchema?): String? = schema?.name()
}