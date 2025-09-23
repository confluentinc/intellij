package io.confluent.kafka.core.rfs.copypaste.providers

import com.intellij.ide.CopyPasteManagerEx
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.copypaste.RfsCopyPasteManager
import io.confluent.kafka.core.rfs.copypaste.model.TransferableDescriptor
import io.confluent.kafka.core.rfs.copypaste.model.rfsDataFlavor
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.local.LocalDriverManager
import java.awt.datatransfer.DataFlavor
import java.io.File

object RfsPasteProviderUtils {
  fun processRfsPaste(project: Project,
                      destInfo: FileInfo,
                      allowMove: Boolean = false): Boolean {
    val pasteInfos = getPastingFileInfos() ?: return false

    RfsCopyPasteManager.copyMoveWithDialog(project = project,
                                           targetPath = destInfo.path,
                                           targetDriver = destInfo.driver,
                                           sourceFiles = pasteInfos,
                                           allowMove = allowMove,
                                           onResult = {})
    return true
  }


  internal fun getTransferableDescriptor() = CopyPasteManagerEx.getInstanceEx().getContents<TransferableDescriptor>(rfsDataFlavor)

  internal fun getPastingFileInfos(): List<FileInfo>? =
    getPastingFromRfsFlavor() ?: getFileInfoFromString()

  private fun getPastingFromRfsFlavor(): List<FileInfo>? {
    val transferable = getTransferableDescriptor() ?: return null

    val pastingNodes = transferable.data
    return pastingNodes.mapNotNull { it.fileInfo }
  }

  private fun getFileInfoFromString(): List<FileInfo>? {
    val files = CopyPasteManagerEx.getInstanceEx().getContents<List<File>>(DataFlavor.javaFileListFlavor) ?: return null
    return files.filter { it.parentFile != null }.map { LocalDriverManager.instance.createFileInfo(it) }.ifEmpty { null }
  }
}