package io.confluent.intellijplugin.util

import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.core.settings.buildValidator
import io.confluent.intellijplugin.core.settings.registerValidator
import io.confluent.intellijplugin.core.settings.withNumberOrEmptyValidator
import io.confluent.intellijplugin.core.ui.doOnChange
import io.confluent.intellijplugin.data.KafkaDataManager
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
            override fun getErrorText(inputString: String): String? {
                return when {
                    inputString.isBlank() -> KafkaMessagesBundle.message("validator.notEmpty")
                    inputString.contains(NOT_SPACES_PATTERN) -> KafkaMessagesBundle.message("validator.notSpaces")
                    inputString in getTopicNames(dataManager) -> KafkaMessagesBundle.message(
                        "kafka.validator.already.exist.topic.name",
                        inputString
                    )

                    else -> null
                }
            }
        }

        registerValidator(builder, buildValidator(nameField, { nameField.text }, validator), nameField)
        nameField.doOnChange { builder.okActionEnabled(validator.canClose(nameField.text)) }

        val centralPanel = panel {
            row(KafkaMessagesBundle.message("dialog.create.topic.name")) {
                cell(nameField).align(AlignX.FILL).resizableColumn()
            }
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

private val NOT_SPACES_PATTERN = Regex("[ \t\n]")