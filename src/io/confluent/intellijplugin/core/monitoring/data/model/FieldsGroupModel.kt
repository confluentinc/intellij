package io.confluent.intellijplugin.core.monitoring.data.model

class FieldsGroupModel<T>(
    allowAutoRefresh: Boolean = true,
    val updaterFun: (DataModel<*>) -> FieldGroupsData<T>
) : DataModel<FieldGroupsData<T>>(allowAutoRefresh) {
    override val updater: (DataModel<*>) -> Pair<FieldGroupsData<T>, Boolean> = { updaterFun(it) to false }
    val originObject: T?
        get() = data?.obj


    val size: Int
        get() = data?.groups?.size ?: 0

    val entries: List<Pair<String, FieldsDataModel>>
        get() = data?.groups ?: emptyList()

}