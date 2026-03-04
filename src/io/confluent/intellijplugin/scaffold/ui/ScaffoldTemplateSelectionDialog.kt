package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent

class ScaffoldTemplateSelectionDialog(
    project: Project,
    private val templates: List<ScaffoldV1TemplateListDataInner>
) : DialogWrapper(project) {

    internal val comboModel = DefaultComboBoxModel(templates.toTypedArray())
    internal val descriptionArea = JBTextArea(4, 20).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = null
        font = JBLabel().font
        isFocusable = false
    }
    internal val languageLabel = JBLabel()
    internal val versionLabel = JBLabel()
    internal val tagsLabel = JBLabel()

    private lateinit var templateComboBox: JComboBox<ScaffoldV1TemplateListDataInner>

    var selectedTemplate: ScaffoldV1TemplateListDataInner? = null
        private set

    init {
        title = KafkaMessagesBundle.message("scaffold.dialog.title")
        init()
        updateDetails(templates.first())
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(KafkaMessagesBundle.message("scaffold.dialog.template")) {
                comboBox(comboModel, CustomListCellRenderer<ScaffoldV1TemplateListDataInner> {
                    it.spec.displayName ?: it.spec.name ?: KafkaMessagesBundle.message("scaffold.dialog.template.unknown")
                }).align(AlignX.FILL).resizableColumn().applyToComponent {
                    templateComboBox = this
                    addActionListener {
                        val selected = comboModel.selectedItem as? ScaffoldV1TemplateListDataInner
                        if (selected != null) {
                            updateDetails(selected)
                        }
                    }
                }
            }
            row(KafkaMessagesBundle.message("scaffold.dialog.template.description")) {
                cell(descriptionArea).align(AlignX.FILL).resizableColumn()
            }
            row(KafkaMessagesBundle.message("scaffold.dialog.template.language")) {
                cell(languageLabel)
            }
            row(KafkaMessagesBundle.message("scaffold.dialog.template.version")) {
                cell(versionLabel)
            }
            row(KafkaMessagesBundle.message("scaffold.dialog.template.tags")) {
                cell(tagsLabel)
            }
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = templateComboBox

    override fun getDimensionServiceKey(): String = "kafka.scaffold.template.selection.dialog"

    private fun updateDetails(template: ScaffoldV1TemplateListDataInner) {
        val spec = template.spec
        descriptionArea.text = spec.description ?: ""
        languageLabel.text = spec.language ?: ""
        versionLabel.text = spec.version ?: ""
        tagsLabel.text = spec.tags?.joinToString(", ") ?: ""
    }

    override fun doOKAction() {
        selectedTemplate = comboModel.selectedItem as? ScaffoldV1TemplateListDataInner
        super.doOKAction()
    }
}
