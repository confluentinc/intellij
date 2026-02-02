package io.confluent.intellijplugin.ccloud.auth

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import io.confluent.intellijplugin.telemetry.CCloudAuthenticationEvent
import io.confluent.intellijplugin.telemetry.logUsage
import io.confluent.intellijplugin.telemetry.logUser

/**
 * Application-level service for Confluent Cloud authentication.
 * Manages the complete OAuth flow, token management, and background refresh.
 *
 * Usage:
 *  ```
 *  CCloudAuthService.getInstance().signIn(
 *      onSuccess = { email -> showSuccess("Signed in as $email") },
 *      onError = { error -> showError(error) }
 *  ```
 *
 * @see CCloudOAuthContext
 * @see CCloudTokenRefreshBean
 * @see CCloudTokenStorage
 */
@Service(Service.Level.APP)
class CCloudAuthService : Disposable {
    private val logger = thisLogger()

    internal var context: CCloudOAuthContext? = null
    internal var refreshBean: CCloudTokenRefreshBean? = null

    /**
     * Start the OAuth sign-in flow.
     * Opens browser for authentication and calls back on completion.
     */
    fun signIn(
        onSuccess: (userEmail: String) -> Unit = {},
        onError: (error: String) -> Unit = {}
    ) {
        logger.info("Starting OAuth sign-in flow")

        val oauthContext = CCloudOAuthContext()

        val server = CCloudOAuthCallbackServer(
            oauthContext = oauthContext,
            onSuccess = { authenticatedContext ->
                completeSignIn(authenticatedContext)

                // Telemetry: identify user and track sign-in
                authenticatedContext.getUser()?.let { user ->
                    val emailRegex = Regex("@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-]+")
                    val domain = if (emailRegex.containsMatchIn(user.email)) {
                        user.email.substringAfter("@")
                    } else null

                    logUser(buildMap {
                        domain?.let { put("ccloudDomain", it) }
                        user.socialConnection?.let { put("ccloudSocialConnection", it) }
                    })
                }
                logUsage(CCloudAuthenticationEvent(status = "signed in"))

                // Switch to EDT for UI callback
                runBlocking {
                    withContext(Dispatchers.EDT) {
                        onSuccess(authenticatedContext.getUserEmail())
                    }
                }
            },
            onError = { error ->
                logger.error("Sign-in failed: $error")
                logUsage(CCloudAuthenticationEvent(status = "authentication failed", errorType = error))

                // Switch to EDT for UI callback
                runBlocking {
                    withContext(Dispatchers.EDT) {
                        onError(error)
                    }
                }
            }
        )

        server.start()
        BrowserUtil.browse(oauthContext.getSignInUri())
    }

    /**
     * Complete sign-in with an authenticated context.
     * Saves session and starts background refresh.
     * @param authenticatedContext The authenticated context to sign in with
     */
    private fun completeSignIn(authenticatedContext: CCloudOAuthContext) {
        signOut()

        context = authenticatedContext
        CCloudTokenStorage.saveSession(authenticatedContext)
        refreshBean = CCloudTokenRefreshBean(authenticatedContext, this).also { it.start() }

        logger.info("Signed in as ${authenticatedContext.getUserEmail()}")
    }

    /**
     * Sign out, clear current session and stop refresh.
     */
    fun signOut() {
        if (isSignedIn()) {
            logUsage(CCloudAuthenticationEvent(status = "signed out"))
        }

        refreshBean?.stop()
        refreshBean = null
        context = null
        CCloudTokenStorage.clearSession()
    }

    // State accessors

    fun isSignedIn(): Boolean = context?.getControlPlaneToken() != null

    fun getUserEmail(): String? = context?.getUserEmail()

    fun getOrganizationName(): String? = context?.getCurrentOrganization()?.name

    fun isRefreshRunning(): Boolean = refreshBean?.isRunning() == true

    // Token accessors

    fun getControlPlaneToken(): String? = context?.getControlPlaneToken()?.token

    fun getDataPlaneToken(): String? = context?.getDataPlaneToken()?.token

    /**
     * Get the context for advanced operations (e.g., manual refresh).
     * Prefer using token accessors for normal use.
     */
    fun getContext(): CCloudOAuthContext? = context

    override fun dispose() {
        refreshBean?.stop()
        refreshBean = null
        context = null
    }

    companion object {
        fun getInstance(): CCloudAuthService = service()
    }
}
