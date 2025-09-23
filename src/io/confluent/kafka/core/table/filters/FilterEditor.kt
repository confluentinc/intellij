package io.confluent.kafka.core.table.filters

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.components.fields.ExtendableTextField
import io.confluent.kafka.core.ui.SearchExtension
import java.awt.BorderLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class FilterEditor(var modelIndex: Int) : JComponent(), UiDataProvider {

  private val listeners = mutableListOf<FilerEditorChangeListener>()

  private val editor = ExtendableTextField()

  init {
    isOpaque = false

    val leftExtension = SearchExtension()

    editor.apply {
      isOpaque = false
      border = null
      editor.addExtension(leftExtension)
    }

    editor.addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent?) {
        editor.removeExtension(leftExtension)
      }

      override fun focusLost(e: FocusEvent?) {
        if (editor.text.isNullOrBlank()) {
          editor.addExtension(leftExtension)
        }
      }
    })

    editor.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) = changed()
      override fun removeUpdate(e: DocumentEvent?) = changed()
      override fun changedUpdate(e: DocumentEvent?) = changed()

      private fun changed() {
        listeners.forEach { it.onChange() }
      }
    })

    layout = BorderLayout()

    add(editor, BorderLayout.CENTER)
  }

  val text: String?
    get() = editor.text

  fun addListener(listener: FilerEditorChangeListener) {
    listeners += listener
  }

  fun removeListener(listener: FilerEditorChangeListener) {
    listeners -= listener
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink.setNull(CommonDataKeys.EDITOR)
  }
}