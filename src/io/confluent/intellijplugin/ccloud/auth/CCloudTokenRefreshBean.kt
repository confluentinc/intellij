package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.telemetry.CCloudAuthenticationEvent
import io.confluent.intellijplugin.telemetry.logUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background token refresh scheduler using coroutines.
 * Checks token expiration at regular intervals and refreshes when needed.
 */
class CCloudTokenRefreshBean(
    private val context: CCloudOAuthContext,
    parentDisposable: Disposable,
    private val onTerminal: ((reason: SignOutReason) -> Unit)? = null,
) : Disposable {
    private val logger = thisLogger()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var refreshJob: Job? = null

    init {
        Disposer.register(parentDisposable, this)
    }

    fun start() {
        if (refreshJob?.isActive == true) {
            logger.warn("Token refresh scheduler already running")
            return
        }

        logger.info("Token refresh scheduler started (interval: ${CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL.inWholeSeconds}s)")
        refreshJob = coroutineScope.launch {
            // Loop until cancelled externally (stop/dispose) or auth state becomes terminal (errors/expired)
            while (isActive && shouldContinueRefreshing()) {
                delay(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL)
                refreshIfNeeded()
            }
            logger.info("Token refresh loop exited")
        }
    }

    /**
     * Check if we've hit non-transient errors (too many failures or end of lifetime)
     */
    private fun shouldContinueRefreshing(): Boolean {
        if (context.hasNonTransientError()) {
            logger.warn("Stopping refresh: non-transient error after ${context.getFailedTokenRefreshAttempts()} failed attempts")
            onTerminal?.invoke(SignOutReason.REFRESH_FAILED)
            return false
        }
        if (context.hasReachedEndOfLifetime()) {
            logger.warn("Stopping refresh: session reached end of lifetime")
            onTerminal?.invoke(SignOutReason.SESSION_EXPIRED)
            return false
        }
        return true
    }

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
        logger.info("Token refresh scheduler stopped")
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

    fun isRunning(): Boolean = refreshJob?.isActive == true

    private suspend fun refreshIfNeeded() {
        val shouldRefresh = context.shouldAttemptTokenRefresh()
        val expiresAt = context.expiresAt()
        val failedAttempts = context.getFailedTokenRefreshAttempts()

        logger.info("Scheduler check: shouldRefresh=$shouldRefresh, expiresAt=$expiresAt, failedAttempts=$failedAttempts/${CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS}")

        if (!shouldRefresh) {
            return
        }

        logger.info("Refreshing tokens...")

        // Use refresh() to track failures - after MAX_TOKEN_REFRESH_ATTEMPTS consecutive
        // failures, hasNonTransientError() becomes true and refresh stops
        context.refresh().fold(
            onSuccess = {
                logger.info("Token refresh successful - new expiresAt: ${context.expiresAt()}")
                CCloudTokenStorage.saveSession(context)
            },
            onFailure = { error ->
                val attempts = context.getFailedTokenRefreshAttempts()
                logger.warn("Token refresh failed ($attempts/${CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS}): ${error.message}")

                // Fire telemetry on the first failure only — if the issue is terminal,
                // a SignedOut(reason="refresh_failed") event will follow
                if (attempts == 1) {
                    logUsage(CCloudAuthenticationEvent.TokenRefreshFailed(errorType = error.message))
                }
            }
        )
    }
}
