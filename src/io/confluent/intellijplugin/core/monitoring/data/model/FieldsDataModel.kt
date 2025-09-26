package io.confluent.intellijplugin.core.monitoring.data.model

import kotlin.reflect.KProperty1

data class FieldsDataModel(private val pairData: List<Pair<String, Any?>>, private val value: Any? = null)
  : ObjectDataModelBase<FieldData>(FieldData::name, allowAutoRefresh = false) {
  val renderableColumns: List<KProperty1<out Any, *>> = if (value != null) RemoteInfo.getProperties(value) else emptyList()

  operator fun get(s: String) = data?.firstOrNull { it.name == s }?.value

  override val updater: (DataModel<*>) -> Pair<List<FieldData>, Boolean> = {
    pairData.map { FieldData(it.first, it.second) }.filter { it.name.isBlank() || (it.value?.toString())?.isNotBlank() == true } to false
  }

  companion object {
    fun createForList(pairData: List<Pair<String, Any?>>) = FieldsDataModel(pairData).also { it.update() }

    fun createForObject(value: Any?): FieldsDataModel = if (value == null)
      FieldsDataModel(emptyList()).also { it.update() }
    else
      FieldsDataModel(RemoteInfo.getProperties(value).map { it.name to it.getter.call(value) }, value).also { it.update() }
  }
}