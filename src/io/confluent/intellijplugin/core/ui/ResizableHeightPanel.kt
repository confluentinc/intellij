package io.confluent.intellijplugin.core.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.SwingUtilities

/**
 * Wraps [content] with a drag grip along its bottom edge so its height can be adjusted independently of any
 * sibling component.
 *
 * Until the user drags the grip, the height auto-fits [content]'s preferred height, clamped to
 * [[minHeight], [maxAutoHeight]]. Once dragged, the user's chosen height is honored (clamped to
 * [[minHeight], [maxManualHeight]]) and, if [persistKey] is set, remembered across sessions.
 */
class ResizableHeightPanel(
    private val content: JComponent,
    private val minHeight: Int,
    private val maxAutoHeight: Int,
    private val maxManualHeight: Int = 5000,
    private val persistKey: String? = null,
) : JPanel(BorderLayout()) {

    companion object {
        private const val GRIP_HEIGHT = 7
    }

    private val properties get() = PropertiesComponent.getInstance()

    /** `null` means "auto-fit to content"; a value means the user picked an explicit editor height. */
    private var userHeight: Int? = persistKey
        ?.let { properties.getInt(it, -1) }
        ?.takeIf { it >= minHeight }

    init {
        add(content, BorderLayout.CENTER)
        add(ResizeGrip(), BorderLayout.SOUTH)
    }

    private fun editorHeight(): Int = userHeight ?: content.preferredSize.height.coerceIn(minHeight, maxAutoHeight)

    override fun getPreferredSize(): Dimension = Dimension(content.preferredSize.width, editorHeight() + GRIP_HEIGHT)

    override fun getMinimumSize(): Dimension = Dimension(0, minHeight + GRIP_HEIGHT)

    private fun setUserHeight(height: Int) {
        userHeight = height.coerceIn(minHeight, maxManualHeight)
        persistKey?.let { properties.setValue(it, userHeight!!, -1) }
        revalidate()
        // Revalidate the enclosing scroll viewport so the new height is reflected immediately.
        (SwingUtilities.getAncestorOfClass(JViewport::class.java, this) ?: parent)?.revalidate()
        repaint()
    }

    private inner class ResizeGrip : JComponent() {
        private var dragStartY = 0
        private var dragStartHeight = 0

        init {
            cursor = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
            preferredSize = Dimension(0, GRIP_HEIGHT)
            val mouse = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    dragStartY = e.locationOnScreen.y
                    dragStartHeight = editorHeight()
                }

                override fun mouseDragged(e: MouseEvent) {
                    setUserHeight(dragStartHeight + (e.locationOnScreen.y - dragStartY))
                }
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            // A small centered grip affordance.
            g.color = JBColor.GRAY
            val midY = height / 2
            val centerX = width / 2
            for (dx in -6..6 step 4) {
                g.fillRect(centerX + dx, midY, 2, 2)
            }
        }
    }
}
