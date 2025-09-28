package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.connections.ConnectionData

@Service(Service.Level.PROJECT)
@State(
  name = "BigDataIdeConnectionSettings",
  useLoadedStateAsExisting = false, // This is hack, needed because we need to transfer sensitive data from settings to PasswordSafe
  storages = [
    Storage("bigdataide_settings.xml")
  ]
)
class LocalConnectionSettings : ConnectionSettingsBase() {
  companion object {
    fun getInstance(project: Project): LocalConnectionSettings = project.service()
  }

  override fun unpackData(conn: ConnectionData): ConnectionData {
    return super.unpackData(conn).apply { isPerProject = true }
  }
}