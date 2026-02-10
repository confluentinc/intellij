package io.confluent.intellijplugin.scaffold.client

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateList
import io.confluent.intellijplugin.scaffold.model.TypedTemplateListItem
import io.confluent.intellijplugin.scaffold.model.toTyped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * HTTP client for Confluent Scaffolding API operations.
 * No authentication required for public endpoints
 */
class ScaffoldHttpClient(
    private val baseUrl: String = "https://api.confluent.cloud",
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS
) {

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        const val READ_TIMEOUT_MS = 60_000 // 1 minute

        // Custom adapter for OffsetDateTime (RFC3339 format)
        private object OffsetDateTimeAdapter : JsonAdapter<OffsetDateTime>() {
            override fun fromJson(reader: com.squareup.moshi.JsonReader): OffsetDateTime? {
                return if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) {
                    reader.nextNull()
                } else {
                    OffsetDateTime.parse(reader.nextString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
            }

            override fun toJson(writer: com.squareup.moshi.JsonWriter, value: OffsetDateTime?) {
                if (value == null) {
                    writer.nullValue()
                } else {
                    writer.value(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                }
            }
        }

        // Custom factory to handle kotlin.Any fields (leaves them as raw types)
        private object AnyAdapterFactory : JsonAdapter.Factory {
            override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
                if (Types.getRawType(type) == Any::class.java) {
                    return object : JsonAdapter<Any>() {
                        override fun fromJson(reader: com.squareup.moshi.JsonReader): Any? {
                            return reader.readJsonValue()
                        }
                        override fun toJson(writer: com.squareup.moshi.JsonWriter, value: Any?) {
                            writer.jsonValue(value)
                        }
                    }
                }
                return null
            }
        }

        // Moshi JSON adapter for generated models
        // AnyAdapterFactory must come before KotlinJsonAdapterFactory to handle kotlin.Any fields
        val moshi = Moshi.Builder()
            .add(OffsetDateTime::class.java, OffsetDateTimeAdapter)
            .add(AnyAdapterFactory)
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Fetches templates from a specific template collection.
     *
     * @param collectionName The name of the template collection (default: "vscode")
     * @return List of templates in the collection
     * @throws HttpRequests.HttpStatusException if the server returns 4xx or 5xx status
     */
    suspend fun fetchTemplates(collectionName: String = "vscode"): Scaffoldv1TemplateList =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/scaffold/v1/template-collections/$collectionName/templates"
            thisLogger().debug("Fetching from URL: $url")

            val responseBody = HttpRequests.request(url)
                .connectTimeout(connectTimeoutMs)
                .readTimeout(readTimeoutMs)
                .throwStatusCodeException(false)
                .connect { request ->
                    readResponseBody(request)
                }

            thisLogger().debug("Received response (${responseBody.length} chars)")

            val adapter = moshi.adapter(Scaffoldv1TemplateList::class.java)
            val result = adapter.fromJson(responseBody) ?: throw IllegalStateException("Failed to parse template list response")

            thisLogger().debug("Parsed ${result.data.size} templates")
            result
        }

    /**
     * Read response body from the appropriate stream based on status code.
     * @param request The request to read the response body from
     * @return The response body as a string
     */
    private fun readResponseBody(request: HttpRequests.Request): String {
        val conn = request.connection as java.net.HttpURLConnection
        val statusCode = conn.responseCode

        if (statusCode >= 500) {
            val errorBody = conn.errorStream?.reader()?.readText()
            val message = if (errorBody.isNullOrBlank()) "Server error" else "Server error: $errorBody"
            throw HttpRequests.HttpStatusException(message, statusCode, conn.url.toString())
        }

        if (statusCode >= 400) {
            val errorBody = conn.errorStream?.reader()?.readText() ?: ""
            throw HttpRequests.HttpStatusException(errorBody, statusCode, conn.url.toString())
        }

        return request.inputStream.reader().readText()
    }

    /**
     * Fetches templates from a specific template collection with properly typed spec fields.
     *
     * The auto-generated models type the 'spec' field as kotlin.Any. This method converts
     * them to TypedTemplateListItem with properly typed Scaffoldv1TemplateSpec.
     *
     * @param collectionName The name of the template collection (default: "vscode")
     * @return List of templates with typed spec fields
     * @throws HttpRequests.HttpStatusException if the server returns 4xx or 5xx status
     */
    suspend fun fetchTypedTemplates(collectionName: String = "vscode"): List<TypedTemplateListItem> {
        val templateList = fetchTemplates(collectionName)
        return templateList.toTyped(moshi)
    }
}
