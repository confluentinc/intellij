package io.confluent.intellijplugin.settings.app

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JComponent


/**
 * Application-level settings configurable for the Kafka plugin displays
 * IDE-wide preferences. These settings apply to all projects in the IDE.
 *
 * Location: Settings → Tools → Kafka
 *
 * @see KafkaPluginSettings for persistent storage
 * @see KafkaPluginSettingsConfigurableProvider for provider implementation
 */
class KafkaPluginSettingsConfigurable : SearchableConfigurable, Configurable.NoScroll {
    private val settings = KafkaPluginSettings.getInstance()
    private lateinit var panel: DialogPanel

    override fun getId(): String = "kafka_plugin_settings"

    override fun getDisplayName(): String = KafkaMessagesBundle.message("plugin.settings.display.name")

    override fun createComponent(): JComponent {
        panel = panel {
            group(KafkaMessagesBundle.message("plugin.settings.telemetry.group.label")) {
                row {
                    text(KafkaMessagesBundle.message("plugin.settings.usage.data.checkbox.description"))
                }
                row {
                    checkBox(KafkaMessagesBundle.message("plugin.settings.usage.data.checkbox.label"))
                        .bindSelected(settings::enableUsageData)
                }
            }
        }
        return panel
    }

    override fun isModified(): Boolean = panel.isModified()

    override fun apply() {
        panel.apply()
    }

    override fun reset() {
        panel.reset()
    }
}