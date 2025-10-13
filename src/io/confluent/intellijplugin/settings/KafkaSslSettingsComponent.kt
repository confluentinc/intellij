package io.confluent.intellijplugin.settings

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.*
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class KafkaSslSettingsComponent(val project: Project, val onUpdate: () -> Unit) {
    internal lateinit var sslTruststoreLocation: Cell<TextFieldWithBrowseButton>
    internal lateinit var sslTruststorePassword: Cell<JBPasswordField>

    internal lateinit var accessKey: Cell<TextFieldWithBrowseButton>
    internal lateinit var accessCertificate: Cell<TextFieldWithBrowseButton>
    internal lateinit var caCertificate: Cell<TextFieldWithBrowseButton>

    internal lateinit var sslEnableValidateHostname: Cell<JBCheckBox>
    internal lateinit var sslKeystoreLocation: Cell<TextFieldWithBrowseButton>
    internal lateinit var sslKeystorePassword: Cell<JBPasswordField>
    internal lateinit var sslKeyPassword: Cell<JBPasswordField>

    internal lateinit var sslUseKeystore: Cell<JBCheckBox>
    private lateinit var sslKeystoreGroup: RowsRange

    private val isCertificate = AtomicBooleanProperty(false)

    fun create(panel: Panel) = panel.indent {
        row {
            sslEnableValidateHostname = checkBox(
                KafkaMessagesBundle.message("kafka.auth.enable.server.host.name.indetification")
            ).onChanged {
                onUpdate()
            }
        }.bottomGap(BottomGap.SMALL)

        buttonsGroup {
            row {
                val truststore =
                    radioButton(KafkaMessagesBundle.message("ssl.type.truststore.keystore")).selected(!isCertificate.get())
                val cert = radioButton(KafkaMessagesBundle.message("ssl.type.ca.certificate")).onChanged {
                    if (isCertificate.get() != it.isSelected)
                        isCertificate.set(it.isSelected)
                }.selected(isCertificate.get())

                isCertificate.afterChange {
                    cert.selected(it)
                    truststore.selected(!it)
                }
            }
        }


        rowsRange {
            row(KafkaMessagesBundle.message("kafka.access.key.location")) {
                accessKey = textFieldWithBrowseButton(
                    browseDialogTitle = KafkaMessagesBundle.message("kafka.access.key.location.title"),
                    project
                ).align(AlignX.FILL).onChanged {
                    onUpdate()
                }
            }
            row(KafkaMessagesBundle.message("kafka.access.certificate.location")) {
                accessCertificate = textFieldWithBrowseButton(
                    browseDialogTitle = KafkaMessagesBundle.message("kafka.access.key.certificate.title"),
                    project
                ).align(AlignX.FILL).onChanged {
                    onUpdate()
                }
            }
            row(KafkaMessagesBundle.message("kafka.ca.certificate.location")) {
                caCertificate = textFieldWithBrowseButton(
                    browseDialogTitle = KafkaMessagesBundle.message("kafka.ca.certificate.title"),
                    project
                ).align(AlignX.FILL).onChanged {
                    onUpdate()
                }
            }
        }.visibleIf(isCertificate)

        rowsRange {
            row(KafkaMessagesBundle.message("kafka.truststore.location")) {
                sslTruststoreLocation = textFieldWithBrowseButton(
                    browseDialogTitle = KafkaMessagesBundle.message("kafka.truststore.location.dialog.title"),
                    project
                ).align(AlignX.FILL).onChanged {
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
                        browseDialogTitle = KafkaMessagesBundle.message("kafka.truststore.location.dialog.title"),
                        project
                    ).align(AlignX.FILL).onChanged {
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
        }.visibleIf(isCertificate.not())
    }

    fun applyConfig(config: KafkaSslConfig) {
        sslEnableValidateHostname.selected(config.validateHostName)
        sslTruststoreLocation.text(config.truststoreLocation)
        sslTruststorePassword.text(config.truststorePassword)
        sslUseKeystore.selected(config.useKeyStore)
        sslKeystoreLocation.text(config.keystoreLocation)
        sslKeystorePassword.text(config.keystorePassword)
        sslKeyPassword.text(config.keyPassword)

        caCertificate.text(config.caCertificate)
        accessKey.text(config.accessKey)
        accessCertificate.text(config.accessCertificate)

        isCertificate.set(config.caCertificate.isNotBlank() || config.accessKey.isNotBlank() || config.accessCertificate.isNotBlank())
    }

    @Suppress("DEPRECATION")
    fun getConfig() = KafkaSslConfig(
        validateHostName = sslEnableValidateHostname.component.isSelected,
        truststoreLocation = sslTruststoreLocation.component.text,
        truststorePassword = sslTruststorePassword.component.text,
        useKeyStore = sslUseKeystore.component.isSelected,
        keyPassword = sslKeyPassword.component.text,
        keystoreLocation = sslKeystoreLocation.component.text,
        keystorePassword = sslKeystorePassword.component.text,
        caCertificate = caCertificate.component.text,
        accessCertificate = accessCertificate.component.text,
        accessKey = accessKey.component.text,
    )
}
