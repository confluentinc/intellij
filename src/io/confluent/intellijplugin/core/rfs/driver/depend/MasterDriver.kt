package io.confluent.intellijplugin.core.rfs.driver.depend

import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath


interface MasterDriver : Driver {
    fun prepareRefreshDependedDriver(driver: Driver)

    fun listDependConnections(rfsPath: RfsPath): List<String>
    fun getDependConnectionRfsPath(connectionId: String): RfsPath?
}