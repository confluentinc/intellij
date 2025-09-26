package io.confluent.intellijplugin.core.rfs.driver.depend

import io.confluent.intellijplugin.core.constants.BdtConnectionType

interface BdtSlaveConnection {
  val connectionId: String
  val connectionType: BdtConnectionType
  val clusterId: String
}