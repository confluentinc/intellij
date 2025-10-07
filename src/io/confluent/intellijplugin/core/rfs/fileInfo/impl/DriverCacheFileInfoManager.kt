@file:Suppress("DEPRECATION")

package io.confluent.intellijplugin.core.rfs.fileInfo.impl

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.rfs.driver.*
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.ErrorResult
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.OkResult
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.SafeResult
import io.confluent.intellijplugin.core.rfs.fileInfo.*
import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil
import io.confluent.intellijplugin.core.util.SmartLogger
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.core.util.toPresentableText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Suppress("SameParameterValue")
class DriverCacheFileInfoManager(
    override val driver: DriverBase,
    private val driverFileInfoLoader: DriverFileInfoLoader,
) : DriverFileInfoManager() {
    private val fileInfoWaiter = RfsFileInfoWaiter(this)

    private val autoUpdater = DriverFilesAutoRefresher(this)

    private val loadingFileInfos = ConcurrentHashMap<RfsPath, Unit>()

    // TODO merge with Driver.connectionStatus, review why they are different
    private val driverStatus = MutableStateFlow<DriverConnectionStatus>(ConnectingConnectionStatus)

    init {
        driver.safeExecutor.coroutineScope.launch {
            driver.connectionStatus.collect { status ->
                val oldStatus = driverStatus.getAndUpdate { status }
                if (status != oldStatus) {
                    log(null, "Connection status updated. Status: $status")
                    notify {
                        it.treeUpdated(driver.root)
                    }
                }
            }
        }
    }

    override fun notify(body: (DriverRfsListener) -> Unit) {
        driverFileInfoLoader.notify(body)
    }

    private val cacheRfsNodes = CacheBuilder.newBuilder()
        .maximumSize(nodesCacheSize)
        .build(object : CacheLoader<RfsPath, SafeResult<FileInfo?>>() {
            override fun load(key: RfsPath) = try {
                loadingFileInfos.putIfAbsent(key, Unit)
                driverFileInfoLoader.loadFileInfo(key)
            } finally {
                loadingFileInfos.remove(key)
            }
        })

    private val cacheRfsNodeChildren: Cache<RfsChildrenPartId, SafeResult<RfsFileInfoChildren>> =
        CacheBuilder.newBuilder()
            .maximumSize(childrenCacheSize)
            .build()

    init {
        Disposer.register(driver, this)
        Disposer.register(this, fileInfoWaiter)
    }

    override fun dispose() {}

    override fun startFileRefresh() {
        autoUpdater.startUpdate()
    }

    override suspend fun refreshDriver(activitySource: ActivitySource) {
        cacheRfsNodes.invalidateAll()
        cacheRfsNodeChildren.invalidateAll()

        notify {
            it.nodeUpdated(driver.root)
        }

        invokeDriverRefresh(activitySource)
    }

    override fun driverRefreshFinished(status: DriverConnectionStatus) {
        driverStatus.value = status
        super.driverRefreshFinished(status)
    }

    override fun refreshFiles(path: RfsPath) {
        if (path == driver.root) {
            invalidateAll()
        } else {
            invalidateChildren(path, true, true)
        }

        cacheRfsNodes.getNonEdt(path)
        notify {
            it.treeUpdated(path)
        }
    }

    override fun getCachedChildrenInner(rfsPath: RfsPath): SafeResult<RfsFileInfoChildren>? =
        cacheRfsNodeChildren.getIfPresent(RfsChildrenPartId(rfsPath))

    override fun getCachedFileInfoInner(rfsPath: RfsPath) = cacheRfsNodes.getIfPresent(rfsPath)

    override fun invokeLoadFileInfo(rfsPath: RfsPath) = executeOnPooledThread {
        getFileInfo(rfsPath)
    }

    override fun loadFileInfo(rfsPath: RfsPath, force: Boolean): SafeResult<FileInfo?> {
        if (force) {
            //We need invalidate both children and node info because children can be used in calculation of FileInfo like in GCS
            cacheRfsNodes.invalidate(rfsPath)
            cacheRfsNodeChildren.invalidate(RfsChildrenPartId(rfsPath))
        }

        return getFileInfo(rfsPath)
    }

    private inline fun <T> checkNotEdt(body: (ErrorResult<T>) -> Nothing) {
        try {
            ApplicationManager.getApplication().assertIsNonDispatchThread()
        } catch (e: Throwable) {
            logger.error("Connection-aware method called from EDT", e)
            body(ErrorResult(e))
        }
    }

    private fun <K : Any, V> LoadingCache<K, SafeResult<V>>.getNonEdt(key: K): SafeResult<V> {
        checkNotEdt { return it }
        return this.get(key)
    }

    private fun getFileInfo(rfsPath: RfsPath): SafeResult<FileInfo?> {
        val cached = cacheRfsNodes.getIfPresent(rfsPath)

        if (cached != null) {
            log(rfsPath, "Get file info from cache: ${cached}")
            return cached
        }

        val result = cacheRfsNodes.getNonEdt(rfsPath)
        notify {
            it.fileInfoLoaded(rfsPath, result)
        }
        log(rfsPath, "File info loaded: ${result}")
        return result
    }


    public override fun getDriverConnectionStatus(): DriverConnectionStatus = driverStatus.value

    override fun waitAppear(rfsPath: RfsPath) = fileInfoWaiter.waitAppear(rfsPath)
    override fun waitDisappear(rfsPath: RfsPath) = fileInfoWaiter.waitDisappear(rfsPath)

    override fun clearFileInfoCaches(paths: List<RfsPath>) = cacheRfsNodes.invalidateAll(paths)

    override fun getChildren(pageKey: RfsChildrenPartId, force: Boolean): Flow<SafeResult<RfsFileInfoChildren>> {
        val cached = cacheRfsNodeChildren.getIfPresent(pageKey)
        if (!force && cached != null) {
            logChildrenFromCache(pageKey, cached)
            return flowOf(cached)
        }

        cacheRfsNodeChildren.invalidate(pageKey)
        checkNotEdt { return flowOf(it) }
        val resultIterationFlow = driverFileInfoLoader.loadChildrenFileInfos(pageKey)
        return resultIterationFlow.map { new ->
            cacheRfsNodeChildren.put(pageKey, new)
            notify { listener ->
                listener.childrenLoaded(pageKey.rfsPath, new)
            }

            logChildren(pageKey, new)
            getCachedFileInfoOrReload(pageKey.rfsPath)

            when {
                new is OkResult && new.result.fileInfos != null -> {
                    processSuccessChildrenLoad(pageKey.rfsPath, new.result.fileInfos)
                }

                new is ErrorResult -> {
                    processChildrenLoadError(pageKey.rfsPath)
                }
            }

            updateWaitingPaths(pageKey.rfsPath, new)
            new
        }
    }

    private fun updateWaitingPaths(
        rfsPath: RfsPath,
        newChildren: SafeResult<RfsFileInfoChildren>
    ) {
        fileInfoWaiter.updateWaitingPaths(rfsPath, newChildren.result)
    }

    private fun processChildrenLoadError(rfsPath: RfsPath) {
        invalidateChildren(rfsPath, true, false)
    }

    private fun processSuccessChildrenLoad(
        rfsPath: RfsPath,
        newChildren: List<FileInfo>
    ) {
        invalidateCachedNullParents(rfsPath)

        newChildren.forEach {
            cacheRfsNodes.put(it.path, OkResult(it))
        }

        val newPaths = newChildren.map { it.path }
        invalidateChildren(rfsPath, propagate = true, includeItself = false, excludeStartWith = newPaths)

        invalidateCachesChildrenIfShouldExists(rfsPath)
        invalidateCachesNodesIfShouldExists(rfsPath)
    }

    private fun invalidateCachedNullParents(rfsPath: RfsPath) {
        var curPath: RfsPath? = rfsPath
        while (true) {
            curPath ?: return
            val cachedNode = cacheRfsNodes.getIfPresent(curPath)
            if (cachedNode is ErrorResult || cachedNode is OkResult && cachedNode.result == null) {
                cacheRfsNodes.invalidate(curPath)
            }
            val cachedChildren = cacheRfsNodeChildren.getIfPresent(RfsChildrenPartId(rfsPath))
            if (cachedChildren is ErrorResult) {
                cacheRfsNodeChildren.invalidate(RfsChildrenPartId(rfsPath))
            }
            curPath = curPath.parent
        }
    }

    private fun <K, V> MutableMap<K, V>.removeIf(predicate: (Map.Entry<K, V>) -> Boolean) {
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (predicate(entry)) {
                iterator.remove()
            }
        }
    }

    fun invalidateCachesIfShouldNotExists(rfsPath: RfsPath) {
        cacheRfsNodeChildren.asMap().removeIf { (k, _) ->
            k.rfsPath.startsWith(rfsPath) || rfsPath.startsWith(k.rfsPath)
        }
        cacheRfsNodes.asMap().removeIf { (k, _) ->
            k.startsWith(rfsPath) || rfsPath.startsWith(k)
        }
    }

    fun invalidateCachesWaitAppear(nodePath: RfsPath) {
        for (curPath in generateSequence(nodePath, RfsPath::parent)) {
            val cachedNode = cacheRfsNodes.getIfPresent(curPath)
            if (cachedNode != null && cachedNode.result == null) {
                cacheRfsNodes.invalidate(curPath)
            }
            curPath.parent?.let { parentPath ->
                val cachedParentChildren = cacheRfsNodeChildren.getIfPresent(RfsChildrenPartId(parentPath))
                if (cachedParentChildren != null && cachedParentChildren.result?.fileInfos?.any { it.path == curPath } != true) {
                    cacheRfsNodeChildren.invalidate(RfsChildrenPartId(parentPath))
                } else {
                    return
                }
            }
        }
    }


    /**
     * @return node which update must be started
     */
    private fun invalidateCachesChildrenIfShouldExists(nodePath: RfsPath): RfsPath {
        var curPath = nodePath

        while (true) {
            val parentPath = curPath.parent ?: return curPath
            val cachedChildren = cacheRfsNodeChildren.getIfPresent(RfsChildrenPartId(parentPath))?.result?.fileInfos
            if (cachedChildren == null) {
                curPath = parentPath
                continue
            }

            if (!cachedChildren.map { it.path }.contains(curPath)) {
                cacheRfsNodeChildren.invalidate(RfsChildrenPartId(parentPath))
            } else {
                return curPath
            }

            curPath = parentPath
        }
    }

    private fun invalidateCachesNodesIfShouldExists(nodePath: RfsPath) {
        var curPath = nodePath

        while (true) {
            val parentPath = curPath.parent ?: return
            val cachedFileInfo = cacheRfsNodes.getIfPresent(parentPath)?.result

            if (cachedFileInfo == null) {
                cacheRfsNodes.invalidate(parentPath)
            }

            curPath = parentPath
        }
    }

    private fun invalidateChildren(
        rfsPath: RfsPath, propagate: Boolean,
        includeItself: Boolean,
        excludeStartWith: List<RfsPath> = emptyList()
    ) {
        val childrenNodeChildren = filterChildren(rfsPath, propagate, includeItself, excludeStartWith)
        cacheRfsNodeChildren.invalidateAll(childrenNodeChildren)


        val childrenNodes = filterNodes(rfsPath, propagate, includeItself, excludeStartWith)
        cacheRfsNodes.invalidateAll(childrenNodes)
    }

    private fun filterChildren(
        rfsPath: RfsPath,
        propagate: Boolean,
        includeItself: Boolean,
        excludeStartWith: List<RfsPath>
    ): List<RfsChildrenPartId> {
        val filterChildren = cacheRfsNodeChildren.asMap().keys.filter {
            it.rfsPath.startsWith(rfsPath) &&
                    (includeItself || rfsPath != it.rfsPath) &&
                    (propagate || rfsPath.size + 1 == it.rfsPath.size || rfsPath == it.rfsPath)
        }

        return filterChildren.filterNot { id ->
            excludeStartWith.any { excludedParentPath -> id.rfsPath.startsWith(excludedParentPath) }
        }
    }


    private fun filterNodes(
        rfsPath: RfsPath,
        propagate: Boolean,
        includeItself: Boolean,
        excludeStartWith: List<RfsPath>
    ): List<RfsPath> {
        val filterChildren = cacheRfsNodes.asMap().keys.filter {
            it.startsWith(rfsPath) &&
                    (includeItself || rfsPath != it) &&
                    (propagate || rfsPath.size + 1 == it.size || rfsPath == it)
        }

        return filterChildren.filterNot { path ->
            excludeStartWith.any { excludedParentPath -> path.startsWith(excludedParentPath) }
        }
    }

    fun invalidateAll() {
        autoUpdater.allFilesUpdated()
        cacheRfsNodes.invalidateAll()
        cacheRfsNodeChildren.invalidateAll()
    }


    private fun logChildrenFromCache(pageKey: RfsChildrenPartId, new: SafeResult<RfsFileInfoChildren>) = when (new) {
        is OkResult -> {
            val names = new.result.fileInfos?.map { it.path.name }
            log(pageKey, "Children get from cache. Children: $names")
        }

        is ErrorResult -> {
            log(pageKey, "Children get from cache. Error: ${new.exception.toPresentableText()}")
        }
    }


    private fun logChildren(pageKey: RfsChildrenPartId, new: SafeResult<RfsFileInfoChildren>) = when (new) {
        is OkResult -> {
            val names = new.result.fileInfos?.map { it.path.name }
            log(pageKey, "Children loaded. Children: $names")
        }

        is ErrorResult -> {
            log(pageKey, "Children loaded. Error: ${new.exception.toPresentableText()}")
        }
    }

    companion object {
        private val childrenCacheSize get() = BdIdeRegistryUtil.RFS_CACHE_SIZE.toLong()
        private val nodesCacheSize get() = childrenCacheSize * 10
        private val metaInfoCacheSize get() = childrenCacheSize
        private const val META_INFO_EXPIRE_TIMEOUT_MIN = 10L

        private val logger = SmartLogger(this::class.java)
    }
}

class SuspendingLoadingCacheWrapper<K : Any, V>(val cache: LoadingCache<K, Deferred<V>>) {
    suspend fun get(key: K): V {
        return cache.get(key).await()
    }
}

private fun <K : Any, K1 : K, V1> CacheBuilder<K, Any>.buildDeferred(
    scope: CoroutineScope,
    load: suspend (K1) -> V1
): SuspendingLoadingCacheWrapper<K1, V1> {
    val cache = this.build(object : CacheLoader<K1, Deferred<V1>>() {
        override fun load(key: K1): Deferred<V1> = scope.async { load(key) }
    })
    return SuspendingLoadingCacheWrapper(cache)
}
