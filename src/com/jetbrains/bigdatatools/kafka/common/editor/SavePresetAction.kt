package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaRunConfig
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConfig
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class SavePresetAction(private val runConfig: KafkaRunConfig, private val configSupplier: () -> StorageConfig)
  : DumbAwareAction(KafkaMessagesBundle.message("action.save.preset"), null, BigdatatoolsKafkaIcons.Bookmark_off) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val hasPreset = runConfig.hasConfig(configSupplier())
    e.presentation.text = KafkaMessagesBundle.message(if (hasPreset) "action.remove.preset" else "action.save.preset")
    e.presentation.icon = if (hasPreset) BigdatatoolsKafkaIcons.Bookmark_on else BigdatatoolsKafkaIcons.Bookmark_off
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    if (runConfig.hasConfig(configSupplier())) {
      runConfig.removeConfig(configSupplier())
    }
    else {
      runConfig.addConfig(configSupplier())
    }
  }
}