package io.confluent.intellijplugin.core.table.extension

import com.intellij.icons.AllIcons
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.fields.ExpandableSupport
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.core.table.MaterialTable
import io.confluent.intellijplugin.core.ui.TextSizeUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import kotlin.math.max

/**
 * If the table cell content is bigger than the cell rect, the popup with this text will be shown on double click.
 */
object TableCellPreview {

  fun installOn(table: MaterialTable, columnName: String) {
    installOn(table, listOf(columnName))
  }

  fun installOn(table: MaterialTable, columnNames: List<String>) {
    table.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {

        if (e.clickCount != 2 || e.button != MouseEvent.BUTTON1) {
          return
        }

        val col = table.columnAtPoint(e.point)
        val row = table.rowAtPoint(e.point)
        if (row < 0 || col < 0) {
          return
        }

        if (!columnNames.contains(table.columnModel.getColumn(col).headerValue)) {
          return
        }

        showPopupIfNecessary(table, row, col)
      }
    })
  }

  fun showPopupIfNecessary(table: MaterialTable, row: Int, col: Int) {
    val tableRect = table.visibleRect
    val cellRect = table.getCellRect(row, col, false).intersection(tableRect)

    val value = table.getValueAt(row, col)?.toString() ?: ""

    val cellRenderer = table.getCellRenderer(row, col)
    table.prepareRenderer(cellRenderer, row, col)
    val component = cellRenderer.getTableCellRendererComponent(table, value, false, false, row, col)

    if (component.preferredSize.width < cellRect.width) {
      return
    }

    val point = cellRect.location
    SwingUtilities.convertPointToScreen(point, table)

    val metrics = if (table.font == null) null else table.getFontMetrics(table.font)
    val height = metrics?.height ?: 16
    val minWidth = height * 16
    val preferredWidth = max(minWidth, cellRect.width)

    showPopup(value, table.font, point, preferredWidth)
  }

  private fun createCollapseExtension(runnable: Runnable): ExtendableTextComponent.Extension {
    return ExtendableTextComponent.Extension.create(AllIcons.General.CollapseComponent,
                                                    AllIcons.General.CollapseComponentHover,
                                                    KeymapUtil.createTooltipText(KafkaMessagesBundle.message ("rfs.editor.collapse"),
                                                                                 "CollapseExpandableComponent"),
                                                    runnable)
  }

  private fun showPopup(value: String, font: Font, point: Point, preferredWidth: Int) {

    @Suppress("HardCodedStringLiteral") // Actually we have no need in localized strings here.
    val area = JTextArea().apply {
      isEditable = false
      this.font = font
      lineWrap = true
      border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
      text = value
    }

    var popup: JBPopup? = null

    val label = ExpandableSupport.createLabel(createCollapseExtension { popup?.cancel() }).apply {
      border = JBUI.Borders.empty(5, 0, 5, 5)
    }

    val pane = JBScrollPane(area).apply {
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
      verticalScrollBar.add(JBScrollBar.LEADING, label)
      verticalScrollBar.background = area.background
      viewportBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5)
      preferredSize = Dimension(preferredWidth, TextSizeUtils.getPreferredHeight(area.text, area.font, preferredWidth - 10) + 10)
    }

    popup = JBPopupFactory.getInstance().createComponentPopupBuilder(pane, pane).setResizable(true)
      .setMovable(true)
      .setFocusable(true)
      .setRequestFocus(true)
      .setCancelOnClickOutside(true)
      .setModalContext(false)
      .setLocateWithinScreenBounds(false)
      .setCancelCallback {
        try {
          popup = null
          return@setCancelCallback true
        }
        catch (ignore: Exception) {
          return@setCancelCallback false
        }
      }
      .createPopup()

    popup?.show(RelativePoint(point))
  }
}