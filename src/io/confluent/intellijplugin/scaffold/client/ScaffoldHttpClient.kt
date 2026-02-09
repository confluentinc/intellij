package io.confluent.intellijplugin.scaffold.client

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * HTTP client for Confluent Scaffolding API operations.
 * No authentication required for public endpoints
 */
class ScaffoldHttpClient(private val baseUrl: String = "https://api.confluent.cloud") {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        private const val READ_TIMEOUT_MS = 60_000 // 1 minute

        // Custom adapter for OffsetDateTime (RFC3339 format)
        private class OffsetDateTimeAdapter {
            @FromJson
            fun fromJson(value: String?): OffsetDateTime? {
                return value?.let { OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
            }

            @ToJson
            fun toJson(value: OffsetDateTime?): String? {
                return value?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            }
        }

        // Moshi JSON adapter - required because generated models use @Json annotations
        // Note: The generated 'spec' field is typed as kotlin.Any, so it will be deserialized as a Map.
        // Consumers should use Moshi to convert the Map to the appropriate spec type (e.g., Scaffoldv1TemplateSpec).
        private val moshi = Moshi.Builder()
            .add(OffsetDateTimeAdapter())
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
                .connectTimeout(CONNECT_TIMEOUT_MS)
                .readTimeout(READ_TIMEOUT_MS)
                .throwStatusCodeException(true)
                .readString()

            thisLogger().debug("Received response (${responseBody.length} chars)")

            val adapter = moshi.adapter(Scaffoldv1TemplateList::class.java)
            val result = adapter.fromJson(responseBody) ?: throw IllegalStateException("Failed to parse template list response")

            thisLogger().debug("Parsed ${result.data.size} templates")
            result
        }
}
