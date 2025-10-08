package io.confluent.intellijplugin.consumer.models

import org.apache.kafka.clients.consumer.ConsumerRecord

data class ConsumerFilter(
    val filterKey: String?,
    val filterValue: String?,
    val filterHeadKey: String?,
    val filterHeadValue: String?,
    val type: ConsumerFilterType
) {

    fun isRecordPassFilter(record: ConsumerRecord<Any, Any>): Boolean =
        isPassFilter(record.key()?.toString(), filterKey) &&
                isPassFilter(record.value()?.toString(), filterValue) &&
                isPassFilterHeaders(record.headers()?.mapNotNull { it.key() ?: "" } ?: emptyList(), filterHeadKey) &&
                isPassFilterHeaders(record.headers()?.mapNotNull { it.value()?.decodeToString() } ?: emptyList(),
                    filterHeadValue)

    private fun isPassFilter(value: String?, filterValue: String?): Boolean {
        if (filterValue == null)
            return true

        return when (type) {
            ConsumerFilterType.NONE -> true
            ConsumerFilterType.CONTAINS -> value?.contains(filterValue) == true
            ConsumerFilterType.DOES_NOT_CONTAINS -> value?.contains(filterValue) == false
            ConsumerFilterType.REGEX -> value?.matches(Regex(filterValue)) == true
        }
    }

    private fun isPassFilterHeaders(value: List<String>, filterValue: String?): Boolean {
        if (filterValue == null)
            return true

        return when (type) {
            ConsumerFilterType.NONE -> true
            ConsumerFilterType.CONTAINS -> value.any { it.contains(filterValue) }
            ConsumerFilterType.DOES_NOT_CONTAINS -> value.all { !it.contains(filterValue) }
            ConsumerFilterType.REGEX -> value.any {
                val regex = Regex(filterValue)
                regex.matches(it)
            }
        }
    }
}