package io.confluent.intellijplugin.core.rfs.driver

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import io.confluent.intellijplugin.core.rfs.copypaste.model.RfsEstimateSizeCountContext
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.SafeResult
import io.confluent.intellijplugin.core.rfs.driver.metainfo.DriverFileMetaInfoProvider
import io.confluent.intellijplugin.core.rfs.fileInfo.DriverFileInfoManager
import io.confluent.intellijplugin.core.rfs.fileInfo.DriverRfsListener
import io.confluent.intellijplugin.core.rfs.fileInfo.RfsFileInfoChildren
import io.confluent.intellijplugin.core.rfs.fileInfo.RfsListMarker
import io.confluent.intellijplugin.core.rfs.search.impl.ListResult
import io.confluent.intellijplugin.core.rfs.search.impl.SearchResult
import io.confluent.intellijplugin.core.rfs.tree.DriverRfsTreeModel
import io.confluent.intellijplugin.core.rfs.tree.node.RfsDriverTreeNodeBuilder
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import kotlinx.coroutines.CoroutineScope
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.Icon
import kotlin.time.Duration

interface Driver : Disposable {
    val isAvailableCopyThroughIoStreams: Boolean
        get() = true

    val safeExecutor: SafeExecutor

    val coroutineScope: CoroutineScope
        get() = safeExecutor.coroutineScope

    val showInfoForRoot: Boolean
        get() = true
    val timeout: Duration
    val isMkDirSupported: Boolean
    val isCreateFileSupported: Boolean
    val isFileStorage: Boolean
    val isRfsViewEditorAvailable: Boolean
        get() = isFileStorage

    /**
     * @return name of driver as User sees it
     */
    val presentableName: String

    val fileInfoManager: DriverFileInfoManager
    val treeNodeBuilder: RfsDriverTreeNodeBuilder

    val connectionData: ConnectionData

    fun getMetaInfoProvider(): DriverFileMetaInfoProvider

    fun createTreeModel(rootPath: RfsPath, project: Project): DriverRfsTreeModel

    fun listStatus(path: RfsPath, force: Boolean = false): SafeResult<RfsFileInfoChildren>

    /**
     * [resultProcessor] can be called more that once if result was not accurate at first attempt
     */
    fun listStatusAsync(
        path: RfsPath,
        force: Boolean = false,
        startFrom: RfsListMarker? = null,
        resultProcessor: Processor<SafeResult<RfsFileInfoChildren>>
    ): CompletableFuture<*>

    fun getFileStatus(path: RfsPath, force: Boolean = false): SafeResult<FileInfo?>

    fun mkdir(path: RfsPath): Result<Unit>

    /**
     * @param overwrite allows to overwrite existing file (should fail without this flag if file exists)
     * @param canCreateNewFile allows to create new file (should fail without this flag if file does not exist)
     * Should never append existing file.
     */
    fun getWriteStream(
        rfsPath: RfsPath,
        overwrite: Boolean = false,
        canCreateNewFile: Boolean = false
    ): SafeResult<OutputStream>

    @NlsSafe
    fun getHomeUri(): String

    fun getExternalId(): String

    //TODO maybe null result should be treated as error as well for getHomeInfo()?
    fun getHomeInfo(): SafeResult<FileInfo?>

    suspend fun refreshConnection(activitySource: ActivitySource): ReadyConnectionStatus

    fun validatePath(path: RfsPath): String?

    fun allowSameNamedFilesAndDirectories(): Boolean

    val icon: Icon

    fun isSearchSupported(): Boolean = false

    fun list(rfsPath: RfsPath, batchId: String? = null, consumer: Processor<ListResult>): Future<*> {
        consumer.process(ListResult(emptyList(), nextBatchId = null, UnsupportedOperationException()))
        return CompletableFuture.completedFuture(null)
    }

    fun searchInConnection(query: String, batchId: String? = null, consumer: Processor<SearchResult>): Future<*> {
        consumer.process(SearchResult.empty)
        return CompletableFuture.completedFuture(null)
    }

    fun ensureOrCreateParentDirectory(fullPath: RfsPath, existingInfo: FileInfo?) {}

    val root: RfsPath

    fun createRfsPath(path: String): RfsPath

    fun addListener(listener: DriverRfsListener)

    fun removeListener(listener: DriverRfsListener)

    fun getCachedFileInfo(rfsPath: RfsPath): SafeResult<FileInfo?>?

    fun initDriverUpdater()

    fun isSupportedForUpload(virtualFile: VirtualFile): Boolean = true
    fun estimateInfoForDirectory(rfsPath: RfsPath, context: RfsEstimateSizeCountContext)
}


interface StorageDriver