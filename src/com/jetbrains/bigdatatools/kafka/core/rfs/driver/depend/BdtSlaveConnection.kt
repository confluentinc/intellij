package io.confluent.kafka.core.rfs.driver.depend

import io.confluent.kafka.core.constants.BdtConnectionType

interface BdtSlaveConnection {
  val connectionId: String
  val connectionType: BdtConnectionType
  val clusterId: String
}