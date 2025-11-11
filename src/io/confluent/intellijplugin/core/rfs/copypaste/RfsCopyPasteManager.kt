package io.confluent.intellijplugin.core.rfs.copypaste

//import io.confluent.intellijplugin.core.rfs.localcache.RfsFileContentManager
//import io.confluent.intellijplugin.core.rfs.util.RfsFileUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import io.confluent.intellijplugin.core.rfs.copypaste.dialog.RfsCopyOrMoveDialog
import io.confluent.intellijplugin.core.rfs.copypaste.dialog.RfsRenameDialog
import io.confluent.intellijplugin.core.rfs.copypaste.model.CopyOrMove
import io.confluent.intellijplugin.core.rfs.copypaste.model.RfsCopyMoveContext
import io.confluent.intellijplugin.core.rfs.copypaste.model.TargetInfo
import io.confluent.intellijplugin.core.rfs.copypaste.utils.RfsCopyPasteHelpers
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.ExportFormat
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.local.LocalDriver
import io.confluent.intellijplugin.core.rfs.driver.local.LocalDriverManager
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.util.executeNotOnEdt
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.io.File
import java.io.InputStream

object RfsCopyPasteManager {
    fun saveTextToDiskWithDialog(
        fileSaverDescriptor: FileSaverDescriptor,
        textSupplier: () -> String,
        project: Project
    ) {
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(fileSaverDescriptor, project)
        val targetFile = dialog.save("table")?.file ?: return

        val task = object : Task.Backgroundable(
            project,
            KafkaMessagesBundle.message("rfs.dump.to.file.action.saved.notification.title")
        ) {
            override fun run(indicator: ProgressIndicator) = try {
                targetFile.writeText(textSupplier())
                RfsNotificationUtils.notifySuccess(
                    KafkaMessagesBundle.message("rfs.dump.to.file.action.saved.notification.message", targetFile.path),
                    KafkaMessagesBundle.message("rfs.dump.to.file.action.saved.notification.title")
                )
            } catch (t: Throwable) {
                RfsNotificationUtils.notifyException(
                    t,
                    KafkaMessagesBundle.message("rfs.dump.to.file.action.saving.error.title")
                )
            }
        }

        ProgressManager.getInstance().run(task)
    }

    fun chooseAndUploadFromDisk(
        project: Project,
        targetDriver: Driver,
        targetPath: RfsPath,
        additionalParams: Map<String, Boolean>
    ) {
        val filesToUpload: List<VirtualFile> = chooseFilesForUpload(targetDriver, project) ?: return
        val sourceFileInfos = RfsCopyPasteUtil.getFileInfoFromVirtualFiles(filesToUpload)
        val targetInfo = TargetInfo(targetPath.name, targetPath.parent!!, targetDriver, null)
        RfsCopyPasteUtil.copyMove(
            project, targetInfo, sourceFileInfos,
            move = false,
            additionalParams = additionalParams,
            onFileLoad = { },
            runInBackground = false
        )
    }

    fun chooseFilesForUpload(targetDriver: Driver, project: Project): List<VirtualFile>? {
        var filesToUpload: List<VirtualFile>? = null
        val chooser = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
            .withDescription(KafkaMessagesBundle.message("rfs.action.upload.file.chooser"))
            .withHideIgnored(false)
            .withShowHiddenFiles(true)
            .withFileFilter {
                targetDriver.isSupportedForUpload(it)
            }

        FileChooser.chooseFiles(chooser, project, null) { uploadingFiles ->
            filesToUpload = uploadingFiles
        }
        return filesToUpload?.ifEmpty { null }
    }

    fun uploadFromDisk(
        project: Project,
        uploadingFiles: List<VirtualFile>,
        targetPath: RfsPath,
        targetDriver: Driver,
        runInBackground: Boolean = true,
        onResult: suspend (List<RfsPath>) -> Unit = {}
    ) {
        val sourceFileInfos = RfsCopyPasteUtil.getFileInfoFromVirtualFiles(uploadingFiles)
        copyMoveWithDialog(
            project = project,
            targetDriver = targetDriver,
            targetPath = targetPath,
            sourceFiles = sourceFileInfos,
            allowMove = false,
            onResult = {
                onResult(it)
            },
            runInBackground = runInBackground
        )
    }

    fun chooseTargetAndDownload(project: Project, fileInfos: List<FileInfo>) {
        val filePath: String = chooseDownloadDirectory(project) ?: return
        downloadWithConfirmationDialog(project, filePath, fileInfos)
    }

    private fun chooseDownloadDirectory(project: Project): String? {
        val chooser = FileChooserDescriptorFactory
            .createSingleFolderDescriptor()
            .withDescription(KafkaMessagesBundle.message("rfs.action.download.file.chooser"))

        var filePath: String? = null

        FileChooser.chooseFile(chooser, project, null) { file ->
            filePath = file.path
        }
        return filePath
    }

    private fun downloadWithConfirmationDialog(project: Project, filePath: String, fileInfos: List<FileInfo>) {
        executeNotOnEdt {
            val systemRootDrivers: List<LocalDriver> = LocalDriverManager.instance.getDrivers()
            val targetFileInfoOptions = systemRootDrivers.mapNotNull { driver ->
                val driverPrefix = FileUtil.toCanonicalPath(driver.fileFromPath(driver.root).path)
                if (filePath.startsWith(driverPrefix)) {
                    val rfsPath = driver.createRfsPath(filePath.removePrefix(driverPrefix))
                    driver.getFileStatus(rfsPath)
                } else null
            }
            val targetFileInfo = targetFileInfoOptions.firstNotNullOfOrNull { it.result }
            if (targetFileInfo == null) {
                val error = IllegalStateException(
                    "Unexpected error, target path ${filePath} is not found in root drivers ${systemRootDrivers.joinToString { it.presentableName }}"
                )
                targetFileInfoOptions.mapNotNull { it.exception }.forEach { error.addSuppressed(it) }
                RfsNotificationUtils.notifyException(error)
            } else invokeLater {
                copyMoveWithDialog(
                    project = project,
                    sourceFiles = fileInfos,
                    targetDriver = targetFileInfo.driver,
                    targetPath = targetFileInfo.path,
                    allowMove = false,
                    availableTargetDrivers = systemRootDrivers,
                    onResult = { })
            }
        }
    }

    //fun runSyncDownloadFromRemotePartToBinaryFile(project: Project,
    //                                              driver: Driver,
    //                                              rfsPath: RfsPath, onLoad: () -> Unit) {
    //  val syncTask = object : Task.Backgroundable(project, KafkaMessagesBundle.message("load.file.task")) {
    //    override fun run(indicator: ProgressIndicator) {
    //      try {
    //        val fileInfo = driver.getFileStatus(rfsPath, force = true).resultOrThrow()
    //                       ?: error(KafkaMessagesBundle.message("rfs.error.file.not.found", rfsPath.name))
    //
    //        val freshFile = RfsFileContentManager.getInstance(project).getContentFile(fileInfo, openEmptyFiles = true,
    //                                                                                  forceLoad = true).resultOrThrow()
    //        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(freshFile) ?: return
    //        vFile.refresh(false, true)
    //        RfsFileUtil.setDriverIdAndPath(vFile, fileInfo)
    //        onLoad()
    //        RfsNotificationUtils.notifySuccess(KafkaMessagesBundle.message("file.synced", rfsPath.name),
    //                                           KafkaMessagesBundle.message("rfs.sync"))
    //      }
    //      catch (t: Throwable) {
    //        RfsNotificationUtils.notifyException(t, KafkaMessagesBundle.message("load.file.error"))
    //      }
    //    }
    //  }
    //  ProgressManager.getInstance().run(syncTask)
    //}

    fun runSyncDownloadFromRemoteToTextFile(
        project: Project,
        driver: Driver,
        remotePath: RfsPath,
        targetLocalFile: VirtualFile,
        onSuccess: (String) -> Unit
    ) {
        val syncTask = object : Task.Backgroundable(project, KafkaMessagesBundle.message("load.file.task")) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val fileInfo = driver.getFileStatus(remotePath, force = true).resultOrThrow()
                        ?: error(KafkaMessagesBundle.message("rfs.error.file.not.found", remotePath.name))

                    val readText = readToString(fileInfo, project, indicator, targetLocalFile.path) ?: return
                    runWriteActionAndWait {
                        val document = FileDocumentManager.getInstance().getDocument(targetLocalFile)
                            ?: return@runWriteActionAndWait
                        document.setText(readText)
                        FileDocumentManager.getInstance().saveDocument(document)
                    }

                    onSuccess(readText)
                    RfsNotificationUtils.notifySuccess(
                        KafkaMessagesBundle.message("file.synced", remotePath.name),
                        KafkaMessagesBundle.message("rfs.sync")
                    )
                } catch (t: Throwable) {
                    RfsNotificationUtils.notifyException(t, KafkaMessagesBundle.message("load.file.error"))
                }
            }
        }
        ProgressManager.getInstance().run(syncTask)
    }

    fun downloadFromRemoteToIoFile(
        project: Project,
        indicator: ProgressIndicator,
        driver: Driver,
        remotePath: RfsPath,
        targetFile: File
    ): String {
        val fileInfo = driver.getFileStatus(remotePath, force = true).resultOrThrow()
            ?: error(KafkaMessagesBundle.message("rfs.error.file.not.found", remotePath.name))
        val readStream = fileInfo.readStream(0, null).resultOrThrow()

        val context = RfsCopyMoveContext.createSingle(
            project = project, indicator, remotePath.stringRepresentation(), targetFile.path,
            fileInfo.length,
            isCopy = true
        )

        RfsCopyPasteHelpers.copyStreams(readStream, targetFile.outputStream(), context, fileInfo.length)
        return targetFile.path
    }

    fun runUploadBytesToFileTask(
        project: Project,
        inputStream: InputStream,
        inputLength: Long,
        targetDriver: Driver,
        targetPath: RfsPath,
        inModal: Boolean,
        onSuccess: () -> Unit
    ) {
        fun performUpload(indicator: ProgressIndicator) = try {
            val context = RfsCopyMoveContext.createSingle(
                project = project, indicator, KafkaMessagesBundle.message("file.upload.source.bytes"),
                targetPath.stringRepresentation(), -1, isCopy = true
            )

            val outputStream =
                targetDriver.getWriteStream(targetPath, overwrite = true, canCreateNewFile = true).resultOrThrow()
            RfsCopyPasteHelpers.copyStreams(inputStream, outputStream, context, inputLength)
            onSuccess()
            RfsNotificationUtils.notifySuccess(
                KafkaMessagesBundle.message("file.uploaded", targetPath.name),
                KafkaMessagesBundle.message("rfs.upload")
            )
        } catch (t: Throwable) {
            RfsNotificationUtils.notifyException(t, KafkaMessagesBundle.message("save.file.error"))
        }

        val uploadTask = object : Task.Backgroundable(project, KafkaMessagesBundle.message("save.file.task")) {
            override fun run(indicator: ProgressIndicator) = performUpload(indicator)
        }.toModalIfNeeded(inModal)
        ProgressManager.getInstance().run(uploadTask)
    }

    fun renameWithDialog(
        project: Project,
        sourceFile: FileInfo,
        onResult: suspend (List<RfsPath>) -> Unit = { },
        runInBackground: Boolean = true
    ) = invokeAndWaitIfNeeded {

        val copyMoveDialog = RfsRenameDialog(sourceFile, project)

        val result = copyMoveDialog.showAndGetResult() ?: return@invokeAndWaitIfNeeded

        RfsCopyPasteUtil.copyMove(
            project, result, listOf(sourceFile), move = true, onFileLoad = onResult,
            runInBackground = runInBackground
        )
    }

    fun copyMoveWithDialog(
        project: Project,
        targetDriver: Driver,
        targetPath: RfsPath,
        sourceFiles: List<FileInfo>,
        allowMove: Boolean = true,
        availableTargetDrivers: List<Driver> = listOf(targetDriver),
        onResult: suspend (List<RfsPath>) -> Unit = { },
        runInBackground: Boolean = true
    ) = invokeAndWaitIfNeeded {

        val pasteType = RfsCopyPasteUtil.calculatePasteType(allowMove, sourceFiles, targetDriver)

        val copyMoveDialog = RfsCopyOrMoveDialog(
            pasteType = pasteType,
            sourceFileInfos = sourceFiles,
            targetPath = targetPath,
            targetDriver = targetDriver,
            availableTargetDrivers = availableTargetDrivers,
            project = project
        )

        val result = copyMoveDialog.showAndGetResult() ?: return@invokeAndWaitIfNeeded

        RfsCopyPasteUtil.copyMove(
            project, result, sourceFiles, move = pasteType == CopyOrMove.MOVE, onFileLoad = onResult,
            runInBackground = runInBackground
        )
    }

    fun copySingle(
        project: Project,
        sourceFileInfo: FileInfo,
        targetDriver: Driver,
        targetPath: RfsPath,
        additionalParams: Map<String, Any> = emptyMap(),
        exportFormat: ExportFormat? = null,
        inBackground: Boolean = true
    ): RfsCopyMoveContext {
        val targetInfo = TargetInfo(targetPath.name, targetPath.parent!!, targetDriver, exportFormat)
        return RfsCopyPasteUtil.copyMove(
            project, targetInfo,
            sourceFileInfos = listOf(sourceFileInfo),
            move = false,
            onFileLoad = {},
            runInBackground = inBackground,
            additionalParams = additionalParams
        )
    }

    fun moveSingle(
        project: Project,
        sourceFileInfo: FileInfo,
        targetDriver: Driver,
        targetPath: RfsPath,
        inBackground: Boolean = true
    ): RfsCopyMoveContext {
        val targetInfo = TargetInfo(targetPath.name, targetPath.parent!!, targetDriver, null)
        return RfsCopyPasteUtil.copyMove(
            project, targetInfo, listOf(sourceFileInfo), true,
            onFileLoad = {},
            runInBackground = inBackground
        )
    }

    private fun readToTempFile(
        fileInfo: FileInfo,
        project: Project,
        indicator: ProgressIndicator,
        targetPath: String?
    ): File? {
        val rfsPath = fileInfo.path
        val context = RfsCopyMoveContext.createSingle(
            project = project,
            indicator,
            rfsPath.stringRepresentation(),
            targetPath,
            fileInfo.length,
            isCopy = true
        )

        val readStream = fileInfo.readStream(0, null).resultOrThrow()

        val tempFile = FileUtil.createTempFile("bdt-rfs-download", null)

        RfsCopyPasteHelpers.copyStreams(readStream, tempFile.outputStream(), context, fileInfo.length)
        if (indicator.isCanceled)
            return null

        return tempFile
    }

    fun readToString(
        fileInfo: FileInfo,
        project: Project,
        indicator: ProgressIndicator,
        targetPath: String?
    ): String? {
        val tempFile = readToTempFile(fileInfo, project, indicator, targetPath) ?: return null
        return StringUtil.convertLineSeparators(tempFile.readText())
    }
}

/**
 * Taken from community/platform/testFramework/src/com/intellij/openapi/application/readWriteActionsInTests.kt
 *
 * Executes [action] inside write action.
 * If called from outside the EDT, transfers control to the EDT first, executes write action there and waits for the execution end.
 */
inline fun <T> runWriteActionAndWait(crossinline action: () -> T): T {
    return WriteAction.computeAndWait(ThrowableComputable { action() })
}
