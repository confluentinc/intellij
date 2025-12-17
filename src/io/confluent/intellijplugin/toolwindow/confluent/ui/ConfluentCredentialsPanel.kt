package io.confluent.intellijplugin.toolwindow.confluent.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Panel for entering Confluent Cloud API credentials.
 * Temporary - will be replaced by OAuth flow.
 */
class ConfluentCredentialsPanel : JPanel() {

    private val credentialsField = JBTextField().apply {
        emptyText.text = "api_key:api_secret"
        columns = 40
    }

    private val loadButton = JButton("Load").apply {
        icon = AllIcons.Actions.Refresh
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

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        add(JBLabel("API Credentials:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        add(credentialsField, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        add(loadButton, gbc)

        gbc.gridx = 1
        gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        add(statusLabel, gbc)
    }

    private fun parseCredentials(): Pair<String, String>? {
        val parts = credentialsField.text.trim().split(":", limit = 2)
        return if (parts.size == 2 && parts.all { it.trim().isNotBlank() }) {
            Pair(parts[0].trim(), parts[1].trim())
        } else null
    }

    fun getApiKey(): String = parseCredentials()?.first ?: ""

    fun getApiSecret(): String = parseCredentials()?.second ?: ""

    fun areCredentialsValid(): Boolean = parseCredentials() != null

    fun addLoadListener(listener: () -> Unit) {
        loadButton.addActionListener { listener() }
    }

    fun setLoading(isLoading: Boolean) {
        credentialsField.isEnabled = !isLoading
        loadButton.isEnabled = !isLoading
        if (isLoading) {
            statusLabel.text = "Loading..."
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

