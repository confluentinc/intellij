package com.jetbrains.bigdatatools.kafka.core.settings.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.jetbrains.bigdatatools.kafka.core.settings.connections.Property
import com.jetbrains.bigdatatools.kafka.core.ui.components.ConnectionPropertiesEditor
import com.jetbrains.bigdatatools.kafka.core.ui.components.ConnectionProperty
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.security.InvalidParameterException
import javax.swing.JLabel

class BdtPropertyComponent(project: Project,
                           completionVariants: List<ConnectionProperty>,
                           @NlsContexts.Label label: String = KafkaMessagesBundle.message ("settings.properties.label")) {

  val propertyField = ConnectionPropertiesEditor(project, completionVariants).getComponent()

  val label = JLabel(label)

  companion object {
    fun joinProperties(props: Map<String, String?>) =
      props.entries.filter { it.value != null }.joinToString("\n", postfix = "\n") { it.key + "=" + it.value }

    fun parseProperties(inputText: String) = inputText.split('\n').mapNotNull { stringProperty: String ->
      try {
        val trimmed = stringProperty.trim()
        if (trimmed.startsWith("#"))
          return@mapNotNull null
        if (trimmed.isEmpty()) null else parsePropertyItem(trimmed)
      }
      catch (t: Throwable) {
        error(KafkaMessagesBundle.message("invalid.property", stringProperty))
      }
    }

    private fun parsePropertyItem(it: String): Property {
      val delimiterIndex = it.indexOf('=')
      val key = it.substring(0, delimiterIndex)
      val value = it.substring(delimiterIndex + 1)
      if (key.isBlank()) {
        throw InvalidParameterException(KafkaMessagesBundle.message("parse.property.error.is.blank", it))
      }
      return Property(key, value)
    }
  }
}