package com.jetbrains.bigdatatools.kafka.util

import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.InputValidatorEx
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.settings.buildValidator
import com.jetbrains.bigdatatools.settings.registerValidator
import com.jetbrains.bigdatatools.ui.MigPanel
import com.jetbrains.bigdatatools.ui.doOnChange
import com.jetbrains.bigdatatools.util.MessagesBundle
import javax.swing.JTextField

object KafkaDialogFactory {

  fun showCreateTopicDialog(dataManager: KafkaDataManager) {
    val builder = DialogBuilder()
    builder.addOkAction()
    builder.addCancelAction()
    builder.title(KafkaMessagesBundle.message("action.create.topic"))
    val nameField = JTextField(KafkaMessagesBundle.message("dialog.create.topic.default.name"), 15)
    nameField.selectAll()
    val numPartition = JTextField(6)

    val validator = object : InputValidatorEx {
      override fun checkInput(inputString: String) = getErrorText(inputString) == null
      override fun canClose(inputString: String) = getErrorText(inputString) == null
      override fun getErrorText(inputString: String) = if (inputString.isBlank()) MessagesBundle.message("validator.notEmpty") else null
    }

    registerValidator(builder, buildValidator(nameField, { nameField.text }, validator), nameField)
    nameField.doOnChange { builder.okActionEnabled(validator.canClose(nameField.text)) }

    val centralPanel = MigPanel().apply {
      row(KafkaMessagesBundle.message("dialog.create.topic.name"), nameField)
      shortRow(KafkaMessagesBundle.message("dialog.create.topic.num.partition"), numPartition)
    }
    builder.centerPanel(centralPanel)

    if (!builder.showAndGet())
      return

    dataManager.createTopic(nameField.text, numPartition.text.toIntOrNull())
  }
}