package com.jetbrains.bigdatatools.kafka.aws.connection.auth

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

class AuthenticationType private constructor(val id: String, @Nls val title: String) {
  companion object {
    val DEFAULT: AuthenticationType = AuthenticationType("Default credential providers chain", KafkaMessagesBundle.message("settings.auth.type.default"))
    val KEY_PAIR: AuthenticationType = AuthenticationType("Explicit access key and secret key", KafkaMessagesBundle.message("auth.type.keypair"))

    @Deprecated(message = "Use PROFILE_FROM_CREDENTIALS_FILE")
    val NAMED_PROFILE: AuthenticationType = AuthenticationType("Named profile", KafkaMessagesBundle.message("auth.type.namedprofile"))
    val PROFILE_FROM_CREDENTIALS_FILE: AuthenticationType = AuthenticationType("Profile from credentials file",
                                                                               KafkaMessagesBundle.message("auth.type.credentialsfile"))

    val ANON: AuthenticationType = AuthenticationType("anonymous", KafkaMessagesBundle.message("auth.type.anon"))

    @Suppress("DEPRECATION")
    fun migrateDeprecated(activeAuthenticationType: String): String = when (activeAuthenticationType) {
      NAMED_PROFILE.id -> PROFILE_FROM_CREDENTIALS_FILE.id
      else -> activeAuthenticationType
    }

    val values: List<AuthenticationType> = listOf(DEFAULT, KEY_PAIR, PROFILE_FROM_CREDENTIALS_FILE, ANON)

    fun getById(id: String): AuthenticationType = values.firstOrNull { it.id == id } ?: DEFAULT
  }
}