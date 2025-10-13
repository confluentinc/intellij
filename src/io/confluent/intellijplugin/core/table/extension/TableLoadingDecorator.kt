package io.confluent.intellijplugin.core.table.extension

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.EdtExecutorService
import io.confluent.intellijplugin.core.table.MaterialTableUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.AbstractTableModel

class TableLoadingDecorator private constructor(private val table: JBTable, @Nls text: String) : TableModelListener,
    Disposable {

    companion object {
        fun installOn(
            table: JBTable, parentDisposable: Disposable,
            @Nls text: String = KafkaMessagesBundle.message("table.loading")
        ): TableLoadingDecorator? {

            return if (table.model.rowCount != 0 ||
                (table.model as? AbstractTableModel)?.getListeners(TableLoadingDecorator::class.java)
                    ?.isNotEmpty() == true
            ) {
                null
            } else {
                TableLoadingDecorator(table, text).apply {
                    Disposer.register(parentDisposable, this)
                }
            }
        }
    }

    private var ticker: ScheduledFuture<*>? = null

    private var currentIndex = 0

    private val states = arrayOf("$text.  ", "$text.. ", "$text...")

    private val originalEmptyText = table.emptyText.text

    init {
        MaterialTableUtils.fitColumnsWidth(table)
        table.model.addTableModelListener(this)

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

    override fun tableChanged(e: TableModelEvent?) = Disposer.dispose(this)

    override fun dispose() {
        ticker?.cancel(true)
        table.model.removeTableModelListener(this)
        table.emptyText.text = originalEmptyText
        table.emptyText.component.repaint()
    }
}