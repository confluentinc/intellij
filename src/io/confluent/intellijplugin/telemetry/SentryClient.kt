package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.diagnostic.Logger
import io.sentry.Sentry

object SentryClient {
    private val logger = Logger.getInstance(SentryClient::class.java)
    
    // Static initialization - runs when class is first accessed
    init {
        try {
            logger.info("Initializing Sentry")
            Sentry.init { options ->
                options.dsn = System.getenv("SENTRY_DSN")
                options.isDebug = false
            }
            logger.info("Sentry initialized successfully")
        } catch (e: Exception) {
            logger.error("Sentry initialization failed", e)
        }
    }

    fun captureException(exception: Throwable) {
        try {
            Sentry.captureException(exception)
            logger.debug("Exception captured and sent to Sentry")
        } catch (e: Exception) {
            logger.error("Failed to capture exception", e)
        }
    }
    
    /**
     * Creates a test error specifically for verifying Sentry integration
     * This should be used during development/testing only
     */
    fun sendTestError() {
        throw RuntimeException("[IGNORE, this is a TEST ERROR] Kafka Plugin Test Error - Sentry Integration Verification")
    }
}
