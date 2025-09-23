package io.confluent.kafka.core.settings.kerberos

import io.confluent.kafka.util.KafkaMessagesBundle
import javax.security.auth.callback.Callback
import javax.security.auth.callback.CallbackHandler
import javax.security.auth.callback.NameCallback
import javax.security.auth.callback.PasswordCallback

/**
 * Used in BdtKerberosManager do not remove!
 */
@Suppress("unused")
class KerberosCallBackHandler(val login: String?, val password: CharArray?) : CallbackHandler {
  override fun handle(callbacks: Array<out Callback>) {
    for (c in callbacks) {
      if (c is NameCallback) {
        c.name = login ?: error(KafkaMessagesBundle.message("kerberos.credentials.error.credential"))
      }
      if (c is PasswordCallback) {
        c.password = password ?: error(KafkaMessagesBundle.message("kerberos.credentials.error.credential"))
      }
    }
  }
}