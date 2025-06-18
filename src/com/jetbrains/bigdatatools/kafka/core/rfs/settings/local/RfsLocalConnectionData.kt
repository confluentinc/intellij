package com.jetbrains.bigdatatools.kafka.core.rfs.settings.local

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.LocalDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.kafka.core.rfs.settings.local.RfsLocalConnectionGroup.Companion.LOCAL_CONNECTION_NAME
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionGroup
import javax.swing.Icon

class RfsLocalConnectionData(var rootPath: String? = null) : RemoteFsDriverProvider(LOCAL_CONNECTION_NAME) {

  override fun rfsDriverType() = BdtConnectionType.LOCAL

  override fun getIcon(): Icon = LocalDriver.driverIcon()

  override fun createConfigurable(project: Project, parentGroup: ConnectionGroup) =
    object : ConnectionConfigurable<RfsLocalConnectionData, RfsLocalSettingsCustomizer>(this, project, parentGroup.icon) {
      override fun createSettingsCustomizer() = RfsLocalSettingsCustomizer(connectionData, disposable)
    }

  override fun createDriverImpl(project: Project?, isTest: Boolean): Driver = LocalDriver(this, project)
}