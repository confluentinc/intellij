package io.confluent.intellijplugin.scaffold.client

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * HTTP client for Confluent Scaffolding API operations.
 * No authentication required for public endpoints
 *
 * Environment can be configured via:
 * - System property: -Dscaffold.api.env=[prod|stag|devel] (default: prod)
 * - Direct URL override: -Dscaffold.api.base-url=<url>
 */
class ScaffoldHttpClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS
) {

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        const val READ_TIMEOUT_MS = 60_000 // 1 minute

        private enum class ScaffoldEnv {
            PROD, STAG, DEVEL
        }

        private val envProperty: String = System.getProperty("scaffold.api.env") ?: "prod"

        private val env: ScaffoldEnv = when (envProperty) {
            "prod" -> ScaffoldEnv.PROD
            "stag" -> ScaffoldEnv.STAG
            "devel" -> ScaffoldEnv.DEVEL
            else -> error("Unknown environment: $envProperty. Valid values: prod, stag, devel")
        }

        private val basePath: String = when (env) {
            ScaffoldEnv.PROD -> "confluent.cloud"
            ScaffoldEnv.STAG -> "stag.cpdev.cloud"
            ScaffoldEnv.DEVEL -> "devel.cpdev.cloud"
        }

        val DEFAULT_BASE_URL: String
            get() = System.getProperty("scaffold.api.base-url") ?: "https://api.$basePath"

        // Custom adapter for OffsetDateTime (RFC3339 format)
        private object OffsetDateTimeAdapter : JsonAdapter<OffsetDateTime>() {
            override fun fromJson(reader: JsonReader): OffsetDateTime? {
                return if (reader.peek() == JsonReader.Token.NULL) {
                    reader.nextNull()
                } else {
                    OffsetDateTime.parse(reader.nextString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
            }

            override fun toJson(writer: JsonWriter, value: OffsetDateTime?) {
                if (value == null) {
                    writer.nullValue()
                } else {
                    writer.value(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                }
            }
        }

        // Custom adapter for URI
        private object UriAdapter : JsonAdapter<java.net.URI>() {
            override fun fromJson(reader: JsonReader): java.net.URI? {
                return if (reader.peek() == JsonReader.Token.NULL) {
                    reader.nextNull()
                } else {
                    java.net.URI(reader.nextString())
                }
            }

            override fun toJson(writer: JsonWriter, value: java.net.URI?) {
                if (value == null) {
                    writer.nullValue()
                } else {
                    writer.value(value.toString())
                }
            }
        }

        // Moshi JSON adapter for generated models
        val moshi = Moshi.Builder()
            .add(OffsetDateTime::class.java, OffsetDateTimeAdapter)
            .add(java.net.URI::class.java, UriAdapter)
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Fetches templates from a specific template collection.
     *
     * @param collectionName The name of the template collection (default: "vscode")
     * @return List of templates in the collection
     * @throws HttpRequests.HttpStatusException if the server returns 4xx or 5xx status
     * TODO: update param from "vscode" default to intellij
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

}
