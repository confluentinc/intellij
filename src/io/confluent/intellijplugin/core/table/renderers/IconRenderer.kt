package io.confluent.intellijplugin.core.table.renderers

import com.intellij.openapi.actionSystem.ActionToolbar
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.SwingConstants

/** If table cell value is Icon, this icon will be rendered graphically.
 * Used directly as  base renderer for Icon type in material table. */
internal class IconRenderer : MaterialTableCellRenderer() {

    private var icon: Icon? = null

    init {
        horizontalAlignment = SwingConstants.CENTER
    }

    override fun setValue(value: Any?) {
        icon = if (value is Icon) value else null
    }

    override fun paintComponent(g: Graphics) {
        icon?.let {
            it.paintIcon(this, g, width / 2 - it.iconWidth / 2, height / 2 - it.iconHeight / 2)
        }
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(
            icon?.iconWidth ?: ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width,
            icon?.iconHeight ?: ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height
        )
    }
}