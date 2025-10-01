package io.confluent.intellijplugin.core.rfs.driver.manager

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.connections.ConnectionData

@Service
class ApplicationDriverManager : DriverManager(null) {
  override fun isSupportedStorageLocation(newConnectionData: ConnectionData, project: Project?) = project ==  null
}