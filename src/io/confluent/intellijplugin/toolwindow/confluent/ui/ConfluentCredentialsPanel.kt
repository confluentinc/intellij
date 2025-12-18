package io.confluent.intellijplugin.toolwindow.confluent.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Panel for Confluent Cloud OAuth authentication.
 */
class ConfluentCredentialsPanel : JPanel() {

    private val signInButton = JButton("Sign In to Confluent Cloud").apply {
        icon = AllIcons.Actions.Execute
    }

    private val statusLabel = JBLabel("").apply {
        foreground = UIUtil.getLabelInfoForeground()
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }

    init {
        layout = GridBagLayout()
        border = JBUI.Borders.empty(8)

        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(4)
            fill = GridBagConstraints.HORIZONTAL
        }

        // Row 0: Sign In button
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0
        add(signInButton, gbc)

        // Row 1: Status label
        gbc.gridy = 1
        add(statusLabel, gbc)
    }

    fun addSignInListener(listener: () -> Unit) {
        signInButton.addActionListener { listener() }
    }

    fun setLoading(isLoading: Boolean) {
        signInButton.isEnabled = !isLoading
        if (isLoading) {
            statusLabel.text = "Signing in..."
            statusLabel.icon = AllIcons.Process.Step_1
            statusLabel.foreground = UIUtil.getLabelInfoForeground()
        } else {
            statusLabel.icon = null
        }
    }

    fun showSuccess(message: String) {
        statusLabel.text = message
        statusLabel.icon = AllIcons.General.InspectionsOK
        statusLabel.foreground = JBColor.GREEN
    }

    fun showError(message: String) {
        statusLabel.text = message
        statusLabel.icon = AllIcons.General.Error
        statusLabel.foreground = JBColor.RED
    }

    fun clearStatus() {
        statusLabel.text = ""
        statusLabel.icon = null
        statusLabel.foreground = UIUtil.getLabelInfoForeground()
    }
}
