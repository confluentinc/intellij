package com.jetbrains.bigdatatools.kafka.core.settings.manager

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData

interface RfsConnectionFactory {
  fun create(groupId: BdtConnectionType,
             project: Project?,
             name: String,
             sshConfig: String?,
             url: String,
             additionalData: Map<String, String>,
             sourceConnection: String): ConnectionData?

  companion object {
    private val EP_NAME = ExtensionPointName.create<RfsConnectionFactory>("com.intellij.bigdatatools.connection.factory")

    fun create(groupId: BdtConnectionType,
               project: Project?,
               name: String,
               sshConfig: String?,
               url: String,
               sourceConnection: String,
               additionalData: Map<String, String>): ConnectionData? {
      val factories = EP_NAME.extensionList
      return factories.firstNotNullOfOrNull { it.create(groupId, project, name, sshConfig, url, additionalData, sourceConnection) }
    }
  }
}