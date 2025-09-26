package io.confluent.intellijplugin.core.rfs.copypaste.model

import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.ExportFormat
import io.confluent.intellijplugin.core.rfs.driver.RfsPath

data class TargetInfo(val targetName: String?,
                      val targetFolder: RfsPath,
                      val targetDriver: Driver,
                      val exportFormat: ExportFormat?)