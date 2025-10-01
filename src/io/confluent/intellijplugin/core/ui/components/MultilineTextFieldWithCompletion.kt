package io.confluent.intellijplugin.core.ui.components

import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.LineSeparator
import com.intellij.util.textCompletion.TextCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import java.awt.Dimension

class MultilineTextFieldWithCompletion(project: Project, provider: TextCompletionProvider)
  : TextFieldWithCompletion(project, provider, "", false, true, true) {

  private var columnWidth = 0

  override fun createEditor(): EditorEx {
    val editorEx = super.createEditor()
    editorEx.setBorder(DarculaEditorTextFieldBorder(this, editorEx))
    return editorEx
  }

  // Code from JTextField
  private fun getColumnWidth(): Int {
    if (columnWidth == 0) {
      val metrics = getFontMetrics(font)
      columnWidth = metrics.charWidth('m')
    }
    return columnWidth
  }

  override fun getPreferredSize(): Dimension? {
    val size = super.getPreferredSize()
    size.width = 5 * getColumnWidth() + insets.left + insets.right
    return size
  }

  fun setTextWithoutScroll(text: String?) {
    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        val myDocument = document
        var separator = LINE_SEPARATOR_KEY[myDocument]
        if (separator == null) {
          separator = detectLineSeparators(myDocument, text)
        }
        LINE_SEPARATOR_KEY[myDocument] = separator
        myDocument.replaceString(0, myDocument.textLength, normalize(text, separator))
      }
    }, null, null, UndoConfirmationPolicy.DEFAULT, document)
  }

  companion object {
    //ToDo copy from EditorTextField.
    private val LINE_SEPARATOR_KEY = Key.create<LineSeparator>("ETF_LINE_SEPARATOR")

    private fun normalize(text: String?, separator: LineSeparator?): String {
      return if (text == null || separator == null) (text ?: "") else StringUtil.convertLineSeparators(text)
    }

    private fun detectLineSeparators(document: Document?, text: String?): LineSeparator? {
      if (text == null) return null
      val doNotNormalizeDetect = document is DocumentImpl && document.acceptsSlashR()
      return if (doNotNormalizeDetect) null else StringUtil.detectSeparators(text)
    }
  }
}