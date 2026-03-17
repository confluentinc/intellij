package io.confluent.intellijplugin.core.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.util.KafkaMessagesBundle

/**
 * Sign-in / sign-out toggle for the Confluent Cloud node's context menu in connection settings.
 * Shows "Sign in" when not authenticated, "Sign out" when authenticated.
 *
 * @param isCCloudSelected returns true when the selected tree node is the CCloud group
 */
class CCloudSignInOutAction(
    private val isCCloudSelected: () -> Boolean
) : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        if (!isCCloudSelected()) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        if (CCloudAuthService.getInstance().isSignedIn()) {
            e.presentation.text = KafkaMessagesBundle.message("confluent.cloud.settings.sign.out")
            e.presentation.icon = AllIcons.Actions.Exit
        } else {
            e.presentation.text = KafkaMessagesBundle.message("confluent.cloud.welcome.panel.cta")
            e.presentation.icon = AllIcons.General.User
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (!isCCloudSelected()) return

        val authService = CCloudAuthService.getInstance()
        if (authService.isSignedIn()) {
            authService.signOut(invokedPlace = "settings_panel")
        } else {
            authService.signIn(invokedPlace = "settings_panel")
        }
    }
}
