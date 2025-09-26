package io.confluent.intellijplugin.core.rfs.driver.local

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.LocalFileSystem
import io.confluent.intellijplugin.core.rfs.driver.*
import io.confluent.intellijplugin.core.rfs.driver.local.task.LocalFileMoveTask
import io.confluent.intellijplugin.core.rfs.driver.task.RemoteFsDeleteTask
import io.confluent.intellijplugin.core.rfs.driver.task.RemoteFsTask
import io.confluent.intellijplugin.core.rfs.driver.task.RfsCopyMoveTask
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class LocalFileInfo(val file: File, override val path: RfsPath, override val driver: LocalDriver) : FileInfoBase() {
  override val permission: FilePermission = FilePermission(file.canRead(), file.canWrite(), file.canExecute())
  override val isDirectory = file.isDirectory
  override val isFile: Boolean get() = !isDirectory
  override val externalPath: String = "file://${file.absolutePath}"
  override val name: String = file.name
  override val length: Long = if (isDirectory) 0 else file.length()
  override val modificationTime: Long = file.lastModified()

  init {
    require(path.isRoot || name == path.name)
  }

  fun refreshInProject() {
    val findFileByIoFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.absolutePath)
    findFileByIoFile?.parent?.refresh(true, false)
  }

  override fun doRenameAsync(newPath: RfsPath, overwrite: Boolean): RfsCopyMoveTask = LocalFileMoveTask(this, newPath)

  override fun doDeleteAsync(): RemoteFsTask = object : RemoteFsDeleteTask(path) {
    override fun run(indicator: ProgressIndicator) {
      val ioPath = file.toPath()
      Files.walkFileTree(ioPath, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          return if (Files.isSymbolicLink(dir)) {
            Files.delete(dir)
            FileVisitResult.SKIP_SUBTREE
          }
          else {
            FileVisitResult.CONTINUE
          }
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
          Files.delete(dir)
          return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          Files.delete(file)
          return FileVisitResult.CONTINUE
        }
      })
    }
  }

  override fun doGetReadStream(offset: Long, exportFormat: ExportFormat?): InputStream {
    if (isDirectory)
      throw DriverException(KafkaMessagesBundle.message("rfs.error.no.read.stream.for.directory", externalPath))
    val stream = file.inputStream()
    var skipped = 0L
    while (skipped < offset) {
      skipped += stream.skip(offset - skipped)
    }
    return stream
  }
}

