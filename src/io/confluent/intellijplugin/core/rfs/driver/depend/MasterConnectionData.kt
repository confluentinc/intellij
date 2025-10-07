package io.confluent.intellijplugin.core.rfs.driver.depend

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager

interface MasterConnectionData<T : BdtSlaveConnection> {
    val innerId: String
    fun sshConfigsByClusterId(): Map<String, String>

    fun getDependConnections(project: Project?) =
        getSlaveConnections().mapNotNull {
            RfsConnectionDataManager.instance?.getConnectionById(
                project,
                it.connectionId
            )
        }


    fun getSlaveConnections(): List<T>
    fun getSlaveDriverConfig(connectionId: String): T?
    fun setSlaveDriverConfig(conn: T)
    fun removeSlave(innerIds: Set<String>): Boolean
}