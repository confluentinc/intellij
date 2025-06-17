package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.use
import com.intellij.util.ui.UI
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.kafka.aws.ui.external.AwsSettingsInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.BdtGlueRegistryClient
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KafkaUIUtils {
  suspend fun showAndGetGlueRegistry(project: Project?, awsSettingsInfo: AwsSettingsInfo): String? {
    @NlsSafe
    val names = run {
      val client = BdtGlueRegistryClient(project, "", awsSettingsInfo)
      client.use {
        client.connect(true)
        val registries = client.listRegistries()
        registries.map { it.registryName() }.toTypedArray()
      }
    }


    return withContext(Dispatchers.EDT) {
      @Suppress("HardCodedStringLiteral")
      val input = ComboBox(names).apply {
        renderer = CustomListCellRenderer<String> { it }
        selectedItem = names.firstOrNull()
        isSwingPopup = false
      }


      val builder = DialogBuilder(project)
      builder.addOkAction()
      builder.addCancelAction()
      builder.setTitle(KafkaMessagesBundle.message("settings.glue.registry.title"))
      builder.setNorthPanel(UI.PanelFactory.panel(input).withLabel(KafkaMessagesBundle.message("settings.glue.registry.name")).createPanel())

      if (builder.showAndGet()) {
        input.selectedItem as? String
      }
      else
        null
    }
  }

}