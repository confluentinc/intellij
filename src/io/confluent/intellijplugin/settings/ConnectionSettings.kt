package io.confluent.intellijplugin.core.settings

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.constants.BdtPlugins
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory

object ConnectionSettings {

  /**
   * Shows Connection settings dialog with newly created data.
   * If data is null, new data will be created as group.createBlankData().
   * If applyIfOk is true, newly created data will be immediately saved.
   */
  fun create(project: Project, group: ConnectionFactory<*>, data: ConnectionData? = null, applyIfOk: Boolean = false): ConnectionData? {
    val configurable = if (BdtPlugins.isSupportedConnectionGroup(group.id))
      ConnectionsConfigurable(project)
    else
      return null
    val dialog = SettingsDialogFactory.getInstance().create(project,
                                                            ShowSettingsUtilImpl.createDimensionKey(configurable),
                                                            configurable, false, false)
    val connData = configurable.myUi.createNewConnectionFor(group, data)
    if (applyIfOk) {
      if (dialog.showAndGet()) {
        configurable.apply()
        return connData
      }
    }
    else {
      dialog.show()
    }
    return null
  }

  /**
   * Opens Connection settings dialog and focus on provided connectionId if it is not-null.
   */
  fun open(project: Project, connectionId: String?) {
    if (connectionId == null) {
      open(project)
    }
    else {
      open(project) { it.myUi.setFirstSelectedNodeConnId(connectionId) }
    }
  }

  fun open(project: Project, action: ((ConnectionsConfigurable) -> Unit)? = null) {
    val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true).filter { it.configurables.isNotEmpty() }
    val configurable = ConfigurableVisitor.findById("kafka_conn_settings", groups)
    val dialog = SettingsDialogFactory.getInstance().create(project, groups, configurable, null)
    dialog.isModal = false

    action?.let {
      val connectionsConfigurable = (configurable as? ConfigurableWrapper)?.configurable as? ConnectionsConfigurable ?: return@let
      it.invoke(connectionsConfigurable)
    }

    dialog.show()
  }
}