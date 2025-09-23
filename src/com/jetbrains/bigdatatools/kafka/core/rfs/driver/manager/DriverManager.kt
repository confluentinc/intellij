package io.confluent.kafka.core.rfs.driver.manager

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.core.rfs.driver.ActivitySource
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.refreshConnectionLaunch
import io.confluent.kafka.core.rfs.settings.RemoteFsDriverProvider
import io.confluent.kafka.core.settings.CommonSettingsKeys
import io.confluent.kafka.core.settings.ConnectionSettingsListener
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager.Companion.filterOnlyEnabled
import io.confluent.kafka.core.util.BdtAsyncPromise
import io.confluent.kafka.core.util.executeNotOnEdt
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

abstract class DriverManager(val project: Project?) : Disposable {
  private val isInited = AtomicBoolean(false)
  val initPromise = BdtAsyncPromise<Unit>()

  private val connDataManager = RfsConnectionDataManager.instance ?: error("Cannot be inited")

  private val innerDrivers = CopyOnWriteArrayList<Driver>()

  init {
    initDrivers()
  }

  val drivers: Iterable<Driver>
    get() = innerDrivers.asIterable()

  protected val connectionListener = object : ConnectionSettingsListener {
    override fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {
      if (!isSupportedStorageLocation(newConnectionData, project) || newConnectionData !is RemoteFsDriverProvider)
        return

      if (newConnectionData.isEnabled)
        createDriver(newConnectionData)
    }

    override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
      if (!isSupportedStorageLocation(removedConnectionData, project) || removedConnectionData !is RemoteFsDriverProvider)
        return

      removeDriver(removedConnectionData)
    }

    override fun onConnectionModified(project: Project?, connectionData: ConnectionData, modified: Collection<ModificationKey>) {
      if (!isSupportedStorageLocation(connectionData, project) || connectionData !is RemoteFsDriverProvider)
        return

      if (connectionData.notReloadRequiredKeys().containsAll(modified)) {
        return
      }

      if (CommonSettingsKeys.ENABLED_KEY in modified && !connectionData.isEnabled || connectionData.isEnabled)
        removeDriver(connectionData)

      if (connectionData.isEnabled)
        createDriver(connectionData)

      //Update master connection
      val masterDriver = connectionData.sourceConnection?.let { getDriverById(project, it) }
      masterDriver?.refreshConnectionLaunch(ActivitySource.DEPEND_UPDATED)
    }
  }

  init {
    connDataManager.addListener(connectionListener)
  }

  @TestOnly
  fun addTestDriver(driver: Driver) {
    innerDrivers.add(driver)
  }

  @TestOnly
  fun clearAllDrivers() {
    innerDrivers.forEach {
      Disposer.dispose(it)
    }
    innerDrivers.clear()
  }

  override fun dispose() {
    connDataManager.removeListener(connectionListener)
  }

  protected abstract fun isSupportedStorageLocation(newConnectionData: ConnectionData, project: Project?): Boolean

  private fun createDriver(connectionData: RemoteFsDriverProvider) {
    try {
      val existsDriver = drivers.firstOrNull { it.getExternalId() == connectionData.innerId }
      if (existsDriver != null) {
        logger.error("Driver already exists for $connectionData")
        return
      }

      val newDriver = connectionData.createDriver(project, false)
      newDriver.initDriverUpdater()
      Disposer.register(this, newDriver)
      innerDrivers.add(newDriver)
    }
    catch (t: Throwable) {
      logger.error("Cannot create driver for ${connectionData.name}", t)
    }
  }

  private fun removeDriver(connectionData: ConnectionData) {
    val driver = drivers.find { it.getExternalId() == connectionData.innerId } ?: return

    innerDrivers.remove(driver)
    Disposer.dispose(driver)
  }

  private fun initDrivers() = executeNotOnEdt {
    val originalConnections = connDataManager.getConnectionsForScope(project).filterOnlyEnabled(project)
    val connections = originalConnections
      .filterIsInstance<RemoteFsDriverProvider>()
      .sortedBy { it.sourceConnection != null } // load depend drivers
    connections.forEach {
      createDriver(it)
    }

    isInited.set(true)
    initPromise.setResult(Unit)
  }

  companion object {
    private val logger = Logger.getInstance(this::class.java)

    @Volatile
    private var initialized = false

    /**
     * Invoke creating requirement driver manager and add driver listener in an Idea Service model
     */
    fun init(project: Project) {
      if (!initialized) {
        synchronized(DriverManager::class.java) {
          if (!initialized) {
            initialized = true
            getApplicationDriverManager()
            getProjectDriverManager(project)
          }
        }
      }
    }

    // should not be called from EDT without subscribing to updates, because if not inited just returns empty list and triggers update
    fun getDrivers(project: Project?): List<Driver> {
      val globalDrivers = getApplicationDriverManager().drivers
      val projectDrivers = project?.let {
        getJustProjectDrivers(it)
      } ?: emptyList()

      return globalDrivers + projectDrivers
    }

    fun getDriversForAllOpenProjects(): List<Driver> {
      val appDrivers = getApplicationDriverManager().drivers
      val allProjectDrivers = ProjectManager.getInstance().openProjects.flatMap {
        getJustProjectDrivers(it)
      }
      return appDrivers + allProjectDrivers
    }

    private fun getJustProjectDrivers(project: Project) = getProjectDriverManager(project).drivers.toList()

    fun getDriverById(project: Project?, id: String) = getDrivers(project).find { it.getExternalId() == id }

    fun getDisposable(project: Project?): Disposable = if (project != null)
      getProjectDriverManager(project)
    else
      getApplicationDriverManager()

    private fun getApplicationDriverManager(): ApplicationDriverManager = service()

    private fun getProjectDriverManager(project: Project) = project.getService(ProjectDriverManager::class.java)

    @TestOnly
    fun clearAllDrivers(project: Project?) {
      getApplicationDriverManager().clearAllDrivers()
      project?.let { getProjectDriverManager(it).clearAllDrivers() }
    }

    fun onDriversInit(project: Project, function: () -> Unit) {
      val applicationDriverManager = getApplicationDriverManager()
      val projectDriverManager = getProjectDriverManager(project)
      applicationDriverManager.initPromise.onSuccess {
        projectDriverManager.initPromise.onSuccess {
          function()
        }
      }
    }
  }
}