package io.confluent.intellijplugin.core.rfs.driver.task

import io.confluent.intellijplugin.core.rfs.copypaste.model.RfsCopyMoveContext
import io.confluent.intellijplugin.core.rfs.copypaste.utils.RfsCopyPasteHelpers
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.ExportFormat
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.local.LocalDriver
import io.confluent.intellijplugin.core.rfs.driver.local.LocalFileInfo
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.nio.file.Files


open class ReadStreamToWriteStreamFsCopyTask(
    fromInfo: FileInfo,
    toPath: RfsPath,
    toDriver: Driver,
    skipIfCopyChildIsNotSupported: Boolean = false,
    val exportFormat: ExportFormat? = null,
    val move: Boolean = false,
    additionalParams: Map<String, Any>
) : BaseRfsCopyMoveTask(
    fromInfo,
    toPath,
    toDriver,
    skipIfCopyChildIsNotSupported,
    additionalParams = additionalParams
) {
    override fun moveUserInfoMessage() = if (move)
        KafkaMessagesBundle.message("move.notice.message.streams")
    else
        null


    override fun copyFile(
        context: RfsCopyMoveContext,
        fromInfo: FileInfo,
        toPath: RfsPath,
        toDriver: Driver
    ) {
        context.startProceedFile(fromInfo.path.stringRepresentation(), toPath.stringRepresentation(), fromInfo.length)

        if (!RfsCopyPasteHelpers.resolveOverwriteFileInfo(toDriver, toPath, context))
            return

        RfsCopyPasteHelpers.copyStreams(fromInfo, toDriver, toPath, context)

        if (move && !context.isCanceled)
            fromInfo.delete()

        (toDriver as? LocalDriver)?.refreshInProject(toPath)
    }

    override fun copyDir(
        context: RfsCopyMoveContext,
        fromInfo: FileInfo,
        toPath: RfsPath,
        toDriver: Driver
    ) {
        if (toDriver is LocalDriver) {
            val existsFile = toDriver.getFileStatus(toPath, force = true).resultOrThrow() as? LocalFileInfo
            val ioFile = existsFile?.file
            if (ioFile != null && Files.isSymbolicLink(ioFile.toPath())) {
                Files.delete(ioFile.toPath())
            }
        }

        super.copyDir(context, fromInfo, toPath, toDriver)

        if (move && !context.isCanceled)
            fromInfo.delete()
    }
}