package com.jetbrains.bigdatatools.kafka.core.ui.filter

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.ClickListener
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.LineBorder
import kotlin.math.max

/**
 * Base component for all filters with popup. (For example date range select or count select).
 * Taken from com.intellij.vcs.log.ui.filter.FilterPopupComponent
 */
abstract class FilterPopupComponent(@Nls label: String) : JPanel() {
  private var nameLabel: JLabel? = null
  private var valueLabel: JLabel

  abstract var currentText: String

  protected open val defaultSelectorForeground: Color
    get() = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else NamedColorUtil.getInactiveTextColor().darker().darker()

  protected open val shouldIndicateHovering = true

  protected open val shouldDrawLabel = true

  init {
    nameLabel = if (shouldDrawLabel) JLabel("$label: ") else null
    valueLabel = object : JLabel() {
      override fun getText() = currentText
    }

    setDefaultForeground()
    isFocusable = true
    border = createUnfocusedBorder()

    layout = BoxLayout(this, BoxLayout.X_AXIS)
    if (nameLabel != null) add(nameLabel)
    add(valueLabel)
    add(Box.createHorizontalStrut(GAP_BEFORE_ARROW))
    add(JLabel(AllIcons.Ide.Statusbar_arrows))

    showPopupMenuOnClick()
    showPopupMenuFromKeyboard()
    if (shouldIndicateHovering) {
      indicateHovering()
    }
    indicateFocusing()
  }

  /** Should be called by descendants on selected value change */
  protected open fun valueChanged() {
    valueLabel.revalidate()
    valueLabel.repaint()
  }

  /** Create popup actions available under this filter. */
  protected abstract fun createActionGroup(): ActionGroup

  private fun indicateFocusing() {
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        border = createFocusedBorder()
      }

      override fun focusLost(e: FocusEvent) {
        border = createUnfocusedBorder()
      }
    })
  }

  private fun showPopupMenuFromKeyboard() {
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_DOWN) {
          showPopupMenu()
        }
      }
    })
  }

  private fun showPopupMenuOnClick() {
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        showPopupMenu()
        return true
      }
    }.installOn(this)
  }

  private fun indicateHovering() {
    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        setOnHoverForeground()
      }

      override fun mouseExited(e: MouseEvent) {
        setDefaultForeground()
      }
    })
  }

  private fun setDefaultForeground() {
    if (nameLabel != null) {
      nameLabel!!.foreground = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else NamedColorUtil.getInactiveTextColor()
    }
    valueLabel.foreground = defaultSelectorForeground
  }

  private fun setOnHoverForeground() {
    nameLabel?.foreground = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else UIUtil.getTextAreaForeground()
    valueLabel.foreground = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else UIUtil.getTextFieldForeground()
  }

  private fun showPopupMenu() {
    createPopupMenu().showUnderneathOf(this)
  }

  private fun createPopupMenu(): ListPopup {
    return JBPopupFactory.getInstance().createActionGroupPopup(null, createActionGroup(), DataManager.getInstance().getDataContext(this),
                                                               JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
  }

  private class FilledRoundedBorder(color: Color, private val myArcSize: Int, thickness: Int)
    : LineBorder(color, thickness) {

    override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
      val config = GraphicsUtil.setupAAPainting(g)

      g!!.color = lineColor
      val area = Area(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), myArcSize.toDouble(),
                                              myArcSize.toDouble()))
      val innerArc = max(myArcSize - thickness, 0)
      area.subtract(Area(RoundRectangle2D.Double((x + thickness).toDouble(), (y + thickness).toDouble(),
                                                 (width - 2 * thickness).toDouble(), (height - 2 * thickness).toDouble(),
                                                 innerArc.toDouble(), innerArc.toDouble())))
      (g as Graphics2D).fill(area)

      config.restore()
    }
  }

  companion object {
    private const val GAP_BEFORE_ARROW = 3
    private const val BORDER_SIZE = 2

    private fun createFocusedBorder(): Border {
      return BorderFactory.createCompoundBorder(
        FilledRoundedBorder(UIUtil.getFocusedBorderColor(), 10, BORDER_SIZE),
        JBUI.Borders.empty(2))
    }

    private fun createUnfocusedBorder(): Border {
      return BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(BORDER_SIZE,
                                                                                BORDER_SIZE,
                                                                                BORDER_SIZE,
                                                                                BORDER_SIZE), JBUI.Borders.empty(2))
    }
  }
}
