package com.jetbrains.bigdatatools.kafka.core.settings

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.constants.BdtPlugins.isSupportedByPlugin
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionSettingProviderEP
import com.jetbrains.bigdatatools.kafka.core.settings.statistics.ConnectionUsagesCollector
import com.jetbrains.bigdatatools.kafka.core.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.kafka.core.util.InternalFeature
import org.jetbrains.annotations.VisibleForTesting
import java.io.*
import java.lang.Long
import java.lang.reflect.Modifier
import java.util.*
import kotlin.Any
import kotlin.Boolean
import kotlin.Exception
import kotlin.Pair
import kotlin.String
import kotlin.Suppress
import kotlin.Throwable
import kotlin.Unit
import kotlin.let
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField
import kotlin.synchronized

abstract class ConnectionSettingsBase : PersistentStateComponent<ConnectionPersistentState>, Disposable {
  companion object {
    private val logger = Logger.getInstance(this::class.java)

    private const val OLD_FQN_KEY = "old_fqn"

    private const val UNHANDLED_MARKER = "1unhandled"

    private val RENAME_MAP = mapOf(
      Pair("com.jetbrains.bigdatatools.rfs.settings.local.RfsLocalConnectionData",
           "com.jetbrains.bigdatatools.kafka.core.rfs.settings.local.RfsLocalConnectionData"),
      Pair("com.jetbrains.bigdatatools.databricks.rfs.DatabricksConnectionData",
           "com.intellij.bigdatatools.databricks.rfs.DatabricksConnectionData"),
      Pair("org.com.jetbrains.bigdatatools.filestorage.common.BucketFilterType",
           "com.jetbrains.bigdatatools.kafka.core.filestorages.BucketFilterType"),
      Pair("org.jetbrains.hdfsplugin.hdfs.settings.BucketFilterType",
           "com.jetbrains.bigdatatools.kafka.core.filestorages.BucketFilterType"),
      Pair("com.jetbrains.bigdatatools.settings.ExtendedConnectionData",
           "com.jetbrains.bigdatatools.kafka.core.settings.ExtendedConnectionData"),
      Pair("com.jetbrains.bigdatatools.connection.ProxyEnableType",
           "com.jetbrains.bigdatatools.kafka.core.connection.ProxyEnableType"),
      Pair("com.jetbrains.bigdatatools.connection.ProxyType",
           "com.jetbrains.bigdatatools.kafka.core.connection.ProxyType"),
      Pair("com.jetbrains.bigdatatools.connection.tunnel.model.ConnectionSshTunnelInfo",
           "com.jetbrains.bigdatatools.kafka.core.connection.tunnel.model.ConnectionSshTunnelDataLegacy"),
      Pair("com.jetbrains.bigdatatools.connection.tunnel.model.ConnectionSshTunnelDataLegacy",
           "com.jetbrains.bigdatatools.kafka.core.connection.tunnel.model.ConnectionSshTunnelDataLegacy"),
    )

    fun findPluginByClassName(conn: ExtendedConnectionData): PluginId? {
      val plugin = PluginManager.getPluginByClassNameAsNoAccessToClass(conn.fqn)
      if (plugin != null)
        return plugin

      val pluginId = BdtConnectionType.getForId(conn.groupId)?.pluginType?.pluginId
      return PluginId.findId(pluginId)
    }

    @VisibleForTesting
    fun shouldSerialize(prop: KProperty1<out ConnectionData, *>): Boolean {
      return prop.findAnnotation<DoNotSerialize>() == null && ((prop.javaField == null) || !Modifier.isTransient(
        prop.javaField!!.modifiers))
    }

    private fun encode64(obj: Any): String {
      val stream = ByteArrayOutputStream()
      ObjectOutputStream(stream).writeObject(obj)

      return Base64.getEncoder().encodeToString(stream.toByteArray())
    }

    private fun decode64(s: String, conn: ExtendedConnectionData): Any =
      PluginObjectInputStream(ByteArrayInputStream(Base64.getDecoder().decode(s)), conn).readObject()

    fun packData(conn: ConnectionData): ExtendedConnectionData {
      if (conn is ExtendedConnectionData) return conn // safe since we cannot inherit from ECD
      val clazz = conn.javaClass
      if (clazz.isAssignableFrom(ConnectionData::class.java)) return ExtendedConnectionData().copyFrom(conn)

      val ext = ExtendedConnectionData(
        fqn = clazz.canonicalName
      ).copyFrom(conn)

      var currentClazz: Class<in ConnectionData>? = clazz

      while (currentClazz != null && currentClazz.name != "java.lang.Object" && currentClazz.name != ConnectionData::class.java.name) {
        @Suppress("UNCHECKED_CAST")
        for (prop in (currentClazz as? Class<ConnectionData>)?.kotlin?.declaredMemberProperties ?: emptyList()) {
          if (shouldSerialize(prop)) prop.get(conn)?.let {
            ext.extended[prop.name] = encode64(it)
          }
        }
        currentClazz = currentClazz.superclass
      }

      if (conn.unhandledProps.isNotEmpty()) {
        ext.extended[UNHANDLED_MARKER] = encode64(conn.unhandledProps.toMutableMap())
      }

      return ext
    }

    fun unpackData(conn: ConnectionData, errorHandler: ConnectionDataPackingErrorHandler = DummyErrorHandler): ConnectionData {
      if (conn !is ExtendedConnectionData || conn.fqn == "")
        return conn

      var fqn = conn.fqn

      if (RENAME_MAP.containsKey(conn.fqn))
        fqn = RENAME_MAP[conn.fqn]!!

      if (conn.extended.containsKey(OLD_FQN_KEY)) {
        val oldFqn = decode64(conn.extended[OLD_FQN_KEY]!!, conn)
        if (RENAME_MAP.containsKey(oldFqn)) {
          fqn = RENAME_MAP[oldFqn]!!
          conn.extended.remove(OLD_FQN_KEY)
        }
      }

      fun tryLoad(name: String): KClass<out Any>? {
        val pluginId = findPluginByClassName(conn)
        val plugin = PluginManagerCore.getPlugin(pluginId)
        if (plugin?.isEnabled != true)
          return null
        return plugin.pluginClassLoader?.loadClass(name)?.kotlin

      }

      val kClass = try {
        tryLoad(fqn)
      }
      catch (e: Throwable) {
        val renamed =
          (ConnectionSettingProviderEP.getAll().flatMap { it.createConnectionGroups() }.find { it.id == conn.groupId } as? ConnectionFactory<*>)?.let {
            try {
              tryLoad(it.newData().javaClass.name)
            }
            catch (e: Exception) {
              null
            }
          }

        if (renamed != null)
          renamed
        else {
          if (!conn.extended.containsKey(OLD_FQN_KEY)) conn.extended[OLD_FQN_KEY] = encode64(conn.fqn)
          errorHandler.handleError(e, conn)
          return conn
        }
      }

      val packedData = kClass?.primaryConstructor?.callBy(emptyMap()) as? ConnectionData

      if (packedData == null) return conn

      packedData.copyFrom(conn)
      val extendedMap = HashMap(conn.extended)

      @Suppress("UNCHECKED_CAST")
      val unhandledProps = (extendedMap[UNHANDLED_MARKER])?.let {
        try {
          decode64(it, conn) as? Map<String, Any?>
        }
        catch (e: Exception) {
          //probably we shouldn't show this one to the user
          logger.error("Can't deserialize unhandled props", e)
          null
        }
      }?.let { HashMap(it) } ?: hashMapOf()
      extendedMap.remove(UNHANDLED_MARKER)

      val superClasses = kClass.allSuperclasses + kClass

      for (currentClass in superClasses) {
        val iter = extendedMap.iterator()
        while (iter.hasNext()) {
          val entry = iter.next()
          val prop = currentClass.declaredMemberProperties.find { it is KMutableProperty<*> && it.name == entry.key } as? KMutableProperty<*>

          try {
            val vl = decode64(entry.value, conn)
            if (prop == null) {
              if (vl.toString().isNotBlank())
                unhandledProps[entry.key] = vl
            }
            else {
              val propType = prop.returnType
              if (vl::class.createType(propType.arguments, propType.isMarkedNullable).isSubtypeOf(propType))
                prop.setter.call(packedData, vl)
              unhandledProps.remove(entry.key)
              iter.remove()
            }
          }
          catch (e: Exception) {
            errorHandler.handleError(e, conn)
          }
        }
      }

      packedData.unhandledProps = unhandledProps

      return packedData
    }

    interface ConnectionDataPackingErrorHandler {
      fun handleError(e: Throwable, cd: ConnectionData)

      fun endLoading()
    }

    class BufferingErrorHandler : ConnectionDataPackingErrorHandler {
      private val errorsMap = mutableMapOf<ConnectionData, MutableList<Throwable>>()

      override fun handleError(e: Throwable, cd: ConnectionData) {
        errorsMap.getOrPut(cd) { mutableListOf() }.add(e)
      }

      override fun endLoading() {
        if (errorsMap.isEmpty()) return

        ConnectionUsagesCollector.logLoadErrors(errorsMap)

        val errorMessage = "Error while loading connections: " + errorsMap.keys.joinToString(separator = ", ") { cd ->
          "[" + cd.name + (if (cd is ExtendedConnectionData) ", " + cd.fqn else "") + "]"
        }

        logger.warn(errorMessage)
        errorsMap.forEach {
          it.value.forEach { throwable -> logger.error(throwable) }
        }
      }
    }

    object DummyErrorHandler : ConnectionDataPackingErrorHandler {
      override fun handleError(e: Throwable, cd: ConnectionData) {}
      override fun endLoading() {}
    }
  }

  private var connections = mutableListOf<ConnectionData>()

  override fun getState(): ConnectionPersistentState? = synchronized(this) {
    ConnectionPersistentState(connections.map { packData(it) })
  }

  protected open fun unpackData(conn: ConnectionData): ConnectionData = ConnectionSettingsBase.unpackData(conn)

  override fun loadState(state: ConnectionPersistentState) = synchronized(this) {
    val errorHandler = BufferingErrorHandler()
    val connectionData = state.connections.map { unpackData(it, errorHandler) }

    connectionData.forEach {
      try {
        it.migrate()
      }
      catch (t: Throwable) {
        errorHandler.handleError(Exception("Migration connection settings failed. Conn name: ${it.name}, id: ${it.innerId}", t), it)
        //thisLogger().error("Migration connection settings failed. Conn name: ${it.name}, id: ${it.innerId}", t)
      }
    }

    errorHandler.endLoading()
    setConnections(connectionData)
  }

  fun getNotParsedConnections(): List<ConnectionData> = synchronized(this) {
    val distinctConnection = connections.distinctBy { it.innerId }
    if (connections.size != distinctConnection.size) {
      connections = distinctConnection.toMutableList()
      logger.error("There are two or more connection with the same id! " +
                   "Connections: ${connections.size}, distinct: ${distinctConnection.size}")
    }

    return distinctConnection.filterNot { it.isSupportedByPlugin() }
  }

  fun getConnections(): List<ConnectionData> = synchronized(this) {
    val distinctConnection = connections.distinctBy { it.innerId }
    if (connections.size != distinctConnection.size) {
      connections = distinctConnection.toMutableList()
      logger.error("There are two or more connection with the same id! " +
                   "Connections: ${connections.size}, distinct: ${distinctConnection.size}")
    }

    val enabledConnections = distinctConnection.filter { it.isSupportedByPlugin() }
    if (BdIdeRegistryUtil.isInternalFeaturesAvailable())
      enabledConnections
    else
      enabledConnections.filterNot { it is InternalFeature }
  }

  fun setConnections(connections: List<ConnectionData>) = synchronized(this) {
    this.connections = connections.toMutableList()
  }

  fun removeConnection(id: String) = synchronized(this) {
    connections.removeIf { it.innerId == id }
  }

  fun addConnection(conn: ConnectionData) = synchronized(this) {
    if (connections.any { it.innerId == conn.innerId }) {
      logger.error("Try to add connection with exists innerId.")
      return@synchronized false
    }
    connections.add(conn)
    return@synchronized true
  }

  override fun dispose() = Unit

  private class PluginObjectInputStream(inp: InputStream, private val forData: ExtendedConnectionData) : ObjectInputStream(inp) {
    private fun loadClassInner(loader: ClassLoader, fqn: String): Class<*>? {
      return try {
        Class.forName(fqn, false, loader)
      }
      catch (_: ClassNotFoundException) {
        null
      }
    }

    private fun findPluginLoader(): ClassLoader = findPluginByClassName(forData)?.let {
      PluginManagerCore.getPlugin(it)?.pluginClassLoader
    } ?: this.javaClass.classLoader

    private fun tryRename(name: String): String? = RENAME_MAP[name]

    override fun readClassDescriptor(): ObjectStreamClass {
      val baseDescriptor = super.readClassDescriptor()
      val newName = tryRename(baseDescriptor.name) ?: baseDescriptor.name
      val actualClass = loadClassInner(findPluginLoader(), newName) ?: return baseDescriptor
      val localDescriptor = ObjectStreamClass.lookup(actualClass) ?: return baseDescriptor

      if (baseDescriptor.serialVersionUID != localDescriptor.serialVersionUID) {
        val clazz = ObjectStreamClass::class.java

        val uidField = clazz.getDeclaredField("suid")
        uidField.trySetAccessible()
        uidField.set(baseDescriptor, Long.valueOf(localDescriptor.serialVersionUID))

        val nameField = clazz.getDeclaredField("name")
        nameField.trySetAccessible()
        nameField.set(baseDescriptor, localDescriptor.name)
      }

      return baseDescriptor
    }

    override fun resolveClass(desc: ObjectStreamClass?): Class<*> {
      if (desc == null) return super.resolveClass(null)

      val loader = findPluginLoader()

      val loaded = loadClassInner(loader, desc.name)
      if (loaded != null) return loaded

      val newName = tryRename(desc.name)

      return newName?.let {
        loadClassInner(loader, newName)
      } ?: super.resolveClass(desc)
    }
  }
}

/**
 * This field/property won't be serialized. Only takes effect on ConnectionData properties
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class DoNotSerialize
