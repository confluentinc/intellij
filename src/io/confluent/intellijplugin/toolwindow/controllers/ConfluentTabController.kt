package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.StatusText
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.ConfluentDriver
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Controller for the fixed "Confluent Cloud" tab.
 * Shows sign-in UI when not authenticated, resource tree when authenticated.
 */
class ConfluentTabController(
    private val project: Project
) : ComponentController, Disposable, CCloudAuthService.AuthStateListener {

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private var driver: ConfluentDriver? = null
    private var resourceController: ConfluentMainController? = null

    private val signInPanel = createSignInPanel()

    companion object {
        private const val SIGN_IN_CARD = "signin"
        private const val RESOURCES_CARD = "resources"
    }

    init {
        cardPanel.add(signInPanel, SIGN_IN_CARD)

        CCloudAuthService.getInstance().addAuthStateListener(this)

        // Initialize with appropriate view
        if (CCloudAuthService.getInstance().isSignedIn()) {
            showResourcesView()
        } else {
            showSignInView()
        }
    }

    override fun onSignedIn(email: String) {
        showResourcesView()
    }

    override fun onSignedOut() {
        signOut()
    }

    private fun createSignInPanel(): JComponent {
        return panel {
            row {
                cell(JBPanelWithEmptyText().apply {
                    emptyText.apply {
                        appendText("Connect to Confluent Cloud\n\n", StatusText.DEFAULT_ATTRIBUTES)
                        appendSecondaryText(
                            "Sign in with OAuth",
                            com.intellij.ui.SimpleTextAttributes.LINK_ATTRIBUTES
                        ) {
                            performSignIn()
                        }
                        appendText(" to access your environments, clusters, and resources.", StatusText.DEFAULT_ATTRIBUTES)
                        isShowAboveCenter = false
                    }
                }).align(Align.FILL)
            }.resizableRow()
        }
    }

    private fun performSignIn() {
        CCloudAuthService.getInstance().signIn(
            onSuccess = { email ->
                showResourcesView()

                com.intellij.notification.Notifications.Bus.notify(
                    com.intellij.notification.Notification(
                        "Kafka Notification",
                        "Signed in to Confluent Cloud",
                        "Signed in as $email",
                        com.intellij.notification.NotificationType.INFORMATION
                    ),
                    project
                )
            },
            onError = { error ->
                com.intellij.notification.Notifications.Bus.notify(
                    com.intellij.notification.Notification(
                        "Kafka Notification",
                        "Sign in failed",
                        error,
                        com.intellij.notification.NotificationType.ERROR
                    ),
                    project
                )
            }
        )
    }

    private fun showSignInView() {
        cardLayout.show(cardPanel, SIGN_IN_CARD)
    }

    private fun showResourcesView() {
        // Create driver and controller if not already created
        if (driver == null) {
            val connectionData = ConfluentConnectionData("Confluent Cloud")

            driver = ConfluentDriver(connectionData, project, testConnection = false).also {
                it.initDriverUpdater()
                Disposer.register(this, it)
            }

            resourceController = ConfluentMainController(project, driver!!).also {
                it.init()
                Disposer.register(driver!!, it)

                // Add to card panel
                cardPanel.add(it.getComponent(), RESOURCES_CARD)
            }
        }

        cardLayout.show(cardPanel, RESOURCES_CARD)
    }

    fun signOut() {
        // Dispose driver and controller
        val resourceComponent = resourceController?.getComponent()
        driver?.let { Disposer.dispose(it) }
        driver = null
        resourceController = null

        // Remove resources card
        if (resourceComponent != null) {
            cardPanel.remove(resourceComponent)
        }

        // Show sign-in view
        showSignInView()
    }

    override fun getComponent(): JComponent = cardPanel

    fun getDriver(): ConfluentDriver? = driver

    override fun dispose() {
        CCloudAuthService.getInstance().removeAuthStateListener(this)
    }
}
