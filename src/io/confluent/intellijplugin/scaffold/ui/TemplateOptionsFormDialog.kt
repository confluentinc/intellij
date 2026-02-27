package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateOption
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField

class TemplateOptionsFormDialog(
    private val project: Project,
    private val templateDisplayName: String,
    private val options: Map<String, Scaffoldv1TemplateOption>
) : DialogWrapper(project) {

    private val fieldMap = mutableMapOf<String, JComponent>()
    private val directoryChooser = TextFieldWithBrowseButton()

    init {
        title = KafkaMessagesBundle.message("scaffold.options.dialog.title", templateDisplayName)
        directoryChooser.addBrowseFolderListener(
            KafkaMessagesBundle.message("scaffold.options.directory.chooser.title"),
            null,
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        init()
    }

    private val sortedOptions: List<Pair<String, Scaffoldv1TemplateOption>>
        get() = options.entries
            .sortedWith(compareBy<Map.Entry<String, Scaffoldv1TemplateOption>> { it.value.order ?: 0 }.thenBy { it.key })
            .map { it.key to it.value }

    override fun createCenterPanel(): JComponent {
        return panel {
            for ((key, option) in sortedOptions) {
                row(option.displayName + ":") {
                    val enumValues = option.`enum`
                    if (enumValues != null && enumValues.isNotEmpty()) {
                        val combo = comboBox(enumValues).align(AlignX.FILL).resizableColumn().component
                        option.initialValue?.let { initial ->
                            if (enumValues.contains(initial)) {
                                combo.selectedItem = initial
                            }
                        }
                        fieldMap[key] = combo
                    } else if (option.format == "password") {
                        val passwordField = JPasswordField().apply {
                            columns = 30
                            option.hint?.let { toolTipText = it }
                            option.initialValue?.let { text = it }
                        }
                        cell(passwordField).align(AlignX.FILL).resizableColumn()
                        fieldMap[key] = passwordField
                    } else {
                        val textField = JTextField().apply {
                            columns = 30
                            option.hint?.let { toolTipText = it }
                            option.initialValue?.let { text = it }
                        }
                        cell(textField).align(AlignX.FILL).resizableColumn()
                        fieldMap[key] = textField
                    }
                    option.description.let { comment(it) }
                }
            }
            separator()
            row(KafkaMessagesBundle.message("scaffold.options.directory.label")) {
                cell(directoryChooser).align(AlignX.FILL).resizableColumn()
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        for ((key, option) in sortedOptions) {
            val component = fieldMap[key] ?: continue
            val value = getFieldValue(component)

            val minLength = option.minLength ?: 0
            if (minLength > 0 && value.length < minLength) {
                return ValidationInfo(
                    KafkaMessagesBundle.message("scaffold.options.validation.min.length", option.displayName, minLength),
                    component
                )
            }

            option.pattern?.let { pattern ->
                if (value.isNotEmpty() && !Regex(pattern).containsMatchIn(value)) {
                    val description = option.patternDescription ?: pattern
                    return ValidationInfo(
                        KafkaMessagesBundle.message("scaffold.options.validation.pattern", option.displayName, description),
                        component
                    )
                }
            }
        }

        if (directoryChooser.text.isBlank()) {
            return ValidationInfo(
                KafkaMessagesBundle.message("scaffold.options.validation.directory.required"),
                directoryChooser
            )
        }

        return null
    }

    fun getOptionValues(): Map<String, String> {
        return fieldMap.mapValues { (_, component) -> getFieldValue(component) }
    }

    fun getOutputDirectory(): String = directoryChooser.text

    private fun getFieldValue(component: JComponent): String {
        return when (component) {
            is JPasswordField -> String(component.password)
            is JTextField -> component.text
            is javax.swing.JComboBox<*> -> component.selectedItem?.toString() ?: ""
            else -> ""
        }
    }
}
