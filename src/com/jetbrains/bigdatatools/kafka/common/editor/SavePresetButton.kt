package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.kafka.common.models.RunConfig
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaRunConfig
import com.jetbrains.bigdatatools.kafka.util.KafkaIcons
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class SavePresetButton(private val runConfig: KafkaRunConfig, private val configSupplier: () -> RunConfig)
  : DumbAwareAction(KafkaMessagesBundle.message("action.save.preset"), null, KafkaIcons.BOOKMARK_OFF) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val hasPreset = runConfig.hasConfig(configSupplier())
    e.presentation.text = KafkaMessagesBundle.message(if (hasPreset) "action.remove.preset" else "action.save.preset")
    e.presentation.icon = if (hasPreset) KafkaIcons.BOOKMARK_ON else KafkaIcons.BOOKMARK_OFF
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (runConfig.hasConfig(configSupplier())) {
      runConfig.removeConfig(configSupplier())
    }
    else {
      runConfig.addConfig(configSupplier())
    }
  }
}