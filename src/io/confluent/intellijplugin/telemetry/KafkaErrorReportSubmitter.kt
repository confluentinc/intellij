package io.confluent.intellijplugin.telemetry

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import java.awt.Component

/**
 * Automatic error reporter that sends plugin exceptions to Sentry
 */
class KafkaErrorReportSubmitter : ErrorReportSubmitter() {
    private val logger = Logger.getInstance(KafkaErrorReportSubmitter::class.java)

    override fun getReportActionText(): String = "Report to Confluent"

    override fun getPrivacyNoticeText(): String? = 
        "Error reports help improve the Kafka plugin. No personal data is collected."

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val context = DataManager.getInstance().getDataContext(parentComponent)
        val project = CommonDataKeys.PROJECT.getData(context)

        object : Task.Backgroundable(project, "Sending Error Report") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    logger.info("Sending ${events.size} error(s) to Sentry via SentryClient")
                    
                    for (ideaEvent in events) {
                        val throwable = extractThrowable(ideaEvent)
                        if (throwable != null) {
                            // Use SentryClient for consistent error handling
                            SentryClient.captureException(throwable)
                        } else {
                            // Create a fallback exception for message-only events
                            val fallbackException = RuntimeException(
                                ideaEvent.message ?: "Unknown error occurred in Kafka plugin"
                            )
                            SentryClient.captureException(fallbackException)
                        }
                        
                        // Add user notes as separate context if provided
                        if (!additionalInfo.isNullOrBlank()) {
                            logger.info("User provided additional context: $additionalInfo")
                        }
                    }

                    logger.info("Error report(s) sent to Sentry successfully via SentryClient")
                    
                    // Notify user of successful submission
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            parentComponent,
                            "Thank you for reporting this issue! It helps us improve the Kafka plugin.",
                            "Error Report Sent"
                        )
                        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send error report to Sentry", e)
                    
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            parentComponent,
                            "Failed to send error report: ${e.message}",
                            "Error Report Failed"
                        )
                        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
                    }
                }
            }
        }.queue()

        return true
    }

    private fun extractThrowable(ideaEvent: IdeaLoggingEvent): Throwable? {
        return ideaEvent.throwable
    }

}
