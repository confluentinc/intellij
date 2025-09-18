package com.jetbrains.bigdatatools.kafka.core.settings.fields

import com.intellij.ui.components.JBPasswordField
import com.jetbrains.bigdatatools.kafka.core.settings.ModificationKey
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
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