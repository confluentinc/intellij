package io.confluent.intellijplugin.toolwindow.confluent

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.core.util.runAsync
import io.confluent.intellijplugin.toolwindow.confluent.controllers.ConfluentMainController
import io.confluent.intellijplugin.toolwindow.confluent.ui.ConfluentCredentialsPanel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Controller for the Confluent Cloud toolwindow.
 * Manages authentication and the main controller.
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

        // Create the credentials panel for initial authentication
        val credentialsPanel = ConfluentCredentialsPanel()
        val containerPanel = JPanel(BorderLayout())

        // Placeholder panel while not connected
        val placeholderPanel = JPanel(BorderLayout()).apply {
            add(credentialsPanel, BorderLayout.NORTH)
        }
        containerPanel.add(placeholderPanel, BorderLayout.CENTER)

        credentialsPanel.addLoadListener {
            if (credentialsPanel.areCredentialsValid()) {
                credentialsPanel.setLoading(true)
                initializeWithCredentials(
                    apiKey = credentialsPanel.getApiKey(),
                    apiSecret = credentialsPanel.getApiSecret(),
                    containerPanel = containerPanel,
                    onSuccess = {
                        credentialsPanel.setLoading(false)
                        credentialsPanel.showSuccess("Connected!")
                    },
                    onError = { message ->
                        credentialsPanel.setLoading(false)
                        credentialsPanel.showError(message)
                    }
                )
            } else {
                credentialsPanel.showError("Invalid format. Expected: api_key:api_secret")
            }
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

    private fun initializeWithCredentials(
        apiKey: String,
        apiSecret: String,
        containerPanel: JPanel,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Run credential setup and connection in background to avoid EDT slow operations
        runAsync {
            try {
                // Create connection data with credentials (off EDT to avoid slow operation warning)
                val connectionData = ConfluentConnectionData("Confluent Cloud").apply {
                    this.apiKey = apiKey
                    this.apiSecret = apiSecret
                }

                // Create the driver directly
                val newDriver = ConfluentDriver(connectionData, project, testConnection = false)

                // Initialize the driver to trigger connection (this does network call)
                newDriver.initDriverUpdater()

                // Switch to EDT for UI updates
                runInEdt {
                    driver = newDriver

                    // Register with Disposer
                    Disposer.register(project, newDriver)

                    // Create the main controller with the driver directly
                    val controller = ConfluentMainController(project, newDriver)
                    // Register controller with driver so Disposer tree is valid before init()
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
                    onError(e.message ?: "Failed to connect")
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
