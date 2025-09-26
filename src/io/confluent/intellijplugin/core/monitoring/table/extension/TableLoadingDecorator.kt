package io.confluent.intellijplugin.core.monitoring.table.extension

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.table.TableEventListener
import io.confluent.intellijplugin.core.table.MaterialTableUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Until first data comes, we will show Loading sign over the table, and then component is disposed.
 */
class TableLoadingDecorator<T : RemoteInfo> private constructor(private val table: DataTable<T>) : TableEventListener, Disposable {

  companion object {
    fun <T : RemoteInfo> installOn(table: DataTable<T>) {

      if (Disposer.isDisposed(table)) return

      if (table.tableModel.getDataModel()?.isInitedByFirstTime == true || table.tableModel.hasListenerOfClass(this.javaClass)) {
        return
      }
      val tableLoadingDecorator = TableLoadingDecorator(table)
      Disposer.register(table, tableLoadingDecorator)
    }

    private val states = arrayOf(KafkaMessagesBundle.message("table.loading") + ".  ",
                                 KafkaMessagesBundle.message("table.loading") + ".. ",
                                 KafkaMessagesBundle.message("table.loading") + "...")
  }

  private var ticker: ScheduledFuture<*>? = null

  private var currentIndex = 0

  init {
    MaterialTableUtils.fitColumnsWidth(table)
    table.tableModel.addListener(this)

    ticker = EdtExecutorService.getScheduledExecutorInstance()
      .scheduleWithFixedDelay({
                                currentIndex++
                                if (currentIndex >= states.size) {
                                  currentIndex = 0
                                }
                                table.emptyText.text = states[currentIndex]
                                table.emptyText.component.repaint()
                              }, 0, 400, TimeUnit.MILLISECONDS)
  }

  // region TableEventListener
  override fun onError(msg: String, e: Throwable?) = Disposer.dispose(this)
  override fun onChanged() = Disposer.dispose(this)
  // endregion TableEventListener

  override fun dispose() {
    ticker?.cancel(true)
    table.tableModel.removeListener(this)
  }
}