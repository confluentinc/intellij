package io.confluent.intellijplugin.settings.app

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.bindSelected
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JComponent


/**
 * Application-level settings configurable for the Kafka plugin displays
 * IDE-wide preferences. These settings apply to all projects in the IDE.
 *
 * Location: Settings → Tools → Kafka
 *
 * @see KafkaPluginSettings for persistent storage
 */
class KafkaPluginSettingsConfigurable : SearchableConfigurable, Configurable.NoScroll {
        private val settings = KafkaPluginSettings.getInstance()
        private val savedPluginSettings = KafkaPluginSettingState.from(settings)

        override fun getId(): String = "kafka_plugin_settings"

        override fun getDisplayName(): String = KafkaMessagesBundle.message("plugin.settings.display.name")

        override fun createComponent(): JComponent = panel {
            group(KafkaMessagesBundle.message("plugin.settings.telemetry.group.label")) {
                row {
                    text(KafkaMessagesBundle.message("plugin.settings.usage.data.checkbox.description"))
                }
                row {
                    checkBox(KafkaMessagesBundle.message("plugin.settings.usage.data.checkbox.label"))
                        .bindSelected(savedPluginSettings::enableUsageData)
                }
            }
        }

        override fun isModified(): Boolean = savedPluginSettings != KafkaPluginSettingState.from(settings)

        override fun apply() {
            savedPluginSettings.applyTo(settings)
        }

        override fun reset() {
            savedPluginSettings.syncFrom(settings)
        }

        /**
         * Form state holder that syncs between UI, memory, and persistent storage when user edits settings.
         */
        private data class KafkaPluginSettingState(
            var enableUsageData: Boolean = true,
        ) {
            fun syncFrom(settings: KafkaPluginSettings) {
                enableUsageData = settings.enableUsageData
            }

            fun applyTo(settings: KafkaPluginSettings) {
                settings.enableUsageData = enableUsageData
            }

            /**
             * Helper function to convert settings from persistent storage to UI state
             * @see KafkaPluginSettings
             */
            companion object {
                fun from(settings: KafkaPluginSettings) = KafkaPluginSettingState(
                    enableUsageData = settings.enableUsageData,
                )
            }
        }
    }
