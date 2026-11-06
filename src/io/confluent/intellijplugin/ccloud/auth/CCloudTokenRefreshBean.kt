package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.client.CCloudRetryPolicy
import io.confluent.intellijplugin.telemetry.CCloudAuthenticationEvent
import io.confluent.intellijplugin.telemetry.logUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Background token refresh scheduler using coroutines.
 * Checks token expiration every [checkInterval] and refreshes when needed. After a failed refresh
 * it waits [retryPolicy]'s backoff (floored by a 429's `Retry-After`) instead of the next tick.
 *
 * Backoff state is derived from [CCloudOAuthContext.getFailedTokenRefreshAttempts], so a new
 * sign-in (new context, new bean) always starts from the first delay.
 *
 * @param retryPolicy Backoff between failed refreshes (visible for tests). Only its timing is used
 * here; the attempt cap is enforced by [CCloudOAuthContext.refresh] via
 * [CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS].
 * @param checkInterval Idle tick between expiry checks (visible for tests)
 * @param sleep How to wait; tests inject a recorder so they don't sleep
 */
class CCloudTokenRefreshBean(
    private val context: CCloudOAuthContext,
    parentDisposable: Disposable,
    private val onTerminal: ((reason: SignOutReason) -> Unit)? = null,
    private val retryPolicy: CCloudRetryPolicy = CCloudOAuthConfig.TOKEN_REFRESH_RETRY_POLICY,
    private val checkInterval: Duration = CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
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

        logger.info("Token refresh scheduler started (interval: ${checkInterval.inWholeSeconds}s)")
        refreshJob = coroutineScope.launch {
            var nextDelay = checkInterval
            // Loop until cancelled externally (stop/dispose) or auth state becomes terminal (errors/expired)
            while (isActive && shouldContinueRefreshing()) {
                sleep(nextDelay)
                nextDelay = refreshIfNeeded()
            }
            logger.debug("Token refresh loop exited")
        }
    }

    /**
     * Check if we've hit non-transient errors (terminal error, too many failures or end of lifetime)
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

    /**
     * Refresh the tokens if one expires before the next tick.
     * @return how long to wait before the next check
     */
    private suspend fun refreshIfNeeded(): Duration {
        val shouldRefresh = context.shouldAttemptTokenRefresh()
        val expiresAt = context.expiresAt()
        val failedAttempts = context.getFailedTokenRefreshAttempts()

        logger.debug("Scheduler check: shouldRefresh=$shouldRefresh, expiresAt=$expiresAt, failedAttempts=$failedAttempts/${CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS}")

        if (!shouldRefresh) {
            return checkInterval
        }

        logger.debug("Refreshing tokens...")

        // refresh() tracks failures - a terminal error, or MAX_TOKEN_REFRESH_ATTEMPTS consecutive
        // failures, makes hasNonTransientError() true and the loop stops before waiting again
        return context.refresh().fold(
            onSuccess = {
                logger.info("Token refresh successful - new expiresAt: ${context.expiresAt()}")
                CCloudTokenStorage.saveSession(context)
                checkInterval
            },
            onFailure = { error ->
                val attempts = context.getFailedTokenRefreshAttempts()

                // Fire telemetry on the first failure only — if the issue is terminal,
                // a SignedOut(reason="refresh_failed") event will follow
                if (attempts == 1) {
                    logUsage(CCloudAuthenticationEvent.TokenRefreshFailed(errorType = error.message))
                }

                if (context.hasNonTransientError()) {
                    logger.warn("Token refresh failed ($attempts/${CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS}), not retrying: ${error.message}")
                    // The loop exits before waiting; the value is never used
                    return@fold checkInterval
                }

                val retryAfter = retryPolicy.parseRetryAfter(TokenRefreshErrorClassifier.retryAfterHeader(error))
                val backoff = retryPolicy.delayFor(retryIndex = (attempts - 1).coerceAtLeast(0), retryAfter = retryAfter)
                logger.warn("Token refresh failed ($attempts/${CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS}), retrying in ${backoff.inWholeMilliseconds}ms: ${error.message}")
                backoff
            }
        )
    }
}
