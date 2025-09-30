package io.confluent.intellijplugin.core.monitoring.table

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.monitoring.table.model.DataTableColumnModel
import io.confluent.intellijplugin.core.monitoring.table.model.DataTableModel
import io.confluent.intellijplugin.core.table.MaterialTable
import java.awt.Color
import javax.swing.DefaultListSelectionModel

class DataTable<T : RemoteInfo>(model: DataTableModel<T>, columnModel: DataTableColumnModel<T>) : MaterialTable(model,
                                                                                                                columnModel), UserDataHolder {
  private val userData = UserDataHolderBase()

  init {
    selectionModel.selectionMode = DefaultListSelectionModel.SINGLE_SELECTION
    rowSelectionAllowed = true
    cellSelectionEnabled = false
    columnSelectionAllowed = false
  }

  override fun getSelectionRowBackground(): Color = UIUtil.getTableSelectionBackground(true)

  override fun getSelectionRowForeground(): Color = getSelectionForeground()

  override fun isColumnSelected(column: Int): Boolean = false

  @Suppress("UNCHECKED_CAST")
  val tableModel: DataTableModel<T>
    get() = model as DataTableModel<T>

  override fun <T : Any?> getUserData(key: Key<T>): T? = userData.getUserData(key)
  override fun <T : Any?> putUserData(key: Key<T>, value: T?) = userData.putUserData(key, value)

  fun getDataAt(row: Int): T? = if (row == -1) null else tableModel.getInfoAt(convertRowIndexToModel(row))
}

fun <T : RemoteInfo> DataTable<T>.getSelectedData(): T? = getDataAt(selectedRow)