package io.confluent.intellijplugin.core.settings.connections

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.StatusText
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.CardLayout
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
                hours > 0 && minutes > 0 -> "in ${hours}h ${minutes}m"
                hours > 0 -> "in ${hours}h"
                minutes > 0 -> "in ${minutes}m"
                else -> "in <1m"
            }
            return "$relative ($absolute)"
        }
    }

    private var authListener: CCloudAuthService.AuthStateListener? = null
    private var expiryTimer: Timer? = null

    override fun createOptionsPanel(): JComponent {
        val cardLayout = CardLayout()
        val cardPanel = JPanel(cardLayout)
        var signedInPanel: JComponent? = null

        fun replaceSignedInPanel() {
            signedInPanel?.let { cardPanel.remove(it) }
            signedInPanel = createSignedInPanel()
            cardPanel.add(signedInPanel, SIGNED_IN_CARD)
            cardPanel.revalidate()
            cardPanel.repaint()
        }

        cardPanel.add(createSignInPanel(), SIGN_IN_CARD)
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
                replaceSignedInPanel()
                cardLayout.show(cardPanel, SIGNED_IN_CARD)
            }

            override fun onSignedOut() {
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

    private fun createSignInPanel(): JComponent {
        return panel {
            row {
                cell(JBPanelWithEmptyText().apply {
                    emptyText.apply {
                        appendText(
                            KafkaMessagesBundle.message("confluent.cloud.welcome.panel.title"),
                            StatusText.DEFAULT_ATTRIBUTES
                        )
                        appendSecondaryText(
                            KafkaMessagesBundle.message("confluent.cloud.welcome.panel.cta"),
                            SimpleTextAttributes.LINK_ATTRIBUTES
                        ) {
                            CCloudAuthService.getInstance().signIn()
                        }
                        appendText(
                            KafkaMessagesBundle.message("confluent.cloud.welcome.panel.label"),
                            StatusText.DEFAULT_ATTRIBUTES
                        )
                        isShowAboveCenter = false
                    }
                }).align(Align.FILL)
            }.resizableRow()
        }
    }

    private fun createSignedInPanel(): JComponent {
        val authService = CCloudAuthService.getInstance()
        val email = authService.getUserEmail() ?: ""
        val orgName = authService.getOrganizationName()
        val sessionExpiry = formatSessionExpiry(authService.getSessionEndOfLifetime())

        return panel {
            row {
                cell(JBPanelWithEmptyText().apply {
                    emptyText.apply {
                        appendText(
                            KafkaMessagesBundle.message("confluent.cloud.notification.sign.in.success.text", email),
                            StatusText.DEFAULT_ATTRIBUTES
                        )
                        if (orgName != null) {
                            appendLine(orgName, StatusText.DEFAULT_ATTRIBUTES, null)
                        }
                        if (sessionExpiry.isNotEmpty()) {
                            appendLine(
                                "Session expires $sessionExpiry",
                                SimpleTextAttributes.GRAYED_ATTRIBUTES,
                                null
                            )
                        }
                        appendLine(
                            KafkaMessagesBundle.message("confluent.cloud.settings.sign.out"),
                            SimpleTextAttributes.LINK_ATTRIBUTES
                        ) {
                            CCloudAuthService.getInstance().signOut()
                        }
                        isShowAboveCenter = false
                    }
                }).align(Align.FILL)
            }.resizableRow()
        }
    }
}
