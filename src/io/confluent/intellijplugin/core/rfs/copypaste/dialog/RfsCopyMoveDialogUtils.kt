package io.confluent.intellijplugin.core.rfs.copypaste.dialog

import com.intellij.refactoring.RefactoringBundle
import io.confluent.intellijplugin.core.rfs.copypaste.model.CopyOrMove
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

internal object RfsCopyMoveDialogUtils {
    fun createDialogLabel(sourceFileInfos: List<FileInfo>, pasteType: CopyOrMove): @Nls String {
        val isSingleFileInfo = sourceFileInfos.size == 1
        if (sourceFileInfos.isEmpty())
            return ""
        val firstFileInfo = sourceFileInfos[0]

        return if (isSingleFileInfo) {
            when (Pair(firstFileInfo.isFile, pasteType)) {
                Pair(true, CopyOrMove.MOVE) -> RefactoringBundle.message("move.file.0", firstFileInfo.externalPath)
                Pair(true, CopyOrMove.COPY) -> RefactoringBundle.message(
                    "copy.files.copy.file.0",
                    firstFileInfo.externalPath
                )

                Pair(false, CopyOrMove.MOVE) -> RefactoringBundle.message(
                    "move.directory.0",
                    firstFileInfo.externalPath
                )
                //for a single entry, we always know whether we are going to copy or move
                else -> RefactoringBundle.message("copy.files.copy.directory.0", firstFileInfo.externalPath)
            }
        } else {
            val allAreDirectories = sourceFileInfos.all { it.isDirectory }
            val allAreFiles = sourceFileInfos.all { it.isFile }
            when {
                allAreDirectories && pasteType == CopyOrMove.MOVE -> RefactoringBundle.message("move.specified.directories")
                allAreFiles && pasteType == CopyOrMove.MOVE -> RefactoringBundle.message("move.specified.files")
                allAreDirectories && pasteType == CopyOrMove.COPY -> RefactoringBundle.message("copy.files.copy.specified.directories.label")
                allAreFiles && pasteType == CopyOrMove.COPY -> RefactoringBundle.message("copy.files.copy.specified.files.label")
                pasteType == CopyOrMove.MOVE -> RefactoringBundle.message("move.specified.elements")
                pasteType == CopyOrMove.COPY -> RefactoringBundle.message("copy.files.copy.specified.mixed.label")
                else -> KafkaMessagesBundle.message("copy.files.copy.or.move.specified")
            }
        }
    }
}