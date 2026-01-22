package io.confluent.intellijplugin.consumer.editor.webview

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Type-safe message protocol for Java ↔ JavaScript communication.
 * Similar to VS Code's typed message handlers in consume.ts:566-783
 */

// Messages from JavaScript to Kotlin
@Serializable
sealed class WebViewRequest {
    @Serializable
    data class GetMessages(val page: Int, val pageSize: Int) : WebViewRequest()

    @Serializable
    data class GetMessagesCount(val dummy: Boolean = true) : WebViewRequest()

    @Serializable
    data class SearchMessages(val search: String?) : WebViewRequest()

    @Serializable
    data class PartitionFilterChange(val partitions: List<Int>?) : WebViewRequest()

    @Serializable
    data class RowSelected(val index: Int) : WebViewRequest()

    @Serializable
    data class StreamPause(val dummy: Boolean = true) : WebViewRequest()

    @Serializable
    data class StreamResume(val dummy: Boolean = true) : WebViewRequest()
}

// Messages from Kotlin to JavaScript
@Serializable
sealed class WebViewResponse {
    @Serializable
    data class Messages(
        val indices: List<Int>,
        val messages: List<RecordDto>
    ) : WebViewResponse()

    @Serializable
    data class MessagesCount(
        val total: Int,
        val filtered: Int?
    ) : WebViewResponse()

    @Serializable
    data class Refresh(val success: Boolean) : WebViewResponse()

    @Serializable
    data class StateUpdate(
        val isRunning: Boolean,
        val isLoading: Boolean,
        val errorMessage: String?
    ) : WebViewResponse()
}

@Serializable
data class RecordDto(
    val topic: String,
    val timestamp: Long,
    val key: String?,
    val value: String?,
    val partition: Int,
    val offset: Long
)

@Serializable
data class WebViewMessage(
    val type: String,
    val payload: String
)

object MessageSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeRequest(request: WebViewRequest): String {
        val type = request::class.simpleName ?: "Unknown"
        val payload = json.encodeToString(request)
        return json.encodeToString(WebViewMessage(type, payload))
    }

    fun encodeResponse(response: WebViewResponse): String {
        val type = response::class.simpleName ?: "Unknown"
        val payload = json.encodeToString(response)
        return json.encodeToString(WebViewMessage(type, payload))
    }

    fun decodeRequest(message: String): WebViewRequest? {
        return try {
            val wrapper = json.decodeFromString<WebViewMessage>(message)
            when (wrapper.type) {
                "GetMessages" -> json.decodeFromString<WebViewRequest.GetMessages>(wrapper.payload)
                "GetMessagesCount" -> json.decodeFromString<WebViewRequest.GetMessagesCount>(wrapper.payload)
                "SearchMessages" -> json.decodeFromString<WebViewRequest.SearchMessages>(wrapper.payload)
                "PartitionFilterChange" -> json.decodeFromString<WebViewRequest.PartitionFilterChange>(wrapper.payload)
                "RowSelected" -> json.decodeFromString<WebViewRequest.RowSelected>(wrapper.payload)
                "StreamPause" -> json.decodeFromString<WebViewRequest.StreamPause>(wrapper.payload)
                "StreamResume" -> json.decodeFromString<WebViewRequest.StreamResume>(wrapper.payload)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
