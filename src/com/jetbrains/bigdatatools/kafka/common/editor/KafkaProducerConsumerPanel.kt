package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane

// Helper to create similar Settings panels for Producer and Consumer.
object KafkaProducerConsumerPanel {
  fun createPanel(mainPanel: DialogPanel, button: JButton, progress : KafkaProducerConsumerProgressComponent) : JPanel {

    mainPanel.border = BorderFactory.createEmptyBorder(0, 10, 0, 0)

    val scroll = JBScrollPane(mainPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      minimumSize = Dimension(mainPanel.minimumSize.width, minimumSize.height)
      border = BorderFactory.createEmptyBorder()
    }

    val bottomWidthGroup = "ButtonAndComment"
    val bottomPanel = panel {
      row {
        cell(button).widthGroup(bottomWidthGroup)
        progress.initCell(this, bottomWidthGroup)
      }
    }.apply {
      border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    return JPanel(BorderLayout()).apply {
      add(scroll, BorderLayout.CENTER)
      add(bottomPanel, BorderLayout.SOUTH)
    }
  }
}