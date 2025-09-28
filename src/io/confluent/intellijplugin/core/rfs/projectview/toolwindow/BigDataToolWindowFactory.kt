package io.confluent.intellijplugin.core.rfs.projectview.toolwindow

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import io.confluent.intellijplugin.core.constants.BdtPlugins
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class BigDataToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    BigDataToolWindowController.getInstance(project)?.setUp(toolWindow)
  }

  override fun shouldBeAvailable(project: Project) =
    BdtPlugins.isKafkaPluginInstalled()

  override fun init(toolWindow: ToolWindow) {
    toolWindow.stripeTitle = KafkaMessagesBundle.message("tool.window.title")
    registerToolWindowShortcut(toolWindow.id)
  }

  private fun registerToolWindowShortcut(toolWindowId: String) {
    val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(toolWindowId)
    val keymap = KeymapManager.getInstance().activeKeymap
    val shortcuts = keymap.getShortcuts(actionId)
    if (shortcuts.isNotEmpty()) {
      return
    }

    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_B,
                                           (if (ClientSystemInfo.isMac()) InputEvent.META_DOWN_MASK
                                           else InputEvent.ALT_DOWN_MASK) or InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)
    val activateToolWindowShortcut = KeyboardShortcut(keyStroke, null)
    keymap.addShortcut(actionId, activateToolWindowShortcut)
  }

  companion object {
    const val TOOL_WINDOW_ID = "BigDataToolWindow"
  }
}