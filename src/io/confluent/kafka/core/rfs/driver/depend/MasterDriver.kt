package io.confluent.kafka.core.rfs.driver.depend

import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.RfsPath


interface MasterDriver : Driver {
  fun prepareRefreshDependedDriver(driver: Driver)

  fun listDependConnections(rfsPath: RfsPath): List<String>
  fun getDependConnectionRfsPath(connectionId: String): RfsPath?
}