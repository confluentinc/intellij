package com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.*
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.ErrorResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.OkResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.SafeResult
import com.jetbrains.bigdatatools.kafka.core.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import kotlinx.coroutines.flow.*

abstract class DriverFileInfoLoader(private val project: Project?,
                                    private val driver: Driver
) {
  fun loadFileInfo(rfsPath: RfsPath): SafeResult<FileInfo?> =
    driver.safeExecutor.asyncInterruptibleProgress(
      showProgress = ProgressOptions(KafkaMessagesBundle.message ("rfs.progress.title.loading.fileinfo", rfsPath), project = project,
                                     cancellable = true, showProgress = BdIdeRegistryUtil.RFS_LOAD_SHOW_PROGRESS)) {
      doLoadFileInfo(rfsPath)
    }.blockingGet()

  fun loadChildrenFileInfos(id: RfsChildrenPartId): Flow<SafeResult<RfsFileInfoChildren>> = if (id.rfsPath.isDirectory) {
    doLoadChildrenFileInfos(id)
      .map<RfsFileInfoChildren, SafeResult<RfsFileInfoChildren>> { OkResult(it) }
      .catch { emit(ErrorResult(it)) }
      .onEmpty { emit(ErrorResult(NoSuchElementException("Empty flow"))) }
  }
  else {
    flowOf(OkResult(RfsFileInfoChildren(null)))
  }

  abstract fun doLoadFileInfo(rfsPath: RfsPath): FileInfo?
  abstract fun doLoadChildrenFileInfos(id: RfsChildrenPartId): Flow<RfsFileInfoChildren>
  abstract fun notify(body: (DriverRfsListener) -> Unit)
}