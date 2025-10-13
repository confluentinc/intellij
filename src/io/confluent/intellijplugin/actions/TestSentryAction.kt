package io.confluent.intellijplugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.telemetry.SentryClient

class TestSentryAction : AnAction("Test Sentry Integration") {
    
    override fun actionPerformed(e: AnActionEvent) {
        try {
            // Send test error to verify Sentry integration
            SentryClient.sendTestError()
            
            Messages.showInfoMessage(
                "Test error sent to Sentry! Check your Sentry dashboard for the 'Kafka Plugin Test Error'.", 
                "Sentry Test"
            )
        } catch (exception: Exception) {
            Messages.showErrorDialog(
                "Failed to send test error: ${exception.message}",
                "Sentry Test Failed"
            )
        }
    }
}
