package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.bigdatatools.aws.ui.external.AwsSettingsInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.use
import com.intellij.util.ui.UI
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.util.invokeAndWaitSwing
import com.jetbrains.bigdatatools.kafka.registry.glue.BdtGlueRegistryClient
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

object KafkaUIUtils {
  fun showAndGetGlueRegistry(project: Project?, awsSettingsInfo: AwsSettingsInfo): String? {
    val client = BdtGlueRegistryClient(project, "", awsSettingsInfo)

    @NlsSafe
    val names =  client.use {
      client.connect(true)
      val registries = client.listRegistries()
      registries.map { it.registryName() }.toTypedArray()
    }



    return invokeAndWaitSwing {
      @Suppress("HardCodedStringLiteral")
      val input = ComboBox(names).apply {
        renderer = CustomListCellRenderer<String> { it }
        selectedItem = names.firstOrNull()
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