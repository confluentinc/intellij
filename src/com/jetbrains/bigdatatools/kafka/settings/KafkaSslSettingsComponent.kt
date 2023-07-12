package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaSslSettingsComponent(val project: Project, val onUpdate: () -> Unit) {
  internal lateinit var sslTruststoreLocation: Cell<TextFieldWithBrowseButton>
  internal lateinit var sslTruststorePassword: Cell<JBPasswordField>

  internal lateinit var sslEnableValidateHostname: Cell<JBCheckBox>
  internal lateinit var sslKeystoreLocation: Cell<TextFieldWithBrowseButton>
  internal lateinit var sslKeystorePassword: Cell<JBPasswordField>
  internal lateinit var sslKeyPassword: Cell<JBPasswordField>

  internal lateinit var sslUseKeystore: Cell<JBCheckBox>
  private lateinit var sslKeystoreGroup: RowsRange

  fun create(panel: Panel) = panel.indent {
    row {
      sslEnableValidateHostname = checkBox(
        KafkaMessagesBundle.message("kafka.auth.enable.server.host.name.indetification")).onChanged {
        onUpdate()
      }
    }.bottomGap(BottomGap.SMALL)
    row(KafkaMessagesBundle.message("kafka.truststore.location")) {
      sslTruststoreLocation = textFieldWithBrowseButton(
        project = project,
        browseDialogTitle = KafkaMessagesBundle.message(
          "kafka.truststore.location.dialog.title")).align(AlignX.FILL).onChanged {
        onUpdate()
      }
    }
    row(KafkaMessagesBundle.message("kafka.truststore.password")) {
      sslTruststorePassword = passwordField().align(AlignX.FILL).onChanged {
        onUpdate()
      }
    }.bottomGap(BottomGap.SMALL)
    this.row {
      sslUseKeystore = checkBox(KafkaMessagesBundle.message("kafka.ssl.use.keystore")).onChanged {
        onUpdate()
      }
    }.topGap(TopGap.SMALL)

    sslKeystoreGroup = rowsRange {
      this.row(KafkaMessagesBundle.message("kafka.keystore.location")) {
        sslKeystoreLocation = textFieldWithBrowseButton(
          project = project,
          browseDialogTitle = KafkaMessagesBundle.message(
            "kafka.truststore.location.dialog.title")).align(AlignX.FILL).onChanged {
          onUpdate()
        }
      }
      this.row(KafkaMessagesBundle.message("kafka.keystore.password")) {
        sslKeystorePassword = passwordField().align(AlignX.FILL).onChanged {
          onUpdate()
        }
      }
      this.row(KafkaMessagesBundle.message("kafka.key.password")) {
        sslKeyPassword = passwordField().align(AlignX.FILL).onChanged {
          onUpdate()
        }
      }
    }.visibleIf(sslUseKeystore.selected)
  }

  fun applyConfig(config: KafkaSslConfig) {
    sslEnableValidateHostname.selected(config.validateHostName)
    sslTruststoreLocation.text(config.truststoreLocation)
    sslTruststorePassword.text(config.truststorePassword)
    sslUseKeystore.selected(config.useKeyStore)
    sslKeystoreLocation.text(config.keystoreLocation)
    sslKeystorePassword.text(config.keystorePassword)
    sslKeyPassword.text(config.keyPassword)
  }

  @Suppress("DEPRECATION")
  fun getConfig() = KafkaSslConfig(
    validateHostName = sslEnableValidateHostname.component.isSelected,
    truststoreLocation = sslTruststoreLocation.component.text,
    truststorePassword = sslTruststorePassword.component.text,
    useKeyStore = sslUseKeystore.component.isSelected,
    keyPassword = sslKeyPassword.component.text,
    keystoreLocation = sslKeystoreLocation.component.text,
    keystorePassword = sslKeystorePassword.component.text
  )
}