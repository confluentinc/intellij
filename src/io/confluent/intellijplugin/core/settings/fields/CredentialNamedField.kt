package io.confluent.intellijplugin.core.settings.fields

import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import javax.swing.JTextField

abstract class CredentialNamedField<D : ConnectionData>(
  key: ModificationKey,
  protected val credentialsHolder: CredentialsHolder<D>
) : WrappedNamedComponent<D>(key) {

  val isInitialized
    get() = credentialsHolder.initialized

  abstract fun getTextComponent(): JTextField

  override fun apply(conn: D) {
    credentialsHolder.apply(conn)
  }

  override fun isModified(conn: D): Boolean {
    return credentialsHolder.isModified()
  }
}