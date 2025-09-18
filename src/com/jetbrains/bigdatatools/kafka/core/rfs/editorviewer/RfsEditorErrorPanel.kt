package com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.scale.JBUIScale
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ActivitySource
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.SafeExecutor
import com.jetbrains.bigdatatools.kafka.core.rfs.exception.RfsAuthRequiredError
import com.jetbrains.bigdatatools.kafka.core.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.*

class RfsEditorErrorPanel(e: Throwable, val disposable: Disposable, driver: Driver) : JPanel() {
  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)

    val errorLabel = if (e !is RfsAuthRequiredError) {
      JLabel(KafkaMessagesBundle.message("error.title") + " ", AllIcons.General.Error, JLabel.LEFT)
    }
    else {
      JLabel(KafkaMessagesBundle.message("auth.required.title") + " ", AllIcons.General.Information, JLabel.LEFT)
    }

    val horizontalPanel = JPanel()
    horizontalPanel.layout = BoxLayout(horizontalPanel, BoxLayout.X_AXIS)
    horizontalPanel.add(errorLabel)

    add(Box.createVerticalGlue())
    add(horizontalPanel.apply { alignmentX = CENTER_ALIGNMENT })

    if (e !is RfsAuthRequiredError) {
      val fullMessage = e.toPresentableText()
      if (fullMessage.isNotBlank()) {
        add(Box.createRigidArea(Dimension(0, JBUIScale.scale(5))))
        val label = ActionLink(KafkaMessagesBundle.message("error.message")) {
          val balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("<html>${fullMessage}</html>",
                                                                                         MessageType.ERROR, null)
            .setShowCallout(true)
            .setHideOnAction(true)
            .setHideOnClickOutside(true)

          val balloon = balloonBuilder.createBalloon()
          Disposer.register(disposable, balloon)
          balloon.showInCenterOf(this)
        }.apply {
          alignmentX = CENTER_ALIGNMENT
          border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
        }
        add(label)
      }
    }
    else {
      add(Box.createRigidArea(Dimension(0, JBUIScale.scale(5))))
      val label = ActionLink(KafkaMessagesBundle.message("auth.error.authorize.link")) {
        SafeExecutor.instance.coroutineScope.launch {
          driver.refreshConnection(ActivitySource.ACTION)
        }
      }.apply {
        alignmentX = CENTER_ALIGNMENT
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
      }
      add(label)
    }

    add(Box.createVerticalGlue())
  }
}
