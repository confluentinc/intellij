package io.confluent.kafka.core.rfs.driver.manager

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.settings.connections.ConnectionData

@Service(Service.Level.PROJECT)
class ProjectDriverManager(project: Project) : DriverManager(project) {
  override fun isSupportedStorageLocation(newConnectionData: ConnectionData, project: Project?) = project == this.project
}