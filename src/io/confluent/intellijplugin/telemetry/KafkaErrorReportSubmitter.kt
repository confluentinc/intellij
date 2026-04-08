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
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Component

/**
 * Filters and reports plugin exceptions to Sentry.
 * Non-plugin errors are rejected and fall back to JetBrains error reporter.
 */
class KafkaErrorReportSubmitter : ErrorReportSubmitter() {

    private val logger = Logger.getInstance(KafkaErrorReportSubmitter::class.java)

    override fun getReportActionText(): String =
        KafkaMessagesBundle.message("error.report.action.text")

    override fun getPrivacyNoticeText(): String =
        KafkaMessagesBundle.message("error.report.privacy.notice")

    /**
     * Handles error submission to Sentry.
     *
     * Flow:
     * 1. Filters events to keep only plugin-related errors
     * 2. If no plugin errors found → returns false → IntelliJ uses JetBrains reporter
     * 3. If plugin errors found → sends to Sentry in background → shows success/failure dialog
     *
     * @return true if we handle the error (shows Confluent dialog), false to use JetBrains reporter
     */
    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val context = DataManager.getInstance().getDataContext(parentComponent)
        val project = CommonDataKeys.PROJECT.getData(context)

        val pluginErrors = events.filter { event ->
            isPluginRelatedError(event)
        }

        if (pluginErrors.isEmpty()) {
            logger.debug("No plugin-related errors to report, delegating to JetBrains reporter")
            return false
        }

        object : Task.Backgroundable(project, KafkaMessagesBundle.message("error.report.sending.title")) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    logger.info("Sending ${pluginErrors.size} plugin error(s) to Sentry")

                    for (ideaEvent in pluginErrors) {
                        val throwable = ideaEvent.throwable
                        if (throwable != null) {
                            SentryClient.captureException(throwable)
                        } else {
                            // Create a fallback exception for message-only events
                            val fallbackException = RuntimeException(
                                ideaEvent.message ?: KafkaMessagesBundle.message("error.report.unknown.error")
                            )
                            SentryClient.captureException(fallbackException)
                        }
                    }

                    logger.info("Error report(s) sent to Sentry successfully")

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            parentComponent,
                            KafkaMessagesBundle.message("error.report.success.message"),
                            KafkaMessagesBundle.message("error.report.success.title")
                        )
                        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send error report to Sentry", e)

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            parentComponent,
                            KafkaMessagesBundle.message("error.report.failed.message", e.message ?: ""),
                            KafkaMessagesBundle.message("error.report.failed.title")
                        )
                        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
                    }
                }
            }
        }.queue()

        return true
    }

    /**
     * Checks if error originated from plugin code (io.confluent.intellijplugin).
     */
    internal fun isPluginRelatedError(event: IdeaLoggingEvent): Boolean {
        return event.throwableText.contains(TelemetryUtils.PLUGIN_PACKAGE)
    }

}
