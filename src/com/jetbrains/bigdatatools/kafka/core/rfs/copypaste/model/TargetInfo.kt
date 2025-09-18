package com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ExportFormat
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath

data class TargetInfo(val targetName: String?,
                      val targetFolder: RfsPath,
                      val targetDriver: Driver,
                      val exportFormat: ExportFormat?)