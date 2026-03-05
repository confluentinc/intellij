package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateOption
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField

class ScaffoldTemplateOptionsDialog(
    project: Project,
    private val template: ScaffoldV1TemplateListDataInner
) : DialogWrapper(project) {

    internal val fieldComponents = mutableMapOf<String, JComponent>()

    var optionValues: Map<String, String> = emptyMap()
        private set

    private val sortedOptions: List<Pair<String, Scaffoldv1TemplateOption>> =
        (template.spec.options ?: emptyMap()).entries
            .sortedWith(compareBy<Map.Entry<String, Scaffoldv1TemplateOption>> { it.value.order ?: 0 }.thenBy { it.key })
            .map { it.key to it.value }

    init {
        title = KafkaMessagesBundle.message("scaffold.options.dialog.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            for ((key, option) in sortedOptions) {
                row(option.displayName) {
                    val component = when {
                        !option.enum.isNullOrEmpty() -> {
                            comboBox(option.enum).applyToComponent {
                                if (option.initialValue != null) {
                                    selectedItem = option.initialValue
                                }
                            }.component
                        }
                        option.format == "password" -> {
                            cell(JBPasswordField().apply {
                                columns = 20
                                if (option.initialValue != null) {
                                    text = option.initialValue
                                }
                            }).align(AlignX.FILL).resizableColumn().applyToComponent {}.component
                        }
                        else -> {
                            textField().align(AlignX.FILL).resizableColumn().applyToComponent {
                                if (option.initialValue != null) {
                                    text = option.initialValue
                                }
                                if (option.hint != null) {
                                    putClientProperty("StatusVisibleFunction", null)
                                    toolTipText = option.hint
                                    @Suppress("HardCodedStringLiteral")
                                    emptyText.text = option.hint
                                }
                            }.validationOnInput { field ->
                                validateField(key, option, field.text)
                            }.component
                        }
                    }
                    fieldComponents[key] = component
                }
                if (option.description.isNotBlank()) {
                    row("") {
                        comment(option.description)
                    }
                }
            }
        }
    }

    private fun validateField(key: String, option: Scaffoldv1TemplateOption, text: String): ValidationInfo? {
        val minLength = option.minLength
        if (minLength != null && minLength > 0 && text.length < minLength) {
            return ValidationInfo(
                KafkaMessagesBundle.message("scaffold.options.validation.min.length", option.displayName, minLength)
            )
        }

        val pattern = option.pattern
        if (pattern != null && text.isNotEmpty() && !Regex(pattern).matches(text)) {
            val errorMsg = option.patternDescription
                ?: KafkaMessagesBundle.message("scaffold.options.validation.pattern", option.displayName)
            return ValidationInfo(errorMsg)
        }

        return null
    }

    public override fun doOKAction() {
        optionValues = fieldComponents.mapValues { (_, component) ->
            when (component) {
                is JComboBox<*> -> component.selectedItem?.toString() ?: ""
                is JBPasswordField -> String(component.password)
                is JTextField -> component.text ?: ""
                else -> ""
            }
        }
        super.doOKAction()
    }
}
