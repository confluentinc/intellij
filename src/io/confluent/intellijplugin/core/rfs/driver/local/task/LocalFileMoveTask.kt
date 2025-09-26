package io.confluent.intellijplugin.core.rfs.driver.local.task

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import io.confluent.intellijplugin.core.rfs.copypaste.model.RfsCopyMoveContext
import io.confluent.intellijplugin.core.rfs.copypaste.utils.RfsCopyPasteHelpers
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.local.LocalFileInfo
import io.confluent.intellijplugin.core.rfs.driver.task.RemoteFsMoveTask
import io.confluent.intellijplugin.core.util.exists
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.fileSize
import kotlin.io.path.pathString

class LocalFileMoveTask(private val sourceFileInfo: LocalFileInfo,
                        private val targetPath: RfsPath
) : RemoteFsMoveTask(sourceFileInfo, targetPath) {
  override fun run(context: RfsCopyMoveContext) {
    val driver = sourceFileInfo.driver
    val targetFile = driver.fileFromPath(targetPath)

    targetFile.parentFile.mkdirs()
    val sourceIoPath = sourceFileInfo.file.toPath()
    val targetIoPath = targetFile.toPath()

    Files.walkFileTree(sourceIoPath, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {

        val relativePath = sourceIoPath.relativize(dir)
        val currentTarget = targetIoPath.resolve(relativePath)

        return if (Files.isSymbolicLink(dir)) {
          moveFile(dir, context, currentTarget)
          FileVisitResult.SKIP_SUBTREE
        }
        else {
          Files.createDirectories(currentTarget)
          FileVisitResult.CONTINUE
        }
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val relativePath = sourceIoPath.relativize(file)
        val currentTarget = targetIoPath.resolve(relativePath)

        context.startProceedFile(relativePath.pathString, currentTarget.pathString, file.fileSize())

        moveFile(file, context, currentTarget)
        return FileVisitResult.CONTINUE
      }

      override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
        Files.delete(dir)
        return FileVisitResult.CONTINUE
      }
    })

    val canonToPath = FileUtil.toSystemIndependentName(targetFile.canonicalPath)
    val url = VirtualFileManager.constructUrl(LocalFileSystem.getInstance().protocol, canonToPath)
    VirtualFileManager.getInstance().refreshAndFindFileByUrl(url)
  }

  private fun moveFile(source: Path,
                       context: RfsCopyMoveContext,
                       target: Path) {
    val shouldContinue = RfsCopyPasteHelpers.resolveOverwrite(target.pathString, context) {
      target.exists()
    }
    if (!shouldContinue)
      return

    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS)
    Files.delete(source)
  }
}