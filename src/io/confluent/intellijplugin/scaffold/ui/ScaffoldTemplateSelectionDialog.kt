package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.scaffold.models.TemplateDisplayInfo
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextArea

/**
 * Dialog for browsing and selecting Confluent scaffold templates.
 * Templates are loaded before the dialog is shown.
 */
class ScaffoldTemplateSelectionDialog(
    project: Project?,
    private val templates: List<TemplateDisplayInfo>
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

    init {
        title = KafkaMessagesBundle.message("scaffold.dialog.title")
        updateDetails(templates.firstOrNull())
        println("Dialog: Initialized with ${templates.size} templates")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(KafkaMessagesBundle.message("scaffold.dialog.template")) {
            comboBox(comboModel, CustomListCellRenderer<TemplateDisplayInfo> { it.displayName })
                .align(AlignX.FILL)
                .resizableColumn()
                .applyToComponent {
                    addActionListener {
                        println("Dialog: Selection changed to: ${(selectedItem as? TemplateDisplayInfo)?.displayName}")
                        updateDetails(selectedItem as? TemplateDisplayInfo)
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

    private fun updateDetails(template: TemplateDisplayInfo?) {
        descriptionArea.text = template?.description ?: ""
        languageLabel.text = template?.language ?: ""
        versionLabel.text = template?.version ?: ""
        tagsLabel.text = template?.tags ?: ""
    }
}
