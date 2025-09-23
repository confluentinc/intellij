package io.confluent.kafka.core.rfs.projectview.pane

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import io.confluent.kafka.core.rfs.ui.HoverButton
import io.confluent.kafka.core.settings.connections.ConnectionGroup
import io.confluent.kafka.core.settings.connections.ConnectionSettingProviderEP
import io.confluent.kafka.core.settings.paneadd.StandaloneCreateConnectionUtil
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JPanel

/**
 * Empty state could be displayed in BDT toolwindow.
 * Also it could be displayed in RFSFileChooser (for example, for FileChooser which is used for uploading files).
 */
object RfsPaneEmptyState {
  fun createPanel(project: Project, groups: List<ConnectionGroup> = ConnectionSettingProviderEP.getGroups()): JPanel {
    val panel = JPanel(GridLayout(0, 1))

    // Final groups will contain all "groups" + all its children.
    val availableGroups = ConnectionSettingProviderEP.getGroups()

    val finalGroups = availableGroups.filter { availableGroup ->
      groups.find { it.id == availableGroup.parentGroupId || it.id == availableGroup.id } != null
    }

    val rootAddActions = StandaloneCreateConnectionUtil.createRootAddAction(project, finalGroups)

    for (action in StandaloneCreateConnectionUtil.linearizeActions(rootAddActions)) {
      if (action is Separator) {
        panel.add(TitledSeparator(action.text))
      }
      else {
        panel.add(createHoverButtonByAction(project, action))
      }
    }

    return JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(10)
      add(panel, BorderLayout.PAGE_START)
    }
  }

  private fun createHoverButtonByAction(project: Project, action: AnAction): HoverButton {
    return HoverButton(action.templatePresentation.text, action.templatePresentation.icon).apply {
      addActionListener {
        action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "RfsPaneEmptyState",
                                                                SimpleDataContext.getProjectContext(project)))
      }
    }
  }
}