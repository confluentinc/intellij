package io.confluent.intellijplugin.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JComponent

class TelemetryConfigurable : SearchableConfigurable, Configurable.NoScroll {

    companion object {
        private val logger = Logger.getInstance(TelemetryConfigurable::class.java)
    }

    private var telemetryCheckBox: JBCheckBox? = null

    override fun getId(): String = "KafkaTelemetrySettings"

    override fun getDisplayName(): String = KafkaMessagesBundle.message("settings.telemetry.consent.group.title")

    override fun createComponent(): JComponent {
        return panel {
            group(KafkaMessagesBundle.message("settings.telemetry.consent.group.title")) {
                row {
                    telemetryCheckBox = checkBox(KafkaMessagesBundle.message("settings.telemetry.consent.label"))
                        .bindSelected(TelemetrySettings.getInstance()::telemetryConsent)
                        .comment(KafkaMessagesBundle.message("settings.telemetry.consent.description"))
                        .component
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val currentValue = TelemetrySettings.getInstance().telemetryConsent
        return telemetryCheckBox?.isSelected != currentValue
    }

    override fun apply() {
        val newValue = telemetryCheckBox?.isSelected ?: false
        val currentValue = TelemetrySettings.getInstance().telemetryConsent

        logger.debug("Telemetry configurable apply called - current_value: $currentValue, new_value: $newValue")
        TelemetrySettings.getInstance().telemetryConsent = newValue
        logger.info("Telemetry setting applied through UI - value: $newValue")
    }

    override fun reset() {
        val currentValue = TelemetrySettings.getInstance().telemetryConsent
        telemetryCheckBox?.isSelected = currentValue
    }
}

