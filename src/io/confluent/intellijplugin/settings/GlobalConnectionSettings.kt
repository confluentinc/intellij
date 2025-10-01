package io.confluent.intellijplugin.core.settings

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(
  name = "ConfluentIntellijKafkaGlobalSettings",
  useLoadedStateAsExisting = false, // This is hack, needed because we need to transfer sensitive data from settings to PasswordSafe
  storages = [
    Storage("confluent_kafka_settings.xml")
  ]
)
@Service
class GlobalConnectionSettings : ConnectionSettingsBase() {
  companion object {
    fun getInstance(): GlobalConnectionSettings = service()
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(
      DynamicPluginListener.TOPIC,
      AdditionalPluginLoadingListener()
    )
  }
}