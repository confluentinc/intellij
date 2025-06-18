package com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.dialog

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.RfsCopyPasteUtil
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.TargetInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.settings.withValidator
import com.jetbrains.bigdatatools.kafka.core.util.executeOnPooledThread
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.JComponent
import kotlin.math.max
import kotlin.properties.Delegates

class RfsRenameDialog(private val sourceFileInfo: FileInfo, project: Project) : DialogWrapper(project, true) {

  private val informationLabel = JBLabel(KafkaMessagesBundle.message("rename.dialog.text", sourceFileInfo.name)).apply {
    preferredSize = JBUI.size(max(preferredSize.width, 300), preferredSize.height)
  }

  private var nameTextField = object : EditorTextField("") {
    init {
      val correctTargetPath = RfsCopyPasteUtil.getCorrectTargetPath(sourceFileInfo,
                                                                    sourceFileInfo.path,
                                                                    sourceFileInfo.driver,
                                                                    exportFormat = calculateExportFormats(
                                                                      sourceFileInfo.driver).firstOrNull())
      text = correctTargetPath.name
    }

    override fun onEditorAdded(editor: Editor) {

      val lastIndexOfSelection = if (sourceFileInfo.isFile) {
        val index = editor.document.text.lastIndexOf(".")
        if (index == -1) {
          editor.document.textLength
        }
        else {
          index
        }
      }
      else {
        editor.document.textLength
      }

      editor.selectionModel.setSelection(0, lastIndexOfSelection)
      editor.caretModel.moveToLogicalPosition(LogicalPosition(0, lastIndexOfSelection))
    }
  }.withValidator(disposable) { text ->
    val result = when {
      text.isBlank() -> KafkaMessagesBundle.message("name.should.not.be.empty")
      text.endsWith("/") && sourceFileInfo.isFile -> KafkaMessagesBundle.message("no.trailing.slash.in.file.name")
      text.removeSuffix("/").contains("/") -> KafkaMessagesBundle.message("no.slash.in.name")
      else -> null
    }
    nameIsValid = result == null
    result
  }

  private var nameConflictBalloon: Balloon? = null

  private var pathIsValid: Boolean by Delegates.observable(true) { _, _, newValue ->
    myOKAction.isEnabled = newValue && nameIsValid
  }
  private var nameIsValid: Boolean by Delegates.observable(true) { _, _, newValue ->
    myOKAction.isEnabled = newValue && pathIsValid
  }

  init {
    title = RefactoringBundle.message("rename.title")

    nameTextField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) = checkConflicts()
    })

    init()
  }

  override fun init() {
    super.init()
    isOKActionEnabled = false
  }

  override fun getPreferredFocusedComponent() = nameTextField

  fun showAndGetResult() = if (showAndGet())
    getDialogResult()
  else
    null

  private fun getDialogResult() = TargetInfo(nameTextField.text,
                                             sourceFileInfo.path.parent!!,
                                             sourceFileInfo.driver,
                                             null)

  override fun createCenterPanel(): JComponent? = null

  override fun createNorthPanel(): JComponent? {
    return FormBuilder.createFormBuilder()
      .addComponent(informationLabel)
      .addVerticalGap(UIUtil.LARGE_VGAP - UIUtil.DEFAULT_VGAP)
      .addLabeledComponent(RefactoringBundle.message("copy.files.new.name.label"), nameTextField).panel
  }

  private fun checkConflicts() {
    nameConflictBalloon?.hide()
    executeOnPooledThread { checkAndReportAlreadyExists() }
  }

  private fun checkAndReportAlreadyExists() {
    val sourceFile = sourceFileInfo

    val curRes = getDialogResult()
    val targetName = curRes.targetName ?: return
    val targetPath = curRes.targetFolder.child(targetName, sourceFile.isDirectory)
    val targetDriver = curRes.targetDriver

    val existsFileInfo = targetDriver.getFileStatus(targetPath).resultOrThrow()
    isOKActionEnabled = existsFileInfo == null
  }

  private fun calculateExportFormats(targetDriver: Driver) = sourceFileInfo.getCopyFormatsFor(targetDriver)
}