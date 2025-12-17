package io.confluent.intellijplugin.toolwindow.confluent

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Factory for the Confluent Cloud toolwindow.
 * Creates a separate toolwindow for OAuth/Confluent Cloud environments.
 */
class ConfluentToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        ConfluentToolWindowController.getInstance(project)?.setUp(toolWindow)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Confluent Cloud"
    }
}

