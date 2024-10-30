package com.jetbrains.bigdatatools.kafka.util

import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.bigdatatools.common.settings.buildValidator
import com.jetbrains.bigdatatools.common.settings.registerValidator
import com.jetbrains.bigdatatools.common.settings.withNumberOrEmptyValidator
import com.jetbrains.bigdatatools.common.ui.doOnChange
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import javax.swing.JTextField

object KafkaDialogFactory {
  fun showCreateTopicDialog(dataManager: KafkaDataManager) {
    val builder = DialogBuilder()
    builder.addOkAction()
    builder.addCancelAction()
    builder.title(KafkaMessagesBundle.message("action.kafka.CreateTopicAction.text"))
    val nameField = JTextField("NewTopic", 15)
    nameField.selectAll()
    val numPartition = JBTextField("", 15).also {
      it.emptyText.text = KafkaMessagesBundle.message("create.topic.leave.empty.for.default")
      it.withNumberOrEmptyValidator(builder)
    }
    val replicationFactor = JBTextField("", 15).also {
      it.emptyText.text = KafkaMessagesBundle.message("create.topic.leave.empty.for.default")
      it.withNumberOrEmptyValidator(builder)
    }

    val validator = object : InputValidatorEx {
      override fun checkInput(inputString: String) = getErrorText(inputString) == null
      override fun canClose(inputString: String) = getErrorText(inputString) == null
      override fun getErrorText(inputString: String) = when {
        inputString.isBlank() -> MessagesBundle.message("validator.notEmpty")
        inputString.contains(Regex("[ \t\n]")) -> MessagesBundle.message("validator.notSpaces")
        inputString in getTopicNames(dataManager) -> KafkaMessagesBundle.message("kafka.validator.already.exist.topic.name", inputString)
        else -> null
      }
    }

    registerValidator(builder, buildValidator(nameField, { nameField.text }, validator), nameField)
    nameField.doOnChange { builder.okActionEnabled(validator.canClose(nameField.text)) }

    val centralPanel = panel {
      row(KafkaMessagesBundle.message("dialog.create.topic.name")) { cell(nameField).align(AlignX.FILL).resizableColumn() }
      row(KafkaMessagesBundle.message("dialog.create.topic.num.partition")) { cell(numPartition) }
      row(KafkaMessagesBundle.message("dialog.create.topic.replication.factor")) { cell(replicationFactor) }
    }
    builder.centerPanel(centralPanel)

    if (!builder.showAndGet())
      return

    dataManager.createTopic(nameField.text, numPartition.text.toIntOrNull(), replicationFactor.text.toIntOrNull())
  }

  private fun getTopicNames(dataManager: KafkaDataManager): List<String> = dataManager.getTopics().map { it.name }
}

