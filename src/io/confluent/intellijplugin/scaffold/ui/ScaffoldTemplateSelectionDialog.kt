package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextArea

class ScaffoldTemplateSelectionDialog(
    project: Project,
    private val templates: List<ScaffoldV1TemplateListDataInner>
) : DialogWrapper(project) {

    private val comboModel = DefaultComboBoxModel(templates.toTypedArray())
    private val descriptionArea = JTextArea(4, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val languageLabel = JLabel()
    private val versionLabel = JLabel()
    private val tagsLabel = JLabel()

    var selectedTemplate: ScaffoldV1TemplateListDataInner? = null
        private set

    init {
        title = KafkaMessagesBundle.message("scaffold.dialog.title")
        init()
        if (templates.isNotEmpty()) {
            updateDetails(templates.first())
        }
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(KafkaMessagesBundle.message("scaffold.dialog.template")) {
                comboBox(comboModel, CustomListCellRenderer<ScaffoldV1TemplateListDataInner> {
                    it.spec.displayName ?: it.spec.name ?: "Unknown Template"
                }).align(AlignX.FILL).resizableColumn().applyToComponent {
                    addActionListener {
                        @Suppress("UNCHECKED_CAST")
                        val selected = (it.source as? javax.swing.JComboBox<ScaffoldV1TemplateListDataInner>)?.selectedItem
                                as? ScaffoldV1TemplateListDataInner
                        if (selected != null) {
                            updateDetails(selected)
                        }
                    }
                }
            }
            row(KafkaMessagesBundle.message("scaffold.dialog.template.description")) {
                scrollCell(descriptionArea).align(AlignX.FILL).resizableColumn()
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

    private fun updateDetails(template: ScaffoldV1TemplateListDataInner) {
        val spec = template.spec
        descriptionArea.text = spec.description ?: ""
        languageLabel.text = spec.language ?: ""
        versionLabel.text = spec.version ?: ""
        tagsLabel.text = spec.tags?.joinToString(", ") ?: ""
    }

    override fun doOKAction() {
        @Suppress("UNCHECKED_CAST")
        selectedTemplate = comboModel.selectedItem as? ScaffoldV1TemplateListDataInner
        super.doOKAction()
    }
}
