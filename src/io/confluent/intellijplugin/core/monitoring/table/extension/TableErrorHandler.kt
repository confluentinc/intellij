package io.confluent.intellijplugin.core.monitoring.table.extension

import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.HintHint
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.StatusText
import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.table.TableEventListener
import io.confluent.intellijplugin.core.util.toPresentableText
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Point
import javax.swing.JViewport

/**
 * If there was en error from data model, sets table.emptyText to error description.
 * Also updates empty text if data received or if data contains no elements.
 */
class TableErrorHandler<T : RemoteInfo> private constructor(
    private val table: DataTable<T>,
    private val customTableEventListener: TableEventListener? = null
) : TableEventListener, Disposable {
    companion object {
        val CUSTOM_EMPTY_TEXT_PROVIDER = Key<CustomEmptyTextProvider>("CUSTOM_EMPTY_TEXT_PROVIDER")

        fun <T : RemoteInfo> installOn(table: DataTable<T>, customListener: TableEventListener? = null) {
            val tableErrorHandler = TableErrorHandler(table, customListener)
            Disposer.register(table.tableModel, tableErrorHandler)
        }
    }

    private var disableFilteredMessage = true

    init {
        table.tableModel.addListener(this)

        table.tableModel.getDataModel()?.error?.let {
            onError(it.message, it.cause)
        }
    }

    //region TableEventListener

    // onError could be called from any thread!
    override fun onError(msg: String, e: Throwable?) = runInEdt {

        // Fix for https://youtrack.jetbrains.com/issue/IDEA-257137
        if (table.parent is JViewport) {
            table.emptyText.attachTo(table, table)
        }

        table.emptyText.apply {
            clear()
            appendText(KafkaMessagesBundle.message("table.loading.error", msg), SimpleTextAttributes.ERROR_ATTRIBUTES)

            val message: String? = e?.message ?: e?.toPresentableText()
            if (!message.isNullOrBlank()) {
                val linkText = KafkaMessagesBundle.message("table.loading.error.original")
                appendLine(linkText, SimpleTextAttributes.LINK_ATTRIBUTES) {

                    val pointBelow = table.emptyText.getPointBelow()
                    val point = Point(pointBelow.x + table.emptyText.preferredSize.width / 2, pointBelow.y)
                    val relativePoint = RelativePoint(table, point)

                    val pane = IdeTooltipManager.initPane(
                        message,
                        HintHint()
                            .setTextBg(MessageType.ERROR.popupBackground)
                            .setTextFg(MessageType.ERROR.titleForeground).setAwtTooltip(true), null
                    )

                    JBPopupFactory.getInstance()
                        .createBalloonBuilder(pane)
                        .setBorderColor(MessageType.ERROR.borderColor)
                        .setFillColor(MessageType.ERROR.popupBackground)
                        .setDisposable(table)
                        .createBalloon()
                        .show(relativePoint, Balloon.Position.below)
                }
            } else {
                appendLine(
                    KafkaMessagesBundle.message("table.loading.error.secondary"),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES,
                    null
                )
            }
        }

        customTableEventListener?.onError(msg, e)
    }

    override fun onChanged() {
        // Could be that after error comes normal data and we need to restore emptyState.
        table.emptyText.clear()
        val provider = table.getUserData(CUSTOM_EMPTY_TEXT_PROVIDER)
        if (provider != null) {
            provider.update(table.emptyText)
        } else {
            if (disableFilteredMessage) {
                table.emptyText.appendText(KafkaMessagesBundle.message("table.loaded.empty"))
            } else {
                if (table.tableModel.rowCount == 0) {
                    table.emptyText.appendText(KafkaMessagesBundle.message("table.loaded.empty"))
                } else {
                    table.emptyText.appendText(
                        KafkaMessagesBundle.message(
                            "table.loaded.data",
                            table.tableModel.rowCount
                        )
                    )
                    table.emptyText.appendSecondaryText(
                        KafkaMessagesBundle.message("table.loaded.data.secondary"),
                        SimpleTextAttributes.GRAYED_ATTRIBUTES,
                        null
                    )
                }
            }
        }

        customTableEventListener?.onChanged()
    }
    //endregion TableEventListener

    override fun dispose() {
        table.tableModel.removeListener(this)
    }
}

class CustomEmptyTextProvider(val update: (StatusText) -> Unit)