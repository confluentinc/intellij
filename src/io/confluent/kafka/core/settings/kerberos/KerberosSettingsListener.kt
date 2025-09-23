package io.confluent.kafka.core.settings.kerberos

interface KerberosSettingsListener {
  fun kerberosSettingsChanged()
}