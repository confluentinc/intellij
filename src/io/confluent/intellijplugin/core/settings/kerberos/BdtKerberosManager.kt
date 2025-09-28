package io.confluent.intellijplugin.core.settings.kerberos

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingCancellable
import io.confluent.intellijplugin.core.connection.exception.BdtConfigurationException
import io.confluent.intellijplugin.core.connection.exception.BdtKerberosException
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.security.auth.Subject
import javax.security.auth.login.Configuration
import javax.security.auth.login.LoginContext

@Service
class BdtKerberosManager {
  val settings: KerberosSettings
    get() = KerberosSettings.instance()

  var jaasPath: String
    get() = System.getProperty(JAAS_PATH_ENV, settings.loginConfig) ?: ""
    set(value) {
      if (value.isNotBlank()) {
        System.setProperty(JAAS_PATH_ENV, value)
        settings.loginConfig = value
      }
      else {
        System.clearProperty(JAAS_PATH_ENV)
        settings.loginConfig = ""
      }
    }

  var krb5Config: String
    get() = System.getProperty(KRB5_CONF_PATH_ENV, settings.krb5Config) ?: ""
    set(value) {
      if (value.isNotBlank()) {
        System.setProperty(KRB5_CONF_PATH_ENV, value)
        settings.krb5Config = value
      }
      else {
        System.clearProperty(KRB5_CONF_PATH_ENV)
        settings.krb5Config = ""
      }
    }


  var krb5Debug
    get() = System.getProperty(KRB_5_DEBUG_ENV)?.toBooleanStrictOrNull() ?: settings.krb5Debug
    set(value) {
      System.setProperty(KRB_5_DEBUG_ENV, value.toString())
      settings.krb5Debug = value
    }

  var jgssDebug
    get() = System.getProperty(JGSS_DEBUG_ENV)?.toBooleanStrictOrNull() ?: settings.jgssDebug
    set(value) {
      System.setProperty(JGSS_DEBUG_ENV, value.toString())
      settings.jgssDebug = value
    }

  suspend fun getCredentials(): Credentials? {
    return withContext(Dispatchers.IO) {
      val credentialAttributes = createCredentialsAttrs()
      try {
        PasswordSafe.instance.get(credentialAttributes)
      }
      catch (t: Throwable) {
        logger.warn("Cannot find user credentials", t)
        null
      }
    }
  }

  suspend fun setCredentials(value: Credentials?) {
    withContext(Dispatchers.IO) {
      val credentialAttributes = createCredentialsAttrs()
      PasswordSafe.instance.set(credentialAttributes, value)
    }
  }

  private var cachedLoginContexts = mutableMapOf<String, LoginContext>()

  private val listeners = mutableListOf<KerberosSettingsListener>()

  fun addListener(listener: KerberosSettingsListener) = listeners.add(listener)
  fun removeListener(listener: KerberosSettingsListener) = listeners.remove(listener)
  fun settingsChanged() = listeners.forEach { it.kerberosSettingsChanged() }

  fun login(force: Boolean = false, jaasEntryName: String): LoginContext = synchronized(this) {
    //Init envs from persistent values if empty
    setupKerberosValues()


    System.setProperty(USE_SUBJECT_ONLY_ENV, "false")

    // Another options, but this taken from krb5.conf and login.conf
    //System.setProperty("java.security.krb5.realm","CLOUDERA");
    //System.setProperty("java.security.krb5.kdc","quickstart.cloudera");

    val loginContext = cachedLoginContexts[jaasEntryName]

    if (loginContext != null && !force)
      return loginContext

    try {
      cachedLoginContexts.remove(jaasEntryName)
      loginContext?.logout()
    }
    catch (t: Throwable) {
      logger.error("Cannot logout", t)
    }

    val configuration = Configuration.getConfiguration()
    configuration.refresh()

    val withRefresh = configuration.getAppConfigurationEntry(jaasEntryName).all {
      "true".equals(it.options["refreshKrb5Config"]?.toString() ?: "", ignoreCase = true)
    }

    val credentials = runBlockingCancellable {
      getCredentials()
    }
    val username = credentials?.userName?.ifBlank { null }
    val password = credentials?.password?.chars
    val lc: LoginContext = object : LoginContext(jaasEntryName, Subject(), KerberosCallBackHandler(username, password), configuration) {}

    try {
      lc.login()
      cachedLoginContexts[jaasEntryName] = lc
      lc
    }
    catch (t: Throwable) {
      val warning = KafkaMessagesBundle.message("kerberos.settings.test.warning", jaasEntryName).takeIf { withRefresh } ?: ""
      throw BdtKerberosException(t, additionalNotification = warning)
    }
  }

  fun setupKerberosValues() {
    if (System.getProperty(JAAS_PATH_ENV).isNullOrBlank() && settings.loginConfig.isNotBlank()) {
      jaasPath = settings.loginConfig
    }
    if (System.getProperty(KRB5_CONF_PATH_ENV).isNullOrBlank() && settings.krb5Config.isNotBlank()) {
      krb5Config = settings.krb5Config
    }
    if (System.getProperty(KRB_5_DEBUG_ENV).isNullOrBlank()) {
      krb5Debug = settings.krb5Debug
    }
    if (System.getProperty(JGSS_DEBUG_ENV).isNullOrBlank()) {
      jgssDebug = settings.jgssDebug
    }
  }

  fun validateCacheSupported() {
    if (krb5Config.isBlank())
      return

    val file = File(krb5Config).readText()
    val cacheString = file.split("\n").firstOrNull { it.contains("default_ccache_name") }
    if (cacheString == null) {
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.cache.param.is.not.found"))
    }
    val value = cacheString.split("=").last().trim()
    val prefix = "FILE:"
    if (!value.startsWith(prefix, ignoreCase = true))
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.cache.param.is.not.file"))
    val cachePath = value.drop(prefix.length)
    if (!File(cachePath).exists()) {
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.cache.file.is.not.exists", cachePath))
    }
  }


  fun validateJaas() {
    if (jaasPath.isBlank())
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.jaas"))
  }

  fun validateKeytab(keytab: String?) {
    if ((keytab ?: "").isBlank())
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.keytab"))
  }

  private fun createCredentialsAttrs() = CredentialAttributes("kerberos.credentials")

  fun validatePrincipal(principal: String?) {
    if ((principal ?: "").isBlank())
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.principal"))
  }

  fun validatePassword(password: CharArray?) {
    if (password?.isNotEmpty() != true)
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.password"))
  }

  fun validateJaasEntry(jaasEntry: String?) {
    if ((jaasEntry ?: "").isBlank())
      throw BdtConfigurationException(KafkaMessagesBundle.message("kerberos.validation.jaas.entry"))
  }

  companion object {
    val instance: BdtKerberosManager
      get() = service()

    private val logger = thisLogger()

    private const val JAAS_PATH_ENV = "java.security.auth.login.config"
    private const val KRB5_CONF_PATH_ENV = "java.security.krb5.conf"
    private const val KRB_5_DEBUG_ENV = "sun.security.krb5.debug"
    private const val JGSS_DEBUG_ENV = "sun.security.jgss.debug"
    private const val USE_SUBJECT_ONLY_ENV = "javax.security.auth.useSubjectCredsOnly"
  }
}