package com.jetbrains.bigdatatools.kafka.registry.ui

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextField
import com.intellij.ui.EditorTextFieldProvider
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.common.ui.DarculaTextAreaBorder
import com.jetbrains.bigdatatools.common.ui.doOnChange
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class KafkaRegistrySchemaEditor(private val project: Project,
                                private val parentDisposable: Disposable,
                                private val isEditable: Boolean = true,
                                private val lineBorder: Boolean = false,
                                private val onChange: (() -> Unit)? = null) {

  var customSchemaEditor = createEditor(project, PlainTextLanguage.INSTANCE)

  val component = JPanel(BorderLayout()).apply {
    add(customSchemaEditor, BorderLayout.CENTER)
  }

  val text: String
    get() = customSchemaEditor.text

  fun setLanguage(language: Language) {
    updateEditor(language, preserveText = true)
  }

  fun setText(text: String, language: Language) {
    val editor = updateEditor(language, preserveText = false)

    editor.document.setReadOnly(false)
    editor.text = text
    editor.document.setReadOnly(!isEditable)
  }

  private fun updateEditor(language: Language, preserveText: Boolean): EditorTextField {
    var editor = customSchemaEditor

    editor = if (editor.fileType == language.associatedFileType) {
      editor
    }
    else {
      val newEditor = createEditor(project, language)
      onChange?.let { newEditor.document.doOnChange(it) }
      component.removeAll()
      component.add(newEditor, BorderLayout.CENTER)

      if (preserveText) {
        newEditor.document.setReadOnly(false)
        newEditor.text = editor.text
        newEditor.document.setReadOnly(!isEditable)
      }

      newEditor
    }

    customSchemaEditor = editor
    return editor
  }

  private fun createEditor(project: Project, language: Language): EditorTextField {
    val editor = EditorTextFieldProvider.getInstance().getEditorField(language, project, listOf(
      EditorCustomization {
        it.settings.apply {
          isLineNumbersShown = false
          isLineMarkerAreaShown = false
          isFoldingOutlineShown = false
          isRightMarginShown = false
          isAdditionalPageAtBottom = false
          isShowIntentionBulb = false
        }
      }, object : EditorCustomization {
      override fun customize(editor: EditorEx) {
        editor.scrollPane.border = BorderFactory.createEmptyBorder()
      }
    })).apply {
      if (lineBorder) {
        border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
      }
      autoscrolls = false
      setCaretPosition(0)
      document.setReadOnly(!isEditable)
      setDisposedWith(parentDisposable)
    }
    editor.withValidator(parentDisposable) { s: String ->
      if (s.isEmpty())
        MessagesBundle.message("validator.notEmpty")
      else
        null
    }
    return editor
  }
}