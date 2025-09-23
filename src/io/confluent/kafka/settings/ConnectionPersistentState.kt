package io.confluent.kafka.core.settings

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.settings.connections.ConnectionGroup
import io.confluent.kafka.core.settings.connections.SimpleConnectionConfigurable
import java.io.Serializable

/**
 * User: Dmitry.Naydanov
 * Date: 2019-04-28.
 */
class ConnectionPersistentState(var connections: List<ExtendedConnectionData> = listOf()) : Serializable

data class ExtendedConnectionData(
  @Deprecated("Not used now")
  var pluginId: String = "",
  var fqn: String = "",
  var extended: MutableMap<String, String> = mutableMapOf()
) : ConnectionData() {
  override fun copyFrom(c2: ConnectionData): ExtendedConnectionData {
    super.copyFrom(c2)
    return this
  }

  override fun createDriver(project: Project?, isTest: Boolean) = throw Exception("Cannot create driver")

  override fun createConfigurable(project: Project, parentGroup: ConnectionGroup) =
    SimpleConnectionConfigurable(this, project)
}