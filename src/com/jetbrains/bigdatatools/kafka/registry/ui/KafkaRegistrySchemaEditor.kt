package com.jetbrains.bigdatatools.kafka.registry.ui

import com.intellij.json.JsonLanguage
import com.intellij.openapi.project.Project
import com.intellij.protobuf.lang.PbLanguage
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization

object KafkaRegistrySchemaEditor {
  fun createEditor(project: Project, isJson: Boolean) = EditorTextFieldProvider.getInstance().getEditorField(
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