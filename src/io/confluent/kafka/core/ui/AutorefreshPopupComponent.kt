package io.confluent.kafka.core.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.toolWindow.ToolWindowHeader
import com.intellij.ui.ClickListener
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.JBUI
import io.confluent.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.PopupMenuListener

/**
 * Refresh button with label and popup mark.
 * On button click onActionPerformed called
 * On selection in popup list - onRefreshIntervalChanged called
 */
class AutorefreshPopupComponent(values: List<Pair<@Nls String, Int>>? = null) : JPanel() {

  companion object {
    private val defaultValues = listOf(
      Pair(KafkaMessagesBundle.message ("autorefresh.manual"), -1),
      Pair(KafkaMessagesBundle.message("autorefresh.seconds", 5), 5000),
      Pair(KafkaMessagesBundle.message("autorefresh.seconds", 10), 10000),
      Pair(KafkaMessagesBundle.message("autorefresh.seconds", 30), 30000),
      Pair(KafkaMessagesBundle.message("autorefresh.minutes", 1), 60_000),
      Pair(KafkaMessagesBundle.message("autorefresh.minutes", 5), 5 * 60_000),
    )
  }

  private val values = values ?: defaultValues

  private val refreshLabel = JLabel(KafkaMessagesBundle.message("autorefresh.refresh.label"))
  private val valueLabel = JLabel(KafkaMessagesBundle.message("autorefresh.seconds", 30))

  var onRefreshIntervalChanged: (seconds: Int) -> Unit = {}
  var onActionPerformed: (e: AnActionEvent) -> Unit = {}

  private val BORDER_SIZE = 2
  private val refreshAction: DumbAwareAction
  private val refreshButton: ActionButton

  var value: Int
    get() {
      return values.first { it.first == valueLabel.text }.second
    }
    set(value) {
      val found = values.find { it.second == value }
      valueLabel.text = found?.first ?: defaultValues.last().first
    }

  init {
    refreshAction = DumbAwareAction.create(KafkaMessagesBundle.message("autorefresh.refresh.action"), AllIcons.Actions.Refresh) {
      onActionPerformed(it)
    }

    refreshButton = ActionButton(refreshAction, null, "Autorefresh", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(refreshButton)
    add(refreshLabel)
    add(Box.createHorizontalStrut(3))
    add(valueLabel)
    add(Box.createHorizontalStrut(3))
    add(JLabel(AllIcons.Ide.Statusbar_arrows))

    showPopupMenuOnClick()
    showPopupMenuFromKeyboard()

    border = BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE),
                                                JBUI.Borders.empty(BORDER_SIZE))
  }

  fun registerRefreshShortcut(component: JComponent) {
    refreshAction.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_REFRESH), component)
  }

  private fun showPopupMenuOnClick() {
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        showPopupMenu()
        return true
      }
    }.installOn(this)
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

  private fun showPopupMenu() {
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, createActionGroup())
    popupMenu.setTargetComponent(this)

    // ToDo Hack from com.intellij.toolWindow.ToolWindowHeader@ShowOptionsAction - the only normal working popup in header.
    // New UI ToolWindowHeader hides its content when inactive, and any standard popup makes toolwindow inactive so we have
    // a popup but with no button below.
    // We need to add our popup to special popupMenuListener of ToolWindowHeader to prevent such behaviour.
    try {
      val clazz = ToolWindowHeader::class.java
      val popupMenuListenerField = clazz.getDeclaredField("popupMenuListener")
      popupMenuListenerField.trySetAccessible()
      val header: ToolWindowHeader? = ComponentUtil.getParentOfType(ToolWindowHeader::class.java, this)
      val popupMenuListener = popupMenuListenerField.get(header) as? PopupMenuListener
      popupMenuListener?.let { popupMenu.component.addPopupMenuListener(it) }
    }
    catch (_: Exception) {
    }

    popupMenu.component.show(refreshLabel, 0, this.height)
  }

  /**
   * Called from popup menu with different auto/manual refresh options.
   * @param text new label
   * @param seconds interval of autorefresh in seconds or -1 if turned off
   */
  private fun valueChanged(@Nls text: String, seconds: Int) {
    valueLabel.text = text
    onRefreshIntervalChanged(seconds)
  }

  private fun createActionGroup(): ActionGroup {
    val group = DefaultActionGroup()
    values.forEach { pair ->
      group.add(DumbAwareAction.create(pair.first) {
        valueChanged(pair.first, pair.second)
        refreshAction.update(it)
      })
    }
    return group
  }
}