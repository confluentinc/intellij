package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Test: Sign in to CCloud.
 * Tools → "Test CCloud OAuth Sign In"
 */
class CCloudOAuthTestAction : AnAction(
    "Test CCloud OAuth Sign In",
    "Sign in to Confluent Cloud and start background token refresh",
    null
) {
    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        logger.warn("=== CCLOUD SIGN IN ===")

        CCloudAuthService.getInstance().signIn(
            onSuccess = { email ->
                logger.warn("✅ Signed in as $email")
                ApplicationManager.getApplication().invokeLater {
                    val org = CCloudAuthService.getInstance().getOrganizationName()
                    Messages.showInfoMessage(project, "✅ Signed in as $email\n\nOrg: $org", "CCloud Sign In")
                }
            },
            onError = { error ->
                logger.error("❌ Sign in failed: $error")
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, "Sign-in failed: $error", "CCloud Sign In")
                }
            }
        )

        Messages.showInfoMessage(project, "Complete sign-in in your browser.", "CCloud Sign In")
    }
}

/**
 * Test: Verify tokens work.
 * Tools → "Test CCloud Tokens"
 */
class CCloudTokenVerifyTestAction : AnAction(
    "Test CCloud Tokens",
    "Verify tokens work by calling CCloud APIs",
    null
) {
    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val authService = CCloudAuthService.getInstance()

        if (!authService.isSignedIn()) {
            Messages.showWarningDialog(project, "Not signed in.", "Token Verify")
            return
        }

        logger.warn("=== VERIFY TOKENS ===")

        CoroutineScope(Dispatchers.IO).launch {
            val results = StringBuilder()
            val context = authService.getContext()!!
            val token = authService.getControlPlaneToken()!!

            // Test JWT
            context.checkAuthenticationStatus().fold(
                onSuccess = { results.appendLine("✅ JWT valid") },
                onFailure = { results.appendLine("❌ JWT: ${it.message}") }
            )

            // Test API call
            try {
                val basePath = System.getProperty("ccloud.base-path", "confluent.cloud")
                CCloudOAuthHttpClient.getRaw("https://api.$basePath/org/v2/environments", token)
                results.appendLine("✅ API call works")
            } catch (ex: Exception) {
                results.appendLine("❌ API: ${ex.message}")
            }

            // Data plane token
            results.appendLine(
                if (authService.getDataPlaneToken() != null) "✅ Data plane token present"
                else "❌ No data plane token"
            )

            ApplicationManager.getApplication().invokeLater {
                Messages.showInfoMessage(project, results.toString(), "Token Verify")
            }
        }
    }
}

/**
 * Test: Manual token refresh.
 * Tools → "Test Auto Refresh"
 */
class CCloudAutoRefreshTestAction : AnAction(
    "Test Auto Refresh",
    "Trigger manual refresh and verify scheduler",
    null
) {
    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val authService = CCloudAuthService.getInstance()

        if (!authService.isSignedIn()) {
            Messages.showWarningDialog(project, "Not signed in.", "Refresh Test")
            return
        }

        val context = authService.getContext()!!

        logger.warn("=== MANUAL REFRESH ===")
        logger.warn("Refresh running: ${authService.isRefreshRunning()}")
        logger.warn("shouldRefresh: ${context.shouldAttemptTokenRefresh()}")
        logger.warn("expiresAt: ${context.expiresAt()}")

        CoroutineScope(Dispatchers.IO).launch {
            context.refreshIgnoreFailures().fold(
                onSuccess = {
                    CCloudTokenStorage.saveSession(context)
                    logger.warn("✅ Refresh success, new expiry: ${context.expiresAt()}")
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "✅ Refresh successful\n\nNew expiry: ${context.expiresAt()}",
                            "Refresh Test"
                        )
                    }
                },
                onFailure = { error ->
                    logger.warn("❌ Refresh failed: ${error.message}")
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Refresh failed: ${error.message}", "Refresh Test")
                    }
                }
            )
        }
    }
}

/**
 * Clear session.
 * Tools → "Clear CCloud Session"
 */
class CCloudClearSessionAction : AnAction(
    "Clear CCloud Session",
    "Clear stored tokens and stop scheduler",
    null
) {
    private val logger = thisLogger()

    override fun actionPerformed(e: AnActionEvent) {
        logger.warn("=== SIGN OUT ===")
        CCloudAuthService.getInstance().signOut()
        Messages.showInfoMessage(e.project, "✅ Signed out", "Clear Session")
    }
}
