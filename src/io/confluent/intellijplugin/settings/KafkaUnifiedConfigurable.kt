package io.confluent.intellijplugin.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.core.settings.ConnectionsConfigurable
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JComponent

class KafkaUnifiedConfigurable(private val project: Project) : SearchableConfigurable {

    companion object {
        private val logger = Logger.getInstance(KafkaUnifiedConfigurable::class.java)
    }

    private val connectionsConfigurable = ConnectionsConfigurable(project)
    private var telemetryCheckBox: JBCheckBox? = null

    override fun getId(): String = "KafkaUnifiedSettings"

    override fun getDisplayName(): String = "Kafka"

    override fun createComponent(): JComponent {
        return panel {
            // Add telemetry settings at the top
            group(KafkaMessagesBundle.message("settings.telemetry.consent.group.title")) {
                row {
                    telemetryCheckBox = checkBox(KafkaMessagesBundle.message("settings.telemetry.consent.label"))
                        .bindSelected(TelemetrySettings.getInstance()::telemetryConsent)
                        .comment(KafkaMessagesBundle.message("settings.telemetry.consent.description"))
                        .component
                }
            }

            // Add the existing connections settings
            group(KafkaMessagesBundle.message("settings.kafkaConnections.group.title")) {
                row {
                    cell(connectionsConfigurable.createComponent()).align(com.intellij.ui.dsl.builder.AlignX.FILL)
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val telemetryModified = telemetryCheckBox?.isSelected != TelemetrySettings.getInstance().telemetryConsent
        val connectionsModified = connectionsConfigurable.isModified
        return telemetryModified || connectionsModified
    }

    override fun apply() {
        // Apply telemetry settings
        val newTelemetryValue = telemetryCheckBox?.isSelected ?: false
        val currentTelemetryValue = TelemetrySettings.getInstance().telemetryConsent

        logger.debug("Kafka unified configurable apply called - telemetry current: $currentTelemetryValue, new: $newTelemetryValue")
        TelemetrySettings.getInstance().telemetryConsent = newTelemetryValue
        logger.info("Telemetry setting applied through unified UI - value: $newTelemetryValue")

        // Apply connection settings
        connectionsConfigurable.apply()
    }

    override fun reset() {
        // Reset telemetry settings
        val currentTelemetryValue = TelemetrySettings.getInstance().telemetryConsent
        telemetryCheckBox?.isSelected = currentTelemetryValue

        // Reset connection settings
        connectionsConfigurable.reset()
    }

    override fun disposeUIResources() {
        connectionsConfigurable.disposeUIResources()
        super.disposeUIResources()
    }
}
