package com.jetbrains.bigdatatools.kafka.core.ui

import com.intellij.ui.EditorTextComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent

fun com.intellij.openapi.editor.Document.doOnChange(runnable: () -> Unit) {
  addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
    override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) = runnable()
  })
}

fun Document.doOnChange(runnable: () -> Unit) {
  addDocumentListener(object : DocumentListener {
    override fun changedUpdate(e: DocumentEvent?) = runnable()
    override fun removeUpdate(e: DocumentEvent?) = runnable()
    override fun insertUpdate(e: DocumentEvent?) = runnable()
  })
}

fun EditorTextComponent.doOnChange(runnable: () -> Unit) {
  document.doOnChange(runnable)
}

/*
 * This method is used for common situation when we have a multiline text field in complex layout,
 * and we want to change this textfield size immediately when the lines number changes.
 */
fun EditorTextComponent.revalidateOnLinesChanged() {
  var lines = document.lineCount
  doOnChange {
    val newLines = document.lineCount
    if (newLines != lines) {
      lines = newLines
      component.apply {
        revalidate()
      }
    }
  }
}

fun JTextComponent.doOnChange(runnable: () -> Unit) {
  document.doOnChange(runnable)
}