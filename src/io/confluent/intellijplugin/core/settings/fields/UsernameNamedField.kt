package io.confluent.intellijplugin.core.settings.fields

import com.intellij.ui.components.JBTextField
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import javax.swing.JComponent

class UsernameNamedField<D : ConnectionData>(
    key: ModificationKey,
    credentialsHolder: CredentialsHolder<D>
) : CredentialNamedField<D>(key, credentialsHolder) {

    private val userNameField = JBTextField(1)
    private val component = credentialsHolder.wrapUsernameField(userNameField)

    override fun getValue(): String = userNameField.text

    override fun getComponent(): JComponent = component
    override fun getTextComponent(): JBTextField = userNameField
}