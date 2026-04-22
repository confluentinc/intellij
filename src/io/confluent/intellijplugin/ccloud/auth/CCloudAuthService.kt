package io.confluent.intellijplugin.ccloud.auth

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
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

/** Why the session ended, used for notification routing and telemetry. */
enum class SignOutReason(val value: String) {
    /** User clicked sign-out. */
    USER_INITIATED("user_initiated"),
    /** Session reached its end-of-lifetime. */
    SESSION_EXPIRED("session_expired"),
    /** Token refresh exhausted all retry attempts. */
    REFRESH_FAILED("refresh_failed");

    val isSessionExpiry get() = this == SESSION_EXPIRED || this == REFRESH_FAILED
}

/** Where a sign-in or sign-out was triggered from, recorded in telemetry events. */
enum class InvokedPlace(val value: String) {
    WELCOME_PANEL("welcome_panel"),
    SETTINGS_PANEL("settings_panel"),
    TOOL_WINDOW_ACTION("tool_window_action"),
    SESSION_EXPIRED_NOTIFICATION("session_expired_notification"),
}

/**
 * Application-level service for Confluent Cloud authentication.
 * Manages the complete OAuth flow, token management, and background refresh.
 *
 * Usage:
 *  ```
 *  CCloudAuthService.getInstance().signIn(InvokedPlace.WELCOME_PANEL)
 *  CCloudAuthService.getInstance().signOut()
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
        fun onSignedOut(reason: SignOutReason) {}
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
     *
     * @param invokedPlace telemetry identifier for where sign-in was triggered from
     */
    fun signIn(invokedPlace: InvokedPlace? = null) {
        logger.info("Starting OAuth sign-in flow")

        val oauthContext = CCloudOAuthContext()

        val server = CCloudOAuthCallbackServer(
            oauthContext = oauthContext,
            onSuccess = { authenticatedContext ->
                completeSignIn(authenticatedContext)

                // Telemetry: identify user and track sign-in
                val user = authenticatedContext.getUser()
                val domain = user?.email?.substringAfter("@", "")?.ifEmpty { null }

                user?.let {
                    logUser(buildMap {
                        domain?.let { put("ccloudDomain", it) }
                        it.socialConnection?.let { put("ccloudSocialConnection", it) }
                        it.resourceId?.let { put("ccloudUserId", it) }
                    })
                }
                logUsage(CCloudAuthenticationEvent.SignedIn(ccloudUserId = user?.resourceId , ccloudDomain = domain, invokedPlace = invokedPlace?.value))

                notifySignedIn(authenticatedContext.getUserEmail())
            },
            onError = { error ->
                logger.error("Sign-in failed: $error")
                logUsage(CCloudAuthenticationEvent.AuthenticationFailed(errorType = error, invokedPlace = invokedPlace?.value))

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
        context = authenticatedContext
        CCloudTokenStorage.saveSession(authenticatedContext)
        refreshBean = CCloudTokenRefreshBean(
            context = authenticatedContext,
            parentDisposable = this,
            onTerminal = { reason -> signOut(reason = reason) }
        ).also { it.start() }

        logger.info("Signed in as ${authenticatedContext.getUserEmail()}")
    }

    /**
     * Sign out, clear current session and stop refresh.
     * PasswordSafe I/O runs on a background thread; listeners are notified on EDT.
     *
     * @param reason why the session ended — determines which notification is shown:
     *   [SignOutReason.SESSION_EXPIRED] or [SignOutReason.REFRESH_FAILED] show the session-expired notification,
     *   [SignOutReason.USER_INITIATED] (default) shows the standard sign-out notification
     * @param invokedPlace where the user triggered sign-out from; only meaningful for [SignOutReason.USER_INITIATED]
     */
    fun signOut(reason: SignOutReason = SignOutReason.USER_INITIATED, invokedPlace: InvokedPlace? = null) {
        val wasSignedIn = isSignedIn()
        logger.info("Signing out (reason=$reason, wasSignedIn=$wasSignedIn)")

        if (wasSignedIn) {
            logUsage(CCloudAuthenticationEvent.SignedOut(reason = reason.value, invokedPlace = invokedPlace?.value))
        }

        refreshBean?.stop()
        refreshBean = null
        context = null

        // PasswordSafe access is a slow operation — run off EDT
        scope.launch(Dispatchers.IO) {
            CCloudTokenStorage.clearSession()
        }

        if (wasSignedIn) {
            // Dispatch to EDT so UI updates work even when triggered from a modal dialog (from Settings)
            ApplicationManager.getApplication().invokeLater({
                authStateListeners.toList().forEach { it.onSignedOut(reason) }
                if (reason.isSessionExpiry) {
                    showSessionExpiredNotification()
                } else {
                    showSignOutNotification()
                }
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

    internal fun showSessionExpiredNotification() {
        val notification = Notification(
            "Kafka Notification",
            KafkaMessagesBundle.message("confluent.cloud.notification.session.expired"),
            KafkaMessagesBundle.message("confluent.cloud.notification.session.expired.text"),
            NotificationType.WARNING
        )
        notification.addAction(
            NotificationAction.create(
                KafkaMessagesBundle.message("confluent.cloud.notification.session.expired.action")
            ) { _, n ->
                n.expire() // Explicitly auto-close notification after sign-in is clicked
                signIn(InvokedPlace.SESSION_EXPIRED_NOTIFICATION)
            }
        )
        Notifications.Bus.notify(notification)
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
