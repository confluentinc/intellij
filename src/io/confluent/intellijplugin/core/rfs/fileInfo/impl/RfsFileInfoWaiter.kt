package io.confluent.intellijplugin.core.rfs.fileInfo.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.runInterruptibleMC
import io.confluent.intellijplugin.core.rfs.fileInfo.RfsFileInfoChildren
import io.confluent.intellijplugin.core.util.BdtRefresher
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.time.Duration.Companion.milliseconds

internal class RfsFileInfoWaiter(val fileInfoManager: DriverCacheFileInfoManager) : Disposable {
    private val MAX_UPDATE_TRIES = fileInfoManager.driver.timeout.div(WAIT_UPDATE_DELAY.milliseconds).toInt()

    private val waitingAppearPaths = ConcurrentSkipListMap<RfsPath, Int>()
    private val waitingDisappearPaths = ConcurrentSkipListMap<RfsPath, Int>()

    private val waitingPathsRefresher = BdtRefresher(this, fileInfoManager.driver, WAIT_UPDATE_DELAY, true) {
        runInterruptibleMC {
            processWaitingPaths()
        }
    }

    override fun dispose() {}

    fun waitAppear(rfsPath: RfsPath) {
        log("Start wait appear $rfsPath")

        synchronized(waitingAppearPaths) {
            waitingAppearPaths[rfsPath] = MAX_UPDATE_TRIES
        }

        waitingPathsRefresher.startIfRequired()
    }

    fun waitDisappear(rfsPath: RfsPath) {
        log("Start wait disappear $rfsPath")

        synchronized(waitingDisappearPaths) {
            waitingDisappearPaths[rfsPath] = MAX_UPDATE_TRIES
        }

        waitingPathsRefresher.startIfRequired()
    }

    fun updateWaitingPaths(rfsPath: RfsPath, newChildren: RfsFileInfoChildren?) {
        updateWaitAppear(newChildren?.fileInfos?.map { it.path }?.toSet().orEmpty())
        updateWaitDisappear(rfsPath, newChildren?.fileInfos?.map { it.path }?.toSet().orEmpty())
        if (newChildren?.nextMarker != null) {
            waitingAppearPaths.keys.removeIf { it.parent == rfsPath }
        }
    }

    private fun updateWaitAppear(newPaths: Set<RfsPath>) {
        val appearedWaited = synchronized(waitingAppearPaths) {
            val prevWaited = waitingAppearPaths.keys
            waitingAppearPaths.keys.removeAll(newPaths)
            prevWaited - waitingAppearPaths.keys
        }
        if ((appearedWaited).isNotEmpty())
            log("Waiting paths are appeared: ${appearedWaited}")
    }

    private fun updateWaitDisappear(rfsPath: RfsPath, newPaths: Set<RfsPath>) {
        if (waitingDisappearPaths.isEmpty())
            return
        val candidatesForRemove = synchronized(waitingDisappearPaths) {
            waitingDisappearPaths.keys.filter { it.size >= rfsPath.size + 1 && it.startsWith(rfsPath) }
        }
        val disappearedWaited = candidatesForRemove.filter { candidatePath ->
            val prefixOfCandidate = candidatePath.prefixPath(rfsPath.size + 1)
            prefixOfCandidate !in newPaths
        }.toSet()
        synchronized(waitingDisappearPaths) {
            waitingDisappearPaths.keys.removeAll(disappearedWaited)
        }
        if (disappearedWaited.isNotEmpty())
            log("Waiting paths are disappeared: $disappearedWaited")
    }

    private fun processWaitingPaths() {
        val parentsWaitAppear = synchronized(waitingAppearPaths) {
            waitingAppearPaths.keys.map {
                fileInfoManager.invalidateCachesWaitAppear(it)
                it.parent ?: it
            }
        }

        val parentWaitDisappear = synchronized(waitingDisappearPaths) {
            waitingDisappearPaths.keys.map {
                fileInfoManager.invalidateCachesIfShouldNotExists(it)
                it.parent ?: it
            }
        }

        val pathsForUpdate = (parentsWaitAppear + parentWaitDisappear).distinct()

        if (pathsForUpdate.isEmpty()) {
            waitingPathsRefresher.stopIfRequired()
            return
        }

        log("Force update: $pathsForUpdate")

        pathsForUpdate.forEach {
            fileInfoManager.getChildren(it, force = true)
        }

        val errorWaitAppear = refreshWaitingPathsCount(waitingAppearPaths)
        if (errorWaitAppear.isNotEmpty()) {
            logger.warn("Paths ${errorWaitAppear} is not appeared.")
        }

        val errorWaitDisappear = refreshWaitingPathsCount(waitingDisappearPaths)
        if (errorWaitDisappear.isNotEmpty()) {
            logger.warn("Paths ${errorWaitDisappear} is not disappeared.")
        }

        if (waitingAppearPaths.isEmpty() && waitingDisappearPaths.isEmpty())
            waitingPathsRefresher.stopIfRequired()
    }

    private fun refreshWaitingPathsCount(pathsTries: MutableMap<RfsPath, Int>) = synchronized(pathsTries) {
        pathsTries.keys.toList().mapNotNull {
            pathsTries.computeIfPresent(it) { _, value ->
                if (value > 1)
                    value - 1
                else
                    null
            }
            if (pathsTries[it] == null) {
                pathsTries.remove(it)
                it
            } else {
                null
            }
        }
    }

    private fun log(msg: String) = if (ApplicationManager.getApplication().isUnitTestMode)
        logger.info(msg)
    else
        logger.debug(msg)

    companion object {
        private val logger = Logger.getInstance(this::class.java)
        private const val WAIT_UPDATE_DELAY = 500
    }
}