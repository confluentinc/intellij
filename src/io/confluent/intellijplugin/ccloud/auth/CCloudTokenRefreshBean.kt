package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background token refresh scheduler using coroutines.
 * Checks token expiration at regular intervals and refreshes when needed.
 */
class CCloudTokenRefreshBean(
    private val context: CCloudOAuthContext,
    parentDisposable: Disposable
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
            while (true) {
                delay(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL)
                refreshIfNeeded()
            }
        }
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
        val endOfLifetime = context.getEndOfLifetime()

        logger.warn("Scheduler check: shouldRefresh=$shouldRefresh, expiresAt=$expiresAt, endOfLifetime=$endOfLifetime")

        if (!shouldRefresh) {
            return
        }

        logger.warn("Refreshing tokens...")

        // Use refreshIgnoreFailures for background refresh
        // This avoids marking the context with non-transient errors due to transient network issues
        context.refreshIgnoreFailures().fold(
            onSuccess = {
                logger.warn("Token refresh successful - new expiresAt: ${context.expiresAt()}")
                CCloudTokenStorage.saveSession(context)
            },
            onFailure = { error ->
                logger.warn("Token refresh failed: ${error.message}")
            }
        )
    }
}
