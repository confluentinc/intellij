package com.jetbrains.bigdatatools.kafka.core.settings.fields

import com.jetbrains.bigdatatools.kafka.core.settings.ModificationKey
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
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