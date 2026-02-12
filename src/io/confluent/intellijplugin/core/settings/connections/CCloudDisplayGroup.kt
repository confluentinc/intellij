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
import javax.swing.JComponent
import javax.swing.JPanel

class CCloudDisplayGroup : ConnectionGroup(
    id = GROUP_ID,
    name = KafkaMessagesBundle.message("confluent.cloud.name"),
    icon = BigdatatoolsKafkaIcons.ConfluentTab
) {
    companion object {
        const val GROUP_ID: String = "CCloudDisplayGroup"
        private const val SIGN_IN_CARD = "signin"
        private const val SIGNED_IN_CARD = "signedin"
    }

    override fun createOptionsPanel(): JComponent {
        val cardLayout = CardLayout()
        val cardPanel = JPanel(cardLayout)

        cardPanel.add(createSignInPanel(), SIGN_IN_CARD)
        cardPanel.add(createSignedInPanel(), SIGNED_IN_CARD)

        val activeCard = if (CCloudAuthService.getInstance().isSignedIn()) SIGNED_IN_CARD else SIGN_IN_CARD
        cardLayout.show(cardPanel, activeCard)

        // Listen for auth state changes from other UI surfaces
        val listener = object : CCloudAuthService.AuthStateListener {
            override fun onSignedIn(email: String) {
                cardPanel.add(createSignedInPanel(), SIGNED_IN_CARD)
                cardLayout.show(cardPanel, SIGNED_IN_CARD)
            }

            override fun onSignedOut() {
                cardLayout.show(cardPanel, SIGN_IN_CARD)
            }
        }
        CCloudAuthService.getInstance().addAuthStateListener(listener)

        return cardPanel
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
        val email = CCloudAuthService.getInstance().getUserEmail() ?: ""
        return panel {
            row {
                cell(JBPanelWithEmptyText().apply {
                    emptyText.apply {
                        appendText(
                            KafkaMessagesBundle.message("confluent.cloud.notification.sign.in.success.text", email),
                            StatusText.DEFAULT_ATTRIBUTES
                        )
                        appendSecondaryText(
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
