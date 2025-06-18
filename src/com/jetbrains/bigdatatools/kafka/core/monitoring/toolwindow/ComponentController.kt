package com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

interface ComponentController : Disposable {
  fun getComponent(): JComponent
  fun getActions(): List<AnAction> = emptyList()

  companion object {
    fun createInfoPanel(@Nls message: String) = panel {
      row { comment(message).align(Align.CENTER).resizableColumn() }.resizableRow()
    }
  }
}