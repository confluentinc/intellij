package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.JsonClass

/**
 * Metadata for paginated list responses.
 */
@JsonClass(generateAdapter = true)
data class ListMetadata(
    val first: String? = null,
    val last: String? = null,
    val prev: String? = null,
    val next: String? = null,
    val total_size: Int? = null
)

