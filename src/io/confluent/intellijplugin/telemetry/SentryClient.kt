package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.diagnostic.Logger
import io.sentry.Sentry

object SentryClient {
    private val logger = Logger.getInstance(SentryClient::class.java)
    
    // Static initialization - runs when class is first accessed
    init {
        try {
            logger.info("Initializing Sentry with static initialization")
            Sentry.init { options ->
                options.dsn = "PUT-YOUR-DSN-HERE-FOR-TESTING"
                // Enable debug mode for initial testing
                options.isDebug = true
            }
            logger.info("Sentry initialized successfully via static init")
        } catch (e: Exception) {
            logger.error("Sentry static initialization failed", e)
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
        try {
            logger.info("Sending test error to Sentry for verification")
            throw RuntimeException("Kafka Plugin Test Error - Sentry Integration Verification")
        } catch (e: Exception) {
            captureException(e)
            logger.info("Test error sent to Sentry successfully")
        }
    }
}
