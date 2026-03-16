package io.confluent.intellijplugin.core.settings.connections

import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IconUtil
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.ui.CCloudSignInPanel
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.CardLayout
import java.awt.GridBagLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

class CCloudDisplayGroup : ConnectionGroup(
    id = GROUP_ID,
    name = KafkaMessagesBundle.message("confluent.cloud.name"),
    icon = BigdatatoolsKafkaIcons.ConfluentTab
) {
    companion object {
        const val GROUP_ID: String = "CCloudDisplayGroup"
        private const val SIGN_IN_CARD = "signin"
        private const val SIGNED_IN_CARD = "signedin"
        private const val EXPIRY_REFRESH_INTERVAL_MS = 60_000

        private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

        fun formatSessionExpiry(endOfLifetime: Instant?): String {
            if (endOfLifetime == null) return ""

            val now = Instant.now()
            if (!now.isBefore(endOfLifetime)) {
                return KafkaMessagesBundle.message("confluent.cloud.settings.session.expired")
            }

            val totalMinutes = ChronoUnit.MINUTES.between(now, endOfLifetime)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60

            val localTime = endOfLifetime.atZone(ZoneId.systemDefault())
            val absolute = dateTimeFormatter.format(localTime)

            val relative = when {
                hours > 0 && minutes > 0 -> KafkaMessagesBundle.message("confluent.cloud.settings.session.expiry.hours.minutes", hours, minutes, absolute)
                hours > 0 -> KafkaMessagesBundle.message("confluent.cloud.settings.session.expiry.hours", hours, absolute)
                minutes > 0 -> KafkaMessagesBundle.message("confluent.cloud.settings.session.expiry.minutes", minutes, absolute)
                else -> KafkaMessagesBundle.message("confluent.cloud.settings.session.expiry.less.than.minute", absolute)
            }
            return KafkaMessagesBundle.message("confluent.cloud.settings.session.expires.label", relative)
        }
    }

    private var authListener: CCloudAuthService.AuthStateListener? = null
    private var expiryTimer: Timer? = null

    override fun createOptionsPanel(): JComponent {
        val cardLayout = CardLayout()
        val cardPanel = JPanel(cardLayout)
        var signedInPanel: JComponent? = null
        var signInPanel: JComponent? = null

        fun replaceSignedInPanel() {
            signedInPanel?.let { cardPanel.remove(it) }
            signedInPanel = createSignedInPanel()
            cardPanel.add(signedInPanel, SIGNED_IN_CARD)
            cardPanel.revalidate()
            cardPanel.repaint()
        }

        fun replaceSignInPanel(message: String? = null) {
            signInPanel?.let { cardPanel.remove(it) }
            signInPanel = CCloudSignInPanel.create("settings_panel", message = message)
            cardPanel.add(signInPanel, SIGN_IN_CARD)
            cardPanel.revalidate()
            cardPanel.repaint()
        }

        replaceSignInPanel()
        replaceSignedInPanel()

        val activeCard = if (CCloudAuthService.getInstance().isSignedIn()) SIGNED_IN_CARD else SIGN_IN_CARD
        cardLayout.show(cardPanel, activeCard)

        // Periodically rebuild the signed-in panel so the relative expiry stays fresh
        expiryTimer = Timer(EXPIRY_REFRESH_INTERVAL_MS) {
            if (CCloudAuthService.getInstance().isSignedIn()) {
                replaceSignedInPanel()
                cardLayout.show(cardPanel, SIGNED_IN_CARD)
            }
        }.apply { start() }

        // Listen for auth state changes from other UI surfaces
        val listener = object : CCloudAuthService.AuthStateListener {
            override fun onSignedIn(email: String) {
                replaceSignInPanel()
                replaceSignedInPanel()
                cardLayout.show(cardPanel, SIGNED_IN_CARD)
            }

            override fun onSignedOut(reason: String) {
                val isSessionExpiry = reason == "session_expired" || reason == "refresh_failed"
                if (isSessionExpiry) {
                    replaceSignInPanel(KafkaMessagesBundle.message("confluent.cloud.settings.session.expired"))
                }
                cardLayout.show(cardPanel, SIGN_IN_CARD)
            }
        }
        authListener = listener
        CCloudAuthService.getInstance().addAuthStateListener(listener)

        return cardPanel
    }

    override fun disposeOptionsPanel() {
        expiryTimer?.stop()
        expiryTimer = null
        authListener?.let { CCloudAuthService.getInstance().removeAuthStateListener(it) }
        authListener = null
    }

    private fun createSignedInPanel(): JComponent {
        val authService = CCloudAuthService.getInstance()
        val email = authService.getUserEmail() ?: ""
        val orgName = authService.getOrganizationName()
        val sessionExpiry = formatSessionExpiry(authService.getSessionEndOfLifetime())

        val content = panel {
            row {
                icon(IconUtil.scale(BigdatatoolsKafkaIcons.ConfluentTab, null, CCloudSignInPanel.ICON_SCALE))
                    .align(AlignX.CENTER)
            }.bottomGap(BottomGap.SMALL)
            row {
                label(KafkaMessagesBundle.message("confluent.cloud.notification.sign.in.success.text", email))
                    .align(AlignX.CENTER)
                    .bold()
            }.bottomGap(BottomGap.NONE)
            if (orgName != null) {
                row {
                    comment(KafkaMessagesBundle.message("confluent.cloud.settings.organization.label", orgName))
                        .align(AlignX.CENTER)
                }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)
            }
            if (sessionExpiry.isNotEmpty()) {
                row {
                    comment(sessionExpiry)
                        .align(AlignX.CENTER)
                }.topGap(TopGap.NONE)
            }
            row {
                link(KafkaMessagesBundle.message("confluent.cloud.settings.sign.out")) {
                    CCloudAuthService.getInstance().signOut(invokedPlace = "settings_panel")
                }.align(AlignX.CENTER)
            }
        }

        return JPanel(GridBagLayout()).apply { add(content) }
    }
}
