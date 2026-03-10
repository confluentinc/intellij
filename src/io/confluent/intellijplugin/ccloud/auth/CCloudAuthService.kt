package io.confluent.intellijplugin.ccloud.auth

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.telemetry.CCloudAuthenticationEvent
import io.confluent.intellijplugin.telemetry.logUsage
import io.confluent.intellijplugin.telemetry.logUser
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application-level service for Confluent Cloud authentication.
 * Manages the complete OAuth flow, token management, and background refresh.
 *
 * Usage:
 *  ```
 *  CCloudAuthService.getInstance().signIn()  // Opens browser, notifies listeners on completion
 *  CCloudAuthService.getInstance().signOut()  // Clears session, notifies listeners
 *  ```
 *
 * @see CCloudOAuthContext
 * @see CCloudTokenRefreshBean
 * @see CCloudTokenStorage
 */
@Service(Service.Level.APP)
class CCloudAuthService(private val scope: CoroutineScope) : Disposable {
    private val logger = thisLogger()

    internal var context: CCloudOAuthContext? = null
    internal var refreshBean: CCloudTokenRefreshBean? = null

    internal val authStateListeners = CopyOnWriteArrayList<AuthStateListener>()

    /**
     * Listener for authentication state changes. Callbacks are invoked on the EDT.
     */
    interface AuthStateListener {
        fun onSignedIn(email: String) {}
        fun onSignedOut() {}
    }

    fun addAuthStateListener(listener: AuthStateListener) {
        authStateListeners.add(listener)
    }

    fun removeAuthStateListener(listener: AuthStateListener) {
        authStateListeners.remove(listener)
    }

    /**
     * Start the OAuth sign-in flow.
     * Opens browser for authentication, shows notifications, and notifies listeners on completion.
     */
    fun signIn() {
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

                notifySignedIn(authenticatedContext.getUserEmail())
            },
            onError = { error ->
                logger.error("Sign-in failed: $error")
                logUsage(CCloudAuthenticationEvent(status = "authentication failed", errorType = error))

                ApplicationManager.getApplication().invokeLater({
                    showSignInFailureNotification(error)
                }, ModalityState.any())
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
        signOut(notifyListeners = false)

        context = authenticatedContext
        CCloudTokenStorage.saveSession(authenticatedContext)
        refreshBean = CCloudTokenRefreshBean(authenticatedContext, this).also { it.start() }

        logger.info("Signed in as ${authenticatedContext.getUserEmail()}")
    }

    /**
     * Sign out, clear current session and stop refresh.
     * PasswordSafe I/O runs on a background thread; listeners are notified on EDT.
     */
    fun signOut() = signOut(notifyListeners = true)

    private fun signOut(notifyListeners: Boolean) {
        val wasSignedIn = isSignedIn()

        if (wasSignedIn) {
            logUsage(CCloudAuthenticationEvent(status = "signed out"))
        }

        refreshBean?.stop()
        refreshBean = null
        context = null

        // PasswordSafe access is a slow operation — run off EDT
        scope.launch(Dispatchers.IO) {
            CCloudTokenStorage.clearSession()
        }

        if (wasSignedIn && notifyListeners) {
            // Dispatch to EDT so UI updates work even when triggered from a modal dialog (from Settings)
            ApplicationManager.getApplication().invokeLater({
                authStateListeners.toList().forEach { it.onSignedOut() }
                showSignOutNotification()
            }, ModalityState.any())
        }
    }

    internal fun notifySignedIn(email: String) {
        // Dispatch to EDT so UI updates work even when triggered from a modal dialog (from Settings)
        ApplicationManager.getApplication().invokeLater({
            authStateListeners.toList().forEach { it.onSignedIn(email) }
            showSignInSuccessNotification(email)
        }, ModalityState.any())
    }

    internal fun showSignInSuccessNotification(email: String) {
        Notifications.Bus.notify(Notification(
            "Kafka Notification",
            KafkaMessagesBundle.message("confluent.cloud.notification.sign.in.success"),
            KafkaMessagesBundle.message("confluent.cloud.notification.sign.in.success.text", email),
            NotificationType.INFORMATION
        ))
    }

    internal fun showSignInFailureNotification(error: String) {
        Notifications.Bus.notify(Notification(
            "Kafka Notification",
            KafkaMessagesBundle.message("confluent.cloud.notification.sign.in.failure"),
            error,
            NotificationType.ERROR
        ))
    }

    internal fun showSignOutNotification() {
        Notifications.Bus.notify(Notification(
            "Kafka Notification",
            KafkaMessagesBundle.message("confluent.cloud.notification.sign.out"),
            "",
            NotificationType.INFORMATION
        ))
    }

    // State accessors

    fun isSignedIn(): Boolean = context?.getControlPlaneToken() != null

    fun getUserEmail(): String? = context?.getUserEmail()

    fun getOrganizationName(): String? = context?.getCurrentOrganization()?.name

    fun getSessionEndOfLifetime(): Instant? = context?.getEndOfLifetime()

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
        // scope is automatically cancelled by the platform when the service is disposed
    }

    companion object {
        fun getInstance(): CCloudAuthService = service()
    }
}
