package io.confluent.intellijplugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Action to test error reporting functionality
 * This will trigger an exception that should be caught by the ErrorReportSubmitter
 */
class TestErrorReportingAction : AnAction("Test Error Reporting") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val result = Messages.showYesNoDialog(
            "This will trigger a test exception that should be sent to Sentry via the error reporter.\n\nProceed?",
            "Test Error Reporting",
            "Trigger Exception",
            "Cancel",
            Messages.getWarningIcon()
        )
        
        if (result == Messages.YES) {
            // This will trigger the ErrorReportSubmitter
            throw RuntimeException("Test exception from Kafka plugin - this should be reported to Sentry automatically!")
        }
    }
}
