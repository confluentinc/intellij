package io.confluent.intellijplugin.core.connection.tunnel.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ssh.config.unified.SshConfigManager
import com.intellij.ssh.ui.unified.SshConfigComboBox
import com.intellij.ssh.ui.unified.SshConfigVisibility
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import io.confluent.intellijplugin.core.connection.tunnel.model.ConnectionSshTunnelData
import io.confluent.intellijplugin.core.connection.tunnel.model.TunnelableData
import io.confluent.intellijplugin.core.connection.tunnel.model.getTunnelDataOrDefault
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.defaultui.HostAndPortChangeListener
import io.confluent.intellijplugin.core.settings.defaultui.HostAndPortProvider
import io.confluent.intellijplugin.core.settings.fields.WrappedComponent
import io.confluent.intellijplugin.core.settings.revalidateComponent
import io.confluent.intellijplugin.core.ui.row
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.swing.JCheckBox

class SshTunnelComponent<T>(
    val project: Project,
    val disposable: Disposable,
    initialConnectionData: T,
    private val hostAndPortProvider: HostAndPortProvider,
    @Nls(capitalization = Nls.Capitalization.Sentence)
    additionalLabel: String = "",
    @Nls(capitalization = Nls.Capitalization.Sentence)
    private val additionalHelper: String = "",
    @NlsContexts.DetailedDescription private val enabledNotification: String = "",
    showLocalPort: Boolean = true
) : WrappedComponent<T>(
    KEY
), HostAndPortChangeListener
        where T : TunnelableData, T : ConnectionData {

    private var initialized: AtomicBoolean = AtomicBoolean(false)

    private var sshVisibility = if (initialConnectionData.isPerProject)
        SshConfigVisibility.Project
    else
        SshConfigVisibility.App

    val isEnabledCheckBox =
        CheckBox(KafkaMessagesBundle.message("settings.tunnel.is.enabled") + additionalLabel).apply {
            addActionListener {
                updateIsEnabled()
            }
        }

    var localPort: Cell<JBTextField>? = null

    var isPerProject: Boolean = initialConnectionData.isPerProject
        set(value) {
            if (field == value) {
                return
            }

            Disposer.dispose(sshComboBox)

            field = value
            sshVisibility = if (value) SshConfigVisibility.Project else SshConfigVisibility.App
            val selectedSshConfig = sshComboBox.selectedSshConfig
            sshComboBox = SshConfigComboBox(project, disposable, sshVisibility)
            sshComboBox.setTextFieldPreferredWidth(15)
            sshComboBox.reload()
            sshComboBox.select(selectedSshConfig)

            installValidatorOnCurrentSshComboBox()
        }

    private var sshComboBox = SshConfigComboBox(project, disposable, sshVisibility).apply {
        setTextFieldPreferredWidth(15)
    }

    private val enabledPanel = panel {
        row {
            cell(isEnabledCheckBox)
            contextHelp(additionalHelper + KafkaMessagesBundle.message("ssh.tunnel.info"))
        }.topGap(TopGap.SMALL)

        indent {
            var row = row(KafkaMessagesBundle.message("settings.tunnel.ssh"), sshComboBox)
            if (showLocalPort) {
                row = row(KafkaMessagesBundle.message("settings.tunnel.ssh.local.port")) {
                    localPort = intTextField()
                    localPort?.columns(COLUMNS_SHORT)
                    localPort?.component?.emptyText?.text =
                        KafkaMessagesBundle.message("settings.tunnel.ssh.local.port.empty.text")
                }
            }

            if (enabledNotification.isNotBlank())
                row.rowComment(enabledNotification)
        }.visibleIf(isEnabledCheckBox.selected)
    }

    init {
        val initialTunnelData = initialConnectionData.getTunnelDataOrDefault()
        val sshConfig = SshConfigManager.getInstance(project).findConfigById(initialTunnelData.configId)
        initialTunnelData.localPort?.toString()?.let { localPort?.text(it) }
        sshComboBox.reload()
        sshComboBox.select(sshConfig)


        isEnabledCheckBox.isSelected = initialTunnelData.isEnabled

        installValidatorOnCurrentSshComboBox()
        updateIsEnabled()
    }

    private fun installValidatorOnCurrentSshComboBox() {
        val sshConfigValidatorSupplier = Supplier {
            val selectedConfig = sshComboBox.selectedSshConfig
            when {
                !isEnabledCheckBox.isSelected -> null
                selectedConfig == null -> ValidationInfo(
                    KafkaMessagesBundle.message("settings.validator.ssh.config.not.selected"),
                    sshComboBox.comboBox
                )

                selectedConfig.isProjectLevel && !isPerProject -> ValidationInfo(
                    KafkaMessagesBundle.message("settings.validator.ssh.config.project.in.global"), sshComboBox.comboBox
                )

                else -> null
            }
        }
        val sshConfigValidator =
            ComponentValidator(sshComboBox).withValidator(sshConfigValidatorSupplier).withFocusValidator(
                sshConfigValidatorSupplier
            ).installOn(sshComboBox.comboBox)
        sshConfigValidator.revalidate()

        sshComboBox.comboBox.addActionListener {
            sshConfigValidator.revalidate()
        }
    }

    override fun getValue(): ConnectionSshTunnelData {
        val configId = sshComboBox.selectedSshConfig?.id ?: ""
        return ConnectionSshTunnelData(
            isEnabledCheckBox.isSelected,
            configId,
            localPort = localPort?.component?.text?.toIntOrNull()
        )
    }

    override fun getComponent() = enabledPanel

    override fun apply(conn: T) {
        isPerProject = conn.isPerProject
        conn.setTunnelData(getValue())
    }

    override fun isModified(conn: T): Boolean {
        val saved = conn.getTunnelDataOrDefault()
        val new = getValue()
        return saved.configId != new.configId || saved.isEnabled != new.isEnabled || saved.localPort != new.localPort
    }

    private fun updateIsEnabled() {
        val isEnabled = isEnabledCheckBox.isSelected
        sshComboBox.isEnabled = isEnabled
        revalidateComboBox()
    }

    private fun revalidateComboBox() = sshComboBox.comboBox.revalidateComponent()

    override fun addIsPerProjectListeners(checkBox: JCheckBox) {
        checkBox.addItemListener {
            isPerProject = checkBox.isSelected
            revalidateComboBox()
        }
    }

    override fun onChange() {
        revalidateComboBox()
    }

    override fun init(conn: T) {
        if (initialized.compareAndSet(false, true)) {
            hostAndPortProvider.registerChangeListener(this)
        }
    }

    override fun getValidators(): List<ComponentValidator> = listOfNotNull(
        ComponentValidator.getInstance(sshComboBox.comboBox).orElse(null)
    )

    companion object {
        val KEY = ModificationKey("SSH_TUNNEL")
    }
}