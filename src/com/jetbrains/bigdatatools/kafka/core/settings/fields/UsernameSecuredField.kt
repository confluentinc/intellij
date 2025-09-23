package io.confluent.kafka.core.settings.fields

import com.intellij.ui.components.JBPasswordField
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import javax.swing.JComponent

/**
 * The same as UsernameNamedField, but uses JBPasswordField for displaying.
 */
class UsernameSecuredField<D : ConnectionData>(
  key: ModificationKey,
  credentialsHolder: CredentialsHolder<D>
) : CredentialNamedField<D>(key, credentialsHolder) {

  private val usernameField = JBPasswordField()
  private val component = credentialsHolder.wrapUsernameField(usernameField)
  init {
    usernameField.columns = 1
  }

  override fun getValue(): CharArray = usernameField.password

  override fun getComponent(): JComponent = component
  override fun getTextComponent() = usernameField
}