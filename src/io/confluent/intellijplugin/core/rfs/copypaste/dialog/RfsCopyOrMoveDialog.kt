package io.confluent.intellijplugin.core.rfs.copypaste.dialog

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.whenTextChanged
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.EditorTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import io.confluent.intellijplugin.core.rfs.copypaste.RfsCopyPasteUtil
import io.confluent.intellijplugin.core.rfs.copypaste.model.CopyOrMove
import io.confluent.intellijplugin.core.rfs.copypaste.model.CopyOrMove.*
import io.confluent.intellijplugin.core.rfs.copypaste.model.TargetInfo
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.ExportFormat
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.ui.TextFieldWithRfsBrowseButton
import io.confluent.intellijplugin.core.settings.withValidator
import io.confluent.intellijplugin.core.ui.row
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JEditorPane
import javax.swing.JList
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

class RfsCopyOrMoveDialog(
    private val pasteType: CopyOrMove,
    private val sourceFileInfos: List<FileInfo>,
    targetPath: RfsPath,
    targetDriver: Driver,
    private val availableTargetDrivers: List<Driver> = listOf(targetDriver),
    project: Project,
    private val additionalNameValidator: (String) -> String? = { null }
) {

    val builder = DialogBuilder(project)

    private var exportFormatComboBox = ComboBox(calculateExportFormats(targetDriver))

    private var nameTextField = EditorTextField("").withValidator(builder) { text ->
        val result = additionalNameValidator(text) ?: when {
            text.isBlank() -> KafkaMessagesBundle.message("name.should.not.be.empty")
            text.endsWith("/") && sourceFileInfos.firstOrNull()?.isFile == true -> KafkaMessagesBundle.message("no.trailing.slash.in.file.name")
            text.removeSuffix("/").contains("/") -> KafkaMessagesBundle.message("no.slash.in.name")
            else -> null
        }
        nameIsValid = result == null
        result
    }

    private var driverComboBox = ComboBox(availableTargetDrivers.toTypedArray()).apply {
        selectedItem = targetDriver
        isSwingPopup = false
    }

    private var targetPathTextField = TextFieldWithRfsBrowseButton(
        project,
        targetDriver,
        targetPath.stringRepresentation(),
        availableTargetDrivers,
        showFiles = false,
        disposable = builder
    )
    private lateinit var alreadyExists: Cell<JEditorPane>

    private val showAlreadyExist = AtomicBooleanProperty(false)

    private val okAction = object : AbstractAction(CommonBundle.getOkButtonText()) {
        init {
            putValue(DialogWrapper.DEFAULT_ACTION, true)
        }

        override fun actionPerformed(e: ActionEvent?) {
            builder.dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
        }
    }

    private var pathIsValid: Boolean by Delegates.observable(true) { _, _, newValue ->
        okAction.isEnabled = newValue && nameIsValid
    }

    private var nameIsValid: Boolean by Delegates.observable(true) { _, _, newValue ->
        okAction.isEnabled = newValue && pathIsValid
    }

    init {
        if (sourceFileInfos.size == 1) {
            val correctTargetPath = RfsCopyPasteUtil.getCorrectTargetPath(
                sourceFileInfos.first(),
                targetPath,
                targetDriver,
                exportFormat = calculateExportFormats(targetDriver).firstOrNull()
            )
            nameTextField.text = correctTargetPath.name

            nameTextField.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = checkConflicts()
            })
        }

        targetPathTextField.whenTextChanged {
            checkConflicts()
        }

        driverComboBox.addItemListener {
            targetPathTextField.updateDriver(it.item as Driver)
            updateExportFormats()
        }

        targetPathTextField.onNewDriverSelectDelegate.plusAssign {
            driverComboBox.item = it
            updateExportFormats()
        }

        exportFormatComboBox.renderer = object : SimpleListCellRenderer<ExportFormat>() {
            override fun customize(
                list: JList<out ExportFormat>,
                value: ExportFormat?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                text = value?.displayName ?: "Not Used"
            }
        }
        var prevItem: ExportFormat? = exportFormatComboBox.item

        exportFormatComboBox.addItemListener {
            val newFormat = it.item as ExportFormat
            nameTextField.text = nameTextField.text.removeSuffix(prevItem?.extension ?: "") + newFormat.extension
            prevItem = newFormat
        }
    }

    fun showAndGetResult(): TargetInfo? {

        val centerPanel = createCenterPanel()
        centerPanel.withPreferredWidth(
            max(
                Toolkit.getDefaultToolkit().screenSize.width / 6,
                min(
                    centerPanel.preferredSize.width,
                    Toolkit.getDefaultToolkit().screenSize.width / 4
                )
            )
        )

        centerPanel.withMinimumWidth(1)

        clearAlreadyExistsWarning()
        checkConflicts()

        builder.removeAllActions()
        builder.addAction(okAction)
        builder.addCancelAction()
        builder.setCenterPanel(centerPanel)
        builder.setPreferredFocusComponent(nameTextField)
        builder.setTitle(
            when (pasteType) {
                COPY -> RefactoringBundle.message("copy.files.copy.title")
                MOVE -> RefactoringBundle.message("move.title")
                COPY_OR_MOVE -> "${RefactoringBundle.message("copy.files.copy.title")}/${RefactoringBundle.message("move.title")}"
            }
        )

        return if (builder.showAndGet())
            getDialogResult()
        else
            null
    }

    private fun getDialogResult() = TargetInfo(
        if (sourceFileInfos.size == 1) nameTextField.text else null,
        targetPathTextField.rfsTargetFolder,
        driverComboBox.item as Driver,
        exportFormatComboBox.item
    )

    private fun createCenterPanel() = panel {
        row {
            text(RfsCopyMoveDialogUtils.createDialogLabel(sourceFileInfos, pasteType)).apply {
                component.font = component.font.deriveFont(Font.BOLD)
            }
        }

        if (sourceFileInfos.size == 1) {
            row(RefactoringBundle.message("copy.files.new.name.label"), nameTextField)
        }

        if (exportFormatComboBox.itemCount > 1) {
            row(KafkaMessagesBundle.message("copy.move.label.file.format"), exportFormatComboBox)
        }
        if (availableTargetDrivers.size > 1) {
            row(KafkaMessagesBundle.message("copy.dialog.target.driver"), driverComboBox)
        }

        row(RefactoringBundle.message("copy.files.to.directory.label"), targetPathTextField)

        row {
            icon(AllIcons.General.Warning)
            alreadyExists = comment("")
        }.visibleIf(showAlreadyExist)
    }

    private fun checkConflicts() {
        executeOnPooledThread {
            clearAlreadyExistsWarning()

            if (sourceFileInfos.size != 1)
                return@executeOnPooledThread

            val sourceFile = sourceFileInfos.first()

            val curRes = getDialogResult()
            val targetName = curRes.targetName ?: return@executeOnPooledThread
            val targetPath = curRes.targetFolder.child(targetName, sourceFile.isDirectory)
            val targetDriver = curRes.targetDriver

            val existsFileInfo = targetDriver.getFileStatus(targetPath).resultOrThrow()
            existsFileInfo?.let { showAlreadyExistsWarning(it) }
        }
    }

    private fun showAlreadyExistsWarning(existingFileInfo: FileInfo) {
        val message = if (existingFileInfo.isFile)
            KafkaMessagesBundle.message("file.already.exists", existingFileInfo.name, targetPathTextField.text)
        else
            KafkaMessagesBundle.message("directory.already.exists", existingFileInfo.name, targetPathTextField.text)

        alreadyExists.text(message)
        showAlreadyExist.set(true)
    }

    private fun clearAlreadyExistsWarning() {
        showAlreadyExist.set(false)
    }

    private fun calculateExportFormats(targetDriver: Driver) = sourceFileInfos.flatMap {
        it.getCopyFormatsFor(targetDriver)
    }.distinct().toTypedArray()

    private fun updateExportFormats() {
        val preselectedItem = exportFormatComboBox.item
        val targetDriver = driverComboBox.item
        val targetExportFormats = targetDriver?.let { calculateExportFormats(it) } ?: emptyArray()

        exportFormatComboBox.removeAllItems()
        targetExportFormats.forEach {
            exportFormatComboBox.addItem(it)
        }
        exportFormatComboBox.isVisible = targetExportFormats.isNotEmpty()
        exportFormatComboBox.item = preselectedItem
    }
}