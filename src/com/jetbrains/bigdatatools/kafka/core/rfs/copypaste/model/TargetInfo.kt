package io.confluent.kafka.core.rfs.copypaste.model

import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.ExportFormat
import io.confluent.kafka.core.rfs.driver.RfsPath

data class TargetInfo(val targetName: String?,
                      val targetFolder: RfsPath,
                      val targetDriver: Driver,
                      val exportFormat: ExportFormat?)