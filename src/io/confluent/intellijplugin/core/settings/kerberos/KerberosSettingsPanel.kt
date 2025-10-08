package io.confluent.intellijplugin.core.settings.kerberos

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.core.settings.withEmptyOrFileExistValidator
import io.confluent.intellijplugin.core.ui.withTooltip
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JCheckBox
import javax.swing.JComponent
import kotlin.math.max

class KerberosSettingsPanel(project: Project) : javax.swing.JPanel(), ConfigurableUi<BdtKerberosManager>, Disposable {
    private val krb5Config = BrowsableEditableTextField(project).apply {
        emptyText.text = KafkaMessagesBundle.message("kerberos.settings.krb5.config.empty")
    }

    private val krb5Debug = JCheckBox(KafkaMessagesBundle.message("kerberos.settings.checkbox.krb5.debug.logging"))
    private val jgssDebug = JCheckBox(KafkaMessagesBundle.message("kerberos.settings.checkbox.jgss.debug.logging"))

    private lateinit var dialogPanel: DialogPanel

    init {
        krb5Config.withEmptyOrFileExistValidator(this, canBeEmpty = true)
        preferredSize = JBUI.size(max(preferredSize.width, 400), preferredSize.height)
    }

    override fun reset(settings: BdtKerberosManager) {
        krb5Debug.isSelected = settings.krb5Debug
        jgssDebug.isSelected = settings.jgssDebug

        krb5Config.text = settings.krb5Config
    }

    override fun isModified(settings: BdtKerberosManager): Boolean {
        return krb5Debug.isSelected != settings.krb5Debug ||
                jgssDebug.isSelected != settings.jgssDebug ||
                krb5Config.text != settings.krb5Config
    }

    override fun apply(settings: BdtKerberosManager) {
        settings.krb5Debug = krb5Debug.isSelected
        settings.jgssDebug = jgssDebug.isSelected
        settings.krb5Config = krb5Config.text

        // Notify all listeners (looks like hack, but works until this is the only place where we can change kerberos settings)
        BdtKerberosManager.instance.settingsChanged()
    }

    override fun getComponent(): JComponent {
        krb5Debug.withTooltip(KafkaMessagesBundle.message("kerberos.settings.debug.krb5.tooltip"))
        jgssDebug.withTooltip(KafkaMessagesBundle.message("kerberos.settings.debug.jgss.tooltip"))

        dialogPanel = panel {
            row(KafkaMessagesBundle.message("kerberos.settings.krb5.config.label")) {
                cell(krb5Config).align(AlignX.FILL).resizableColumn()
                cell(ContextHelpLabel.create(KafkaMessagesBundle.message("kerberos.settings.krb5.config.tooltip")))
            }.bottomGap(BottomGap.NONE)

            row {
                label(KafkaMessagesBundle.message("kerberos.settings.change.krb5.conf.comment")).component.icon =
                    AllIcons.General.Warning
            }.bottomGap(BottomGap.MEDIUM)

            row { cell(krb5Debug) }
            row { cell(jgssDebug) }
        }
        return dialogPanel
    }

    override fun dispose() = Unit
}