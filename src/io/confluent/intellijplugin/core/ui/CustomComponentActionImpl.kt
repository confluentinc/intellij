package io.confluent.intellijplugin.core.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import javax.swing.JComponent

/**
 * DumbAwareAction, which can contain any user component.
 *
 * {@code
 * val toolbar = DefaultActionGroup(CustomComponentAction(JLabel("Custom component!")))
 * }
 */
open class CustomComponentActionImpl(private val component: JComponent) : DumbAwareAction(), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String) = component
    override fun actionPerformed(e: AnActionEvent) {}
}