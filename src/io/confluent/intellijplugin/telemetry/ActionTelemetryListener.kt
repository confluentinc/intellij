package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Automatically tracks all action invocations from the Kafka plugin for discovery purposes.
 *
 * This listener provides lightweight telemetry to understand which actions users invoke.
 * For detailed event tracking with additional properties, use manual `logUsage()` with appropriate @TelemetryEvent.
 */
class ActionTelemetryListener : AnActionListener {
    private val logger = thisLogger()

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        val actionClassName = action.javaClass.simpleName
        val registeredActionId = event.actionManager.getId(action)
        val actionIdOrClass = registeredActionId ?: actionClassName

        // Only track if it's a Kafka action git (either registered or known inner class)
        if (!isKafkaAction(actionIdOrClass) && !isKafkaActionClass(actionIdOrClass)) {
            return
        }

        try {
            var name = normalizeActionName(registeredActionId?: actionClassName)
            logUsage(ActionInvokedEvent(
                actionName = normalizeActionName(registeredActionId?: actionClassName),
                actionClassName = actionClassName,
                registeredActionId = registeredActionId,
                invokedPlace = event.place
            ))
            logger.warn("Tracked action: $name")
        } catch (e: Exception) {
            logger.debug("Failed to track action: $actionIdOrClass", e)
        }
    }

    /**
     * Normalizes action names to PascalCase for consistent action naming in Segment.
     * Examples:
     * - "kafka.create.producer" → "CreateProducer"
     * - "Kafka.Export.ToCsv" → "ExportToCsv"
     * - "kafka.DeleteTopicAction" → "DeleteTopic"
     */
    internal fun normalizeActionName(name: String): String {
        // Split by dots and capitalize each part, then remove "Action" suffix
        return name.removePrefix("Kafka.")
            .removePrefix("kafka.")
            .split('.')
            .joinToString("") { part ->
                part.replaceFirstChar { it.uppercase() }
            }
            .removeSuffix("Action")
    }

    /**
     * Determines if an action belongs to the Kafka plugin.
     * Actions defined in plugin.xml with IDs starting with "Kafka." or "kafka." are tracked.
     */
    internal fun isKafkaAction(actionId: String): Boolean {
        return actionId.startsWith("Kafka.") || actionId.startsWith("kafka.")
    }

    /**
     * Determines if an action class belongs to the Kafka plugin.
     * Used for actions without IDs (e.g., dynamically created inner classes).
     *
     * Note: Only tracks specific known inner classes. Most actions should be registered
     * in plugin.xml or use manual logUsage() calls for detailed tracking.
     */
    internal fun isKafkaActionClass(className: String): Boolean {
        return when (className) {
            // Tool window actions
            "CreateNewConnectionAction",     // "+" button in Kafka tool window tabs
            "MonitoringSettingsAction",      // Settings button in tool window
            // Settings panel actions
            "AddConnectionAction",           // "+" button in Settings → Kafka
            "RemoveConnectionAction",        // Delete button in Settings → Kafka
            "DuplicateConnectionAction"      // Duplicate button in Settings → Kafka
                -> true
            // Add other known inner classes here as needed
            else -> false
        }
    }
}
