package com.jetbrains.bigdatatools.kafka.core.ui

import com.intellij.ui.EditorCustomization

object EditorUtils {
  fun getViewerEditorCustomizations() = listOf(
    EditorCustomization {
      it.settings.apply {
        isLineNumbersShown = false
        isLineMarkerAreaShown = false
        isFoldingOutlineShown = false
        isRightMarginShown = false
        additionalLinesCount = 5
        additionalColumnsCount = 5
        isAdditionalPageAtBottom = false
        isShowIntentionBulb = false
      }
    })
}