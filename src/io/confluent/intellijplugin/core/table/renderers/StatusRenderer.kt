package io.confluent.intellijplugin.core.table.renderers

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTable

class StatusRenderer(private val failName: String, private val successName: String, private val warningName: String) :
    MaterialTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        // unify colors?
        if (value != null) when (value.toString()) {
            successName -> foreground = if (UIUtil.isUnderDarcula()) JBColor.GREEN else JBColor.GREEN.darker().darker()
            failName -> foreground =
                JBColor.namedColor("Notification.ToolWindow.errorForeground", UIUtil.getToolTipForeground())

            warningName -> foreground = JBColor.YELLOW
        }

        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    }
}
