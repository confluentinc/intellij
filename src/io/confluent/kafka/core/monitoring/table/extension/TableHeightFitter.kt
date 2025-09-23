package io.confluent.kafka.core.monitoring.table.extension

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.monitoring.table.DataTable
import io.confluent.kafka.core.monitoring.table.TableEventListener
import java.awt.Dimension
import javax.swing.JScrollPane
import javax.swing.JTable

class TableHeightFitter<T : RemoteInfo> private constructor(private val scrollPane: JScrollPane, private val table: DataTable<T>)
  : TableEventListener, Disposable {

  companion object {
    fun <T : RemoteInfo> installOn(scrollPane: JScrollPane, table: DataTable<T>) {
      val tableColumnsFitter = TableHeightFitter(scrollPane, table)
      Disposer.register(table, tableColumnsFitter)
    }

    fun fitSize(scrollPane: JScrollPane, table: JTable) {
      scrollPane.preferredSize = Dimension(scrollPane.preferredSize.width,
        table.tableHeader.preferredSize.height +
        // To prevent zero-height table and display at least empty text.
        if (table.preferredSize.height == 0) table.rowHeight + scrollPane.horizontalScrollBar.preferredSize.height
        else table.preferredSize.height +
             scrollPane.horizontalScrollBar.preferredSize.height +
             scrollPane.insets.top +
             scrollPane.insets.bottom)
    }
  }

  init {
    table.tableModel.addListener(this)
  }

  //region TableEventListener
  override fun onChanged() = fitSize(scrollPane, table)
  //endregion TableEventListener

  override fun dispose() {
    table.tableModel.removeListener(this)
  }
}