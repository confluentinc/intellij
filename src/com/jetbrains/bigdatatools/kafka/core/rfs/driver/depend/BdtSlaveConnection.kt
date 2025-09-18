package com.jetbrains.bigdatatools.kafka.core.rfs.driver.depend

import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType

interface BdtSlaveConnection {
  val connectionId: String
  val connectionType: BdtConnectionType
  val clusterId: String
}