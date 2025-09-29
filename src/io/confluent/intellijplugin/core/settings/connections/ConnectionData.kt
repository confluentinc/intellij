package io.confluent.intellijplugin.core.settings.connections

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.defaultui.ConnectionStatus
import java.io.Serializable

abstract class ConnectionData(
  var innerId: String = "",
  var groupId: String = "",
  @NlsSafe
  var name: String = "",
  @NlsSafe var uri: String = "",
  @Deprecated("port value stored directly in uri string or in other fields")
  var port: Int? = null,
  var isEnabled: Boolean = true,
  var isPerProject: Boolean = false,
  var anonymous: Boolean = false,

  var sourceConnection: String? = null
) : Serializable {
  companion object {
    private val logger = Logger.getInstance(this::class.java)
    const val SERVICE_PREFIX: String = "BigDataIDEConnectionSettings"
    const val TEST_SUFFIX: String = ":test"

    fun getUrlWithHttp(uri: String): String {
      fun withoutLasSlash(string: String): String {
        return if (string.endsWith("/")) string.substring(0, string.length - 1) else string
      }
      return if (uri.startsWith("http")) withoutLasSlash(uri) else "http://${withoutLasSlash(uri)}"
    }
  }

  abstract fun createDriver(project: Project?, isTest: Boolean): Driver

  abstract fun createConfigurable(project: Project, parentGroup: ConnectionGroup): ConnectionConfigurable<*, *>

  open fun copyFrom(c2: ConnectionData): ConnectionData {
    innerId = c2.innerId
    name = c2.name
    groupId = c2.groupId
    uri = c2.uri
    @Suppress("DEPRECATION")
    port = c2.port
    isEnabled = c2.isEnabled
    isPerProject = c2.isPerProject
    anonymous = c2.anonymous
    sourceConnection = c2.sourceConnection
    return this
  }

  /** Used for:
   * - cloning ConnectionData in ConnectionSettingsPanel
   * - clearing connections at connection removal
   * null value is not included in the list but is also allowed as main (default) credentials id
   */
  open fun credentialIds(): List<CredentialId> = emptyList()

  fun clearCredentials() {
    credentialIds().plus(null).forEach { credentialId ->
      setCredentials(null, credentialId)
    }
  }

  private fun createCredentialsAttrs(credentialId: CredentialId?): CredentialAttributes {
    val connectionFamilyId = innerId.removeSuffix(TEST_SUFFIX)
    val id = if (credentialId == null) connectionFamilyId else Pair(connectionFamilyId, credentialId.value).toString()
    return CredentialAttributes("$SERVICE_PREFIX@$id")
  }

  fun requireCredentialId(id: CredentialId?) {
    require(id in credentialIds() + null) { "credential id $id not listed: ${credentialIds()}" }
  }

  open fun setCredentials(credentials: Credentials?, id: CredentialId? = null) {
    requireCredentialId(id)
    PasswordSafe.instance.set(createCredentialsAttrs(id), credentials)
  }

  open fun getCredentials(id: CredentialId? = null): Credentials? {
    requireCredentialId(id)
    return try {
      PasswordSafe.instance.get(createCredentialsAttrs(id))
    }
    catch (t: Throwable) {
      logger.warn("Cannot find user credentials", t)
      null
    }
  }


  open fun migrate() {}

  open fun notReloadRequiredKeys(): Collection<ModificationKey> = listOf()

  @Transient
  var unhandledProps: HashMap<String, Any?> = hashMapOf()
}

interface ConnectionTesting<D : ConnectionData> {
  suspend fun ConnectionTestingSession<D>.validateAndTest()
}

class ConnectionTestingSession<D : ConnectionData>(
  val testConnectionData: D,
  val testDisposable: Disposable,
  val dialogDisposable: Disposable,
  val modalityState: ModalityState,
  val updateStatusIndicator: suspend (ConnectionStatus) -> Unit,
  val runAgainByExternalEvent: () -> Unit
) {
  var driver: Driver? = null
}

@JvmInline
value class CredentialId(val value: String)

val ConnectionData.connType: BdtConnectionType?
  get() {
    return BdtConnectionType.getForId(groupId)
  }