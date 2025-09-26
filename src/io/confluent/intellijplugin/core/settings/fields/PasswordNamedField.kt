package io.confluent.intellijplugin.core.settings.fields

import com.intellij.ui.components.JBPasswordField
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import javax.swing.JComponent

class PasswordNamedField<D : ConnectionData>(
  key: ModificationKey,
  credentialsHolder: CredentialsHolder<D>
) : CredentialNamedField<D>(key, credentialsHolder) {

  private val passwordField = JBPasswordField()
  private val component = credentialsHolder.wrapPasswordField(passwordField)
  override fun getValue(): CharArray = passwordField.password

  init {
    passwordField.columns = 1
  }

  override fun getComponent(): JComponent = component
  override fun getTextComponent() = passwordField
}