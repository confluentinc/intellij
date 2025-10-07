package io.confluent.intellijplugin.core.monitoring.data.model

data class FieldGroupsData<T>(
    val obj: T?,
    val groups: List<Pair<String, FieldsDataModel>>
) {
    companion object {
        fun <T> empty() = FieldGroupsData<T>(null, emptyList())
    }
}