package com.jetbrains.bigdatatools.kafka.registry.ui

import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.project.Project
import com.intellij.protobuf.lang.PbFileType
import com.intellij.protobuf.lang.PbLanguage
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextField
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization
import com.jetbrains.bigdatatools.common.ui.doOnChange
import java.awt.BorderLayout
import javax.swing.JPanel

class KafkaRegistrySchemaEditor(private val project: Project, private val onChange: (() -> Unit)? = null) {

  val component = JPanel(BorderLayout())

  private var customSchemaEditor: EditorTextField? = null

  val text: String
    get() = customSchemaEditor?.text ?: ""

  fun setText(text: String, isJson: Boolean) {
    var editor = customSchemaEditor

    editor = if (editor == null) {
      val newEditor = createEditor(project, isJson)
      onChange?.let { newEditor.document.doOnChange(it) }

      component.add(newEditor, BorderLayout.CENTER)
      newEditor
    }
    else {
      if (editor.fileType == PbFileType.INSTANCE && !isJson ||
          editor.fileType == JsonFileType.INSTANCE && isJson) {
        editor
      }
      else {
        val newEditor = createEditor(project, isJson)
        onChange?.let { newEditor.document.doOnChange(it) }
        component.removeAll()
        component.add(newEditor, BorderLayout.CENTER)
        newEditor
      }
    }

    editor.text = text

    customSchemaEditor = editor
  }

  private fun createEditor(project: Project, isJson: Boolean) = EditorTextFieldProvider.getInstance().getEditorField(
    if (isJson) JsonLanguage.INSTANCE else PbLanguage.INSTANCE, project, listOf(
    EditorCustomization {
      it.settings.apply {
        isLineNumbersShown = false
        isLineMarkerAreaShown = false
        isFoldingOutlineShown = false
        isRightMarginShown = false
        isAdditionalPageAtBottom = false
        isShowIntentionBulb = false
      }
    }, MonospaceEditorCustomization.getInstance())).apply {
    autoscrolls = false
    setCaretPosition(0)
  }
}