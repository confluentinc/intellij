package io.confluent.intellijplugin.core.settings.manager

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.confluent.intellijplugin.core.settings.*
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

@Service
class RfsConnectionDataManager : Disposable {
  private val isDisposed = AtomicBoolean(false)
  private var listeners: Set<ConnectionSettingsListener> = emptySet()

  override fun dispose() {
    isDisposed.set(true)
    listeners = emptySet()
  }

  @TestOnly
  fun replaceConnections(project: Project?, connections: List<ConnectionData>) {
    val storage = getConnectionStorage(project)
    val connectionsForRemove = storage.getConnections()
    connectionsForRemove.forEach {
      it.clearCredentials()
      innerRemoveConnection(project, it)
    }

    storage.setConnections(connections)
  }

  fun getConnections(project: Project?): List<ConnectionData> {
    val appConnData = getAppConnections()
    val projectConnData = getProjectConnections(project)
    return appConnData + projectConnData
  }

  fun getConnectionsForScope(project: Project?) = if (project != null)
    getProjectConnections(project)
  else
    getAppConnections()

  fun findConnectionInAllProjects(connId: String): ConnectionData? {
    getAppConnections().find { it.innerId == connId }?.let {
      return it
    }
    ProjectManager.getInstance().openProjects.forEach { project ->
      getProjectConnections(project).find { it.innerId == connId }?.let {
        return it
      }
    }

    return null
  }

  inline fun <reified T> getTyped(project: Project): List<T> = getConnections(project).filterIsInstance<T>()

  inline fun <reified T> getTyped(connId: String, project: Project?) = getConnectionById(project, connId) as? T

  fun getConnectionsByGroupId(groupId: String, project: Project?) = getConnections(project).filter { it.groupId == groupId }

  fun getConnectionById(project: Project?, connId: String) = getConnections(project).find { it.innerId == connId }

  fun addConnection(project: Project?, conn: ConnectionData) = invokeAndWaitIfNeeded {
    val realProject = connProject(conn, project)
    innerAddConnection(realProject, conn)
  }

  fun removeConnection(project: Project?, conn: ConnectionData) {
    executeOnPooledThread {
      conn.clearCredentials()
    }
    removeConnectionKeepingCredentials(project, conn)
  }

  fun removeConnectionKeepingCredentials(project: Project?, conn: ConnectionData) {
    val connectionProject = connProject(conn, project)
    innerRemoveConnection(connectionProject, conn)
  }

  fun modifyConnection(project: Project?, conn: ConnectionData, changedFields: List<ModificationKey>) {
    if (changedFields.isEmpty())
      return

    if (!getConnections(project).any { it.innerId == conn.innerId })
      return

    if (CommonSettingsKeys.IS_GLOBAL_KEY in changedFields) {
      val prevProject = if (conn.isPerProject) null else project
      val curProject = connProject(conn, project)
      innerRemoveConnection(prevProject, conn)
      innerAddConnection(curProject, conn)
    }
    else {
      val connectionProject = connProject(conn, project)
      notifyListeners {
        it.onConnectionModified(connectionProject, conn, changedFields)
      }
    }
  }

  private fun getAppConnections() = getConnectionStorage(null).getConnections()

  private fun innerAddConnection(realProject: Project?, conn: ConnectionData) {
    val storage = getConnectionStorage(realProject)
    val isSuccessfullyAdded = storage.addConnection(conn)
    if (!isSuccessfullyAdded)
      return

    notifyListeners {
      it.onConnectionAdded(realProject, conn)
    }
  }

  private fun innerRemoveConnection(connectionProject: Project?, conn: ConnectionData) {
    val storage = getConnectionStorage(connectionProject)
    storage.removeConnection(conn.innerId)
    notifyListeners {
      it.onConnectionRemoved(connectionProject, conn)
    }
  }

  private fun getProjectConnections(project: Project?): List<ConnectionData> {
    if (isDisposed.get())
      return emptyList()
    return project?.let {
      getConnectionStorage(project).getConnections()
    } ?: emptyList()
  }

  private fun getConnectionStorage(project: Project?) = if (project == null)
    GlobalConnectionSettings.getInstance()
  else
    LocalConnectionSettings.getInstance(project)

  private fun notifyListeners(body: (ConnectionSettingsListener) -> Unit) = listeners.forEach {
    try {
      body(it)
    }
    catch (t: Throwable) {
      logger.error(t)
    }
  }

  private fun connProject(conn: ConnectionData, project: Project?) = if (conn.isPerProject) {
    assert(project != null) {
      "Project must be not null for per project connectionData!"
    }
    project
  }
  else {
    null
  }

  fun addListener(listener: ConnectionSettingsListener) {
    listeners = listeners + listener
  }

  fun removeListener(listener: ConnectionSettingsListener) {
    listeners = listeners - listener
  }

  fun removeListener(listenerId: String) {
    listeners = listeners.filterNot { it.getId() == listenerId }.toSet()
  }

  companion object {
    private val logger = Logger.getInstance(this::class.java)

    //Can be null on dispose and plugin unload
    val instance: RfsConnectionDataManager?
      get() = serviceOrNull()

    fun List<ConnectionData>.filterOnlyEnabled(project: Project?): List<ConnectionData> {
      val filter = this
        .filter { it.isEnabled }
        .filter { conn ->
          conn.sourceConnection == null || instance?.getConnections(
            project)?.firstOrNull { conn.sourceConnection == it.innerId }?.isEnabled == true
        }
      return filter
    }
  }
}