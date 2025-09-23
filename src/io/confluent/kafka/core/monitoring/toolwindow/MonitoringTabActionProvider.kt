package io.confluent.kafka.core.monitoring.toolwindow

import io.confluent.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.ide.ui.UISettings.Companion.shadowInstance
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.AsyncDataContext
import com.intellij.openapi.ui.popup.ActiveIcon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabAction
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import com.intellij.ui.ComponentUtil
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import io.confluent.kafka.core.util.ConnectionUtil
import io.confluent.kafka.util.KafkaMessagesBundle

class MonitoringTabActionProvider : ContentTabActionProvider {
  override fun createTabActions(content: Content): List<ContentTabAction> {
    content.getUserData(MonitoringToolWindowController.CONNECTION_ID) ?: return emptyList()
    content.getUserData(MonitoringToolWindowController.PROJECT) ?: return emptyList()
    return listOf(ManageConnectionContentTabAction(content))
  }

  // Nearly copy of om.intellij.openapi.wm.impl.content.ContentTabLabel@CloseContentTabAction because we need special behaviour.
  private class ManageConnectionContentTabAction(private val content: Content) : ContentTabAction(
    ActiveIcon(BigdatatoolsKafkaIcons.MoreHovered,
               BigdatatoolsKafkaIcons.More)) {
    override val available = true

    override fun runAction() {
      val connectionId = content.getUserData(MonitoringToolWindowController.CONNECTION_ID) ?: return
      val project = content.getUserData(MonitoringToolWindowController.PROJECT) ?: return
      val contentManager = content.manager ?: return

      val internalDecorator = ComponentUtil.getParentOfType(InternalDecorator::class.java, contentManager.component)
      val tabs = UIUtil.findComponentsOfType(internalDecorator, ContentTabLabel::class.java)
      val tab = tabs.find { it.content == content } ?: return

      val actions = ActionManager.getInstance().getAction("Kafka.MonitoringTabsActions") as ActionGroup

      val dataContext = AsyncDataContext { dataId ->
        when (dataId) {
          ConnectionUtil.CONNECTION_ID.name -> connectionId
          CommonDataKeys.PROJECT.name -> project
          else -> null
        }
      }

      val popupMenu = JBPopupFactory.getInstance().createActionGroupPopup(null, actions, dataContext,
                                                                          ActionSelectionAid.SPEEDSEARCH, false)
      popupMenu.showUnderneathOf(tab)
    }

    override val afterText: Boolean
      get() = shadowInstance.closeTabButtonOnTheRight || !shadowInstance.showCloseButton

    override val tooltip = KafkaMessagesBundle.message("connections.menu.tab.action")
  }
}