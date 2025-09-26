package io.confluent.intellijplugin.core.settings.fields

import com.intellij.credentialStore.Credentials
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.CredentialId
import io.confluent.intellijplugin.core.ui.components.MultilineTextFieldWithCompletion
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JComponent

class CredentialsHolder<D : ConnectionData> private constructor(private val credentialsId: CredentialId?, val disposable: Disposable) {

  private var initializedInternal = false
  val initialized = AtomicBooleanProperty(false)
  private var savedCredentials: Credentials? = null

  private fun loadInitialCredentials(value: Credentials?) {
    require(!initializedInternal)

    usernameField?.isEditable = true
    //  usernameField?.isFocusable = true
    usernameField?.emptyText?.clear()

    usernameSecuredField?.isEditable = true
    //  usernameSecuredField?.isFocusable = true
    usernameSecuredField?.emptyText?.clear()

    usernameEditorField?.isEnabled = true
    usernameEditorField?.text = ""

    passwordField?.isEditable = true
    //  passwordField?.isFocusable = true
    passwordField?.emptyText?.clear()

    initializedInternal = true

    if (value != null) {
      usernameField?.setText(value.userName.orEmpty())
      usernameSecuredField?.setText(value.userName.orEmpty())
      usernameEditorField?.setTextWithoutScroll(value.userName.orEmpty())
      passwordField?.setText(value.getPasswordAsString().orEmpty())
      savedCredentials = value
    }

    initialized.set(true)
  }

  companion object {
    @RequiresEdt
    operator fun <D : ConnectionData> invoke(initSettings: D,
                                             credentialsId: CredentialId?,
                                             disposable: Disposable,
                                             coroutineScope: CoroutineScope): CredentialsHolder<D> {
      initSettings.requireCredentialId(credentialsId)
      return CredentialsHolder<D>(credentialsId, disposable).also {
        coroutineScope.launch {
          try {
            val initialCredentials = withContext(Dispatchers.IO) {
              initSettings.getCredentials(credentialsId)
            }
            withContext(Dispatchers.EDT) {
              it.loadInitialCredentials(initialCredentials)
            }
          }
          catch (t: Throwable) {
            throw t
          }
        }
      }
    }
  }

  private var usernameField: JBTextField? = null
  fun wrapUsernameField(value: JBTextField): JComponent {
    usernameField = value
    if (!initializedInternal) {
      value.isEditable = false
      //   value.isFocusable = false
      value.emptyText.appendLine(KafkaMessagesBundle.message("settings.credential.loading.status"),
                                 SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES) {}
    }
    else {
      value.setText(savedCredentials?.userName.orEmpty())
    }
    return value
  }

  private var usernameSecuredField: JBPasswordField? = null
  fun wrapUsernameField(value: JBPasswordField): JComponent {
    usernameSecuredField = value
    if (!initializedInternal) {
      value.isEditable = false
      //   value.isFocusable = false
      value.emptyText.appendLine(KafkaMessagesBundle.message("settings.credential.loading.status"),
                                 SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES) {}
    }
    else {
      value.setText(savedCredentials?.userName.orEmpty())
    }
    return value
  }

  private var usernameEditorField: MultilineTextFieldWithCompletion? = null
  fun wrapUsernameField(value: MultilineTextFieldWithCompletion): JComponent {
    usernameEditorField = value
    if (!initializedInternal) {
      value.setTextWithoutScroll(KafkaMessagesBundle.message("settings.credential.loading.status"))
      usernameEditorField?.isEnabled = false
    }
    else {
      value.setTextWithoutScroll(savedCredentials?.userName.orEmpty())
    }
    return value
  }

  private var passwordField: JBPasswordField? = null
  fun wrapPasswordField(value: JBPasswordField): JComponent {
    passwordField = value
    if (!initializedInternal) {
      value.isEditable = false
      //   value.isFocusable = false
      value.emptyText.appendLine(KafkaMessagesBundle.message("settings.credential.loading.status"),
                                 SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES) {}
    }
    else {
      value.setText(savedCredentials?.getPasswordAsString().orEmpty())
    }
    return value
  }

  fun isModified(): Boolean {
    if (!initializedInternal) return false
    if (usernameField != null && usernameField?.text.orEmpty() != savedCredentials?.userName.orEmpty()) return true
    if (usernameSecuredField != null && usernameSecuredField?.password?.let(::String) != savedCredentials?.userName.orEmpty()) return true
    if (usernameEditorField != null && usernameEditorField?.text != savedCredentials?.userName.orEmpty()) return true
    if (passwordField != null && passwordField?.password?.let(::String) != savedCredentials?.getPasswordAsString()) return true
    return false
  }

  fun apply(conn: D) {
    if (!initializedInternal) return
    if (!isModified()) return
    val newUsername = usernameField?.text ?: usernameSecuredField?.password?.let(::String) ?: usernameEditorField?.text
                      ?: savedCredentials?.userName
    val newCredentials = if (passwordField != null) {
      Credentials(newUsername, passwordField?.password)
    }
    else {
      Credentials(newUsername, savedCredentials?.password)
    }
    val modalTaskOwnerComponent = checkNotNull(usernameField ?: usernameSecuredField ?: usernameEditorField ?: passwordField)
    runWithModalProgressBlocking(ModalTaskOwner.component(modalTaskOwnerComponent),
                                 KafkaMessagesBundle.message("progress.title.credentials.saving")) {
      conn.setCredentials(newCredentials, credentialsId)
    }
    savedCredentials = newCredentials
  }
}