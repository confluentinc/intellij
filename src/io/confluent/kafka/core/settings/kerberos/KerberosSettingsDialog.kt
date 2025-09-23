package io.confluent.kafka.core.settings.kerberos

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.JComponent
import javax.swing.SwingUtilities

class KerberosSettingsDialog(project: Project) : DialogWrapper(project) {
  val panel = KerberosSettingsPanel(project)

  init {
    Disposer.register(this.disposable, panel)
    init()
    title = KafkaMessagesBundle.message("kerberos.settings.title")
    setOKButtonText(KafkaMessagesBundle.message("kerberos.settings.button.ok"))
  }

  override fun showAndGet(): Boolean {
    val res = super.showAndGet()
    if (res && panel.isModified(BdtKerberosManager.instance)) {
      panel.apply(BdtKerberosManager.instance)
    }
    return res
  }

  override fun createCenterPanel(): JComponent {
    panel.reset(BdtKerberosManager.instance)
    return panel.component
  }

  // This will skip the validation results and allow us to press "Apply"
  // even if we have entered invalid data.
  override fun setOKActionEnabled(isEnabled: Boolean) = Unit

  override fun updateErrorInfo(info: List<ValidationInfo>) {
    SwingUtilities.invokeLater {
      if (isDisposed) return@invokeLater
      setErrorInfoAll(info)
    }
  }
}