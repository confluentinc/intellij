package io.confluent.kafka.core.rfs.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import io.confluent.kafka.core.settings.buildValidator
import io.confluent.kafka.core.settings.registerValidator
import io.confluent.kafka.core.ui.doOnChange
import org.jetbrains.annotations.Nls
import javax.swing.JTextField
import kotlin.math.max

object BdtMessages {
  fun showInputDialogWithDescription(project: Project?,
                                     @Nls(capitalization = Nls.Capitalization.Title) title: String,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) label: String,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) description: String?,
                                     initialValue: String = "",
                                     validation: (String) -> @Nls String? = { null }): String? {
    val validator = object : InputValidatorEx {
      override fun checkInput(inputString: String) = getErrorText(inputString) == null

      override fun canClose(inputString: String) = getErrorText(inputString) == null

      override fun getErrorText(inputString: String) = validation(inputString)
    }
    return showInputDialogWithDescription(project, title, label, description, initialValue, validator)
  }

  fun showInputDialogWithDescription(project: Project?,
                                     @Nls(capitalization = Nls.Capitalization.Title) title: String,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) label: String,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) description: String?,
                                     initialValue: String = "",
                                     validator: InputValidatorEx? = null): String? {

    val builder = DialogBuilder(project)
    val input = JTextField(max(initialValue.length, 15))

    builder.addOkAction()
    builder.addCancelAction()
    builder.setTitle(title)

    builder.setNorthPanel(UI.PanelFactory.panel(input).withLabel(label).createPanel())
    if (description != null) {
      val jbLabel = JBLabel(description).apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = JBColor.GRAY
      }
      builder.setCenterPanel(UI.PanelFactory.panel(jbLabel).withLabel("").createPanel())
    }
    builder.setPreferredFocusComponent(input)

    if (validator != null) {
      builder.setOkActionEnabled(false)
      registerValidator(builder, buildValidator(input, { input.text }, validator), input)
      input.doOnChange { builder.okActionEnabled(validator.canClose(input.text)) }
    }

    input.text = initialValue
    input.selectAll()

    return if (builder.showAndGet()) input.text else null
  }
}