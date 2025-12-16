package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Application-level service for Confluent Cloud authentication.
 * Manages OAuth context and background token refresh.
 *
 * @see CCloudOAuthContext
 * @see CCloudTokenRefreshBean
 * @see CCloudTokenStorage
 */
@Service(Service.Level.APP)
class CCloudAuthService : Disposable {
    private val logger = thisLogger()

    private var context: CCloudOAuthContext? = null
    private var refreshBean: CCloudTokenRefreshBean? = null

    /**
     * Initialize the service with an authenticated context.
     * Saves session and starts background token refresh.
     * @param authenticatedContext The authenticated context to sign in with
     */
    fun signIn(authenticatedContext: CCloudOAuthContext) {
        signOut()

        context = authenticatedContext

        // Persist session
        CCloudTokenStorage.saveSession(authenticatedContext)

        // Start background refresh
        refreshBean = CCloudTokenRefreshBean(authenticatedContext, this).also { it.start() }

        logger.info("Signed in as ${authenticatedContext.getUserEmail()}")
    }

    /**
     * Clear the current session and stop refresh.
     */
    fun signOut() {
        refreshBean?.stop()
        refreshBean = null
        context = null
        CCloudTokenStorage.clearSession()
    }

    fun isSignedIn(): Boolean = context?.getControlPlaneToken() != null

    fun getContext(): CCloudOAuthContext? = context

    fun getControlPlaneToken(): String? = context?.getControlPlaneToken()?.token

    fun getDataPlaneToken(): String? = context?.getDataPlaneToken()?.token

    fun getUserEmail(): String? = context?.getUserEmail()

    fun getOrganizationName(): String? = context?.getCurrentOrganization()?.name

    fun isRefreshRunning(): Boolean = refreshBean?.isRunning() == true

    override fun dispose() {
        refreshBean?.stop()
        refreshBean = null
        context = null
    }

    companion object {
        fun getInstance(): CCloudAuthService = service()
    }
}
