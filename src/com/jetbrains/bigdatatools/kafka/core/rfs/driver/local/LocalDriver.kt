package com.jetbrains.bigdatatools.kafka.core.rfs.driver.local

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.DriverBase
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.StorageDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.node.LocalRfsDriverTreeNodeBuilder
import com.jetbrains.bigdatatools.kafka.core.rfs.exception.RfsPermissionException
import com.jetbrains.bigdatatools.kafka.core.rfs.icons.RfsIcons
import com.jetbrains.bigdatatools.kafka.core.rfs.settings.local.RfsLocalConnectionData
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsDriverTreeNodeBuilder
import com.jetbrains.bigdatatools.kafka.core.rfs.util.withSlash
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.InvalidPathException
import javax.swing.Icon

class LocalDriver(
  override val connectionData: RfsLocalConnectionData,
  override val project: Project?,
) : DriverBase(), StorageDriver {
  private val connectionId: String = connectionData.innerId
  private val outerName: String = connectionData.name
  private val baseFullPath = FileUtil.toCanonicalPath(extractFullPath(connectionData, project)).withSlash()
  override val treeNodeBuilder: RfsDriverTreeNodeBuilder = LocalRfsDriverTreeNodeBuilder()

  override fun validatePath(path: RfsPath): String? =
    try {
      val fileName = path.name
      if (fileName == "." || fileName == "..") {
        KafkaMessagesBundle.message("local.path.dot.not.allowed")
      }
      else {
        fileFromPath(path).toPath()
        null
      }
    }
    catch (e: InvalidPathException) {
      e.message
    }

  override fun doRefreshConnection(calledByUser: Boolean) {
    //TODO is there really anything to do?
  }

  override fun preprocessPath(path: String): String {
    val withoutSlashes = withoutLeadingSlashes(path)
    val isDirectory = FileUtil.toCanonicalPath(withoutSlashes, '/', false).endsWith("/")
    val res = FileUtil.toCanonicalPath(withoutSlashes, false).removePrefix("/")
    return if (isDirectory) res.withSlash() else res
  }

  @NlsSafe
  override fun doGetHomeUri(): String = File(baseFullPath).absolutePath

  override fun doCheckAvailable() = checkHomeInfo()

  fun fileFromPath(path: RfsPath): File {
    val absolutePath = baseFullPath.withSlash() + path.stringRepresentation()
    return File(absolutePath)
  }

  fun canonicalLocalFsPath(path: RfsPath): String = fileFromPath(path).canonicalPath

  private fun createFileInfo(file: File, rfsPath: RfsPath) = LocalFileInfo(file, rfsPath, this)

  override val presentableName: String = "${outerName.ifEmpty { "LocalDriver" }} ($baseFullPath)"

  override fun doListStatus(path: RfsPath): List<FileInfo>? {
    val file = fileFromPath(path)
    if (!file.exists() || !file.isDirectory)
      return null
    val listFiles = file.listFiles()
    if (listFiles == null) {
      throw RfsPermissionException(LocalFileInfo(file, path, this))
    }
    return listFiles.map { createFileInfo(it, path.child(it.name, it.isDirectory)) }
  }

  override fun doGetFileStatus(path: RfsPath): FileInfo? {
    val file = fileFromPath(path)
    return when {
      file.exists() && file.isFile && path.isDirectory -> null
      file.exists() -> createFileInfo(file, path)
      !file.isFile && Files.isSymbolicLink(file.toPath()) -> createFileInfo(file, path)
      else -> null
    }
  }

  override fun doMkdir(path: RfsPath) {
    fileFromPath(path).mkdirs()
  }

  override fun getExternalId(): String = connectionId

  override fun doCreateWriteStream(rfsPath: RfsPath, overwrite: Boolean, create: Boolean): OutputStream {
    val file = fileFromPath(rfsPath)
    when {
      Files.isSymbolicLink(file.toPath()) -> error(KafkaMessagesBundle.message("cannot.write.to.symlink"))
      file.exists() && !overwrite -> throw FileAlreadyExistsException(file)
      else -> file.parentFile.mkdirs()
    }

    return FileOutputStream(file, false)
  }

  override val icon = driverIcon()

  fun refreshInProject(rfsPath: RfsPath) {
    (getFileStatus(rfsPath, force = true).result as? LocalFileInfo)?.refreshInProject()
  }

  fun getFileInfoFromIoFile(file: File): FileInfo {
    var relPath = file.absolutePath.removePrefix(File(baseFullPath).absolutePath).removePrefix("/")
    if (file.isDirectory)
      relPath = relPath.withSlash()
    val rfsPath = createRfsPath(relPath)
    return doGetFileStatus(rfsPath) ?: error(KafkaMessagesBundle.message("unexpected.error"))
  }

  companion object {
    private fun extractFullPath(connectionData: RfsLocalConnectionData, project: Project?): String {
      val path = connectionData.rootPath ?: ""
      val rootFile = File(if (path.startsWith(".") || path.isEmpty()) "${project?.basePath ?: ""}/$path" else path)
      val basePath = rootFile.canonicalPath.replace("\\", "/")
      return if (basePath.endsWith(File.separatorChar)) basePath else "$basePath/"
    }

    fun driverIcon(): Icon = RfsIcons.LOCAL_ICON
  }
}