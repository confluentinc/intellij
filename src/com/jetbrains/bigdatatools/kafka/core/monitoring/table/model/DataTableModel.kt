package io.confluent.kafka.core.monitoring.table.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import io.confluent.kafka.core.monitoring.data.listener.DataModelListener
import io.confluent.kafka.core.monitoring.data.model.ObjectDataModel
import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.monitoring.table.TableEventListener
import io.confluent.kafka.core.table.DecoratableDataTableModel
import javax.swing.table.AbstractTableModel
import kotlin.reflect.KClass

class DataTableModel<T : RemoteInfo>(dataModel: ObjectDataModel<T>?, val columnModel: DataTableColumnModel<T>) :
  AbstractTableModel(), DecoratableDataTableModel, DataModelListener, Disposable {

  private var dataModel: ObjectDataModel<T>?
  private var listeners = listOf<TableEventListener>()

  init {
    this.dataModel = dataModel
    dataModel?.addListener(this)
    dataModelFirstInit(dataModel)
  }

  fun getDataModel(): ObjectDataModel<T>? = dataModel

  fun setDataModel(dataModel: ObjectDataModel<T>?) {
    beforeChanged()
    this.dataModel?.removeListener(this)
    this.dataModel = dataModel
    this.dataModel?.addListener(this)

    dataModelFirstInit(dataModel)
  }

  private fun dataModelFirstInit(dataModel: ObjectDataModel<T>?) {
    if (dataModel?.error != null)
      onError(dataModel.error?.message ?: "", dataModel.error?.cause)
    else
      onChanged()
  }

  fun hasListenerOfClass(cls: Class<Any>): Boolean {
    return listeners.any { it.javaClass == cls }
  }

  fun getFirstListenerOfClass(cls: Class<*>): TableEventListener? {
    return listeners.firstOrNull { it.javaClass == cls }
  }

  fun addListener(listener: TableEventListener) {
    listeners = listeners + listener
  }

  fun removeListener(listener: TableEventListener) {
    listeners = listeners - listener
  }

  override fun onChanged() {
    runInEdt {
      fireTableDataChanged()
    }
    listeners.forEach { it.onChanged() }
  }

  override fun beforeChanged() {
    listeners.forEach { it.beforeChanged() }
  }

  override fun onError(msg: String, e: Throwable?) {
    runInEdt {
      fireTableDataChanged()
    }
    listeners.forEach { it.onError(msg, e) }
  }

  //region DecoratableDataTableModel
  override fun getValueByColumnName(name: String, row: Int): Any? = getValueAt(row, columnModel.getModelIndex(name))
  //endregion DecoratableDataTableModel

  override fun getRowCount(): Int = dataModel?.size ?: 0

  override fun getColumnCount(): Int = columnModel.allColumns.size

  fun getInfoAt(rowIndex: Int): T? = dataModel?.get(rowIndex)

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
    val obj = dataModel?.get(rowIndex) ?: return null
    return columnModel.allColumns[columnIndex].field.get(obj)
  }

  fun getIndexById(id: String): Int? = dataModel?.data?.withIndex()?.find { dataModel?.getId(it.value) == id }?.index

  @Suppress("HardCodedStringLiteral") // Column name in nearly all cased are data driven.
  override fun getColumnName(column: Int): String = columnModel.getColumnName(column)

  override fun getColumnClass(columnIndex: Int): Class<*> {
    return (columnModel.allColumns[columnIndex].field.returnType.classifier as KClass<*>).javaObjectType
  }

  override fun dispose() {
    dataModel?.removeListener(this)
  }
}