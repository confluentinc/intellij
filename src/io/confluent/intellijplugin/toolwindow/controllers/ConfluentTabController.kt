package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.ui.CCloudSignInPanel
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.settings.ConnectionSettings
import io.confluent.intellijplugin.settings.KafkaConnectionGroup
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.ConfluentDriver
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

    companion object {
        private const val SIGN_IN_CARD = "signin"
        private const val RESOURCES_CARD = "resources"
    }

    init {
        cardPanel.add(CCloudSignInPanel.create {
            ConnectionSettings.create(project, KafkaConnectionGroup(), null, true)
        }, SIGN_IN_CARD)

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
