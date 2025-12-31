package io.confluent.intellijplugin.toolwindow.confluent

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.core.util.runAsync
import io.confluent.intellijplugin.toolwindow.confluent.controllers.ConfluentMainController
import io.confluent.intellijplugin.toolwindow.confluent.ui.ConfluentCredentialsPanel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Controller for the Confluent Cloud toolwindow.
 * Manages OAuth authentication and the main controller.
 */
@Service(Service.Level.PROJECT)
class ConfluentToolWindowController(private val project: Project) {
    private var mainController: ConfluentMainController? = null
    private var driver: ConfluentDriver? = null
    private var isInitialized = false

    fun setUp(toolWindow: ToolWindow) {
        toolWindow.isAutoHide = false
        toolWindow.setToHideOnEmptyContent(false)

        val contentManager = toolWindow.contentManager

        // Create the sign-in panel
        val credentialsPanel = ConfluentCredentialsPanel()
        val containerPanel = JPanel(BorderLayout())

        // Placeholder panel while not connected
        val placeholderPanel = JPanel(BorderLayout()).apply {
            add(credentialsPanel, BorderLayout.NORTH)
        }
        containerPanel.add(placeholderPanel, BorderLayout.CENTER)

        // OAuth Sign In
        credentialsPanel.addSignInListener {
            credentialsPanel.setLoading(true)
            credentialsPanel.clearStatus()

            CCloudAuthService.getInstance().signIn(
                onSuccess = { email ->
                    runInEdt {
                        credentialsPanel.showSuccess("Signed in as $email")
                        // Initialize UI with OAuth
                        initializeWithOAuth(
                            containerPanel = containerPanel,
                            onSuccess = {
                                credentialsPanel.setLoading(false)
                            },
                            onError = { message ->
                                credentialsPanel.setLoading(false)
                                credentialsPanel.showError(message)
                            }
                        )
                    }
                },
                onError = { error ->
                    runInEdt {
                        credentialsPanel.setLoading(false)
                        credentialsPanel.showError("Sign in failed: $error")
                    }
                }
            )
        }

        val content: Content = contentManager.factory.createContent(
            containerPanel,
            "Confluent Cloud",
            false
        ).apply {
            isCloseable = false
        }

        Disposer.register(content) {
            dispose()
        }

        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
    }

    /**
     * Initialize the UI using OAuth authentication.
     * OAuth tokens are retrieved from CCloudAuthService automatically.
     */
    private fun initializeWithOAuth(
        containerPanel: JPanel,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        runAsync {
            try {
                // Create connection data for OAuth
                val connectionData = ConfluentConnectionData("Confluent Cloud")

                // Create the driver
                val newDriver = ConfluentDriver(connectionData, project, testConnection = false)

                // Initialize the driver (will use OAuth tokens via CloudRestClient.getAuthHeaders())
                newDriver.initDriverUpdater()

                // Switch to EDT for UI updates
                runInEdt {
                    driver = newDriver

                    Disposer.register(project, newDriver)

                    val controller = ConfluentMainController(project, newDriver)
                    Disposer.register(newDriver, controller)
                    controller.init()
                    mainController = controller

                    // Replace the container content with the main controller
                    containerPanel.removeAll()
                    containerPanel.add(controller.getComponent(), BorderLayout.CENTER)
                    containerPanel.revalidate()
                    containerPanel.repaint()

                    isInitialized = true
                    onSuccess()
                }
            } catch (e: Exception) {
                runInEdt {
                    onError(e.message ?: "Failed to load resources")
                }
            }
        }
    }

    fun dispose() {
        // mainController is a child of driver in Disposer, so it will be disposed automatically
        mainController = null
        driver?.let { Disposer.dispose(it) }
        driver = null
        isInitialized = false
    }

    companion object {
        const val TOOL_WINDOW_ID = "ConfluentToolWindow"

        fun getInstance(project: Project): ConfluentToolWindowController? {
            return project.getService(ConfluentToolWindowController::class.java)
        }
    }
}
