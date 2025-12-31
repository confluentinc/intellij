package io.confluent.intellijplugin.rfs

import com.intellij.openapi.progress.ProgressIndicator
import io.confluent.intellijplugin.core.rfs.driver.ExportFormat
import io.confluent.intellijplugin.core.rfs.driver.FileInfoBase
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.task.RemoteFsDeleteTask
import io.confluent.intellijplugin.core.rfs.driver.task.RfsCopyMoveTask
import java.io.InputStream

/**
 * File info for Confluent Cloud tree nodes.
 * Represents environment, cluster, or schema registry nodes in the tree.
 */
class ConfluentFileInfo(
    override val driver: ConfluentDriver,
    override val path: RfsPath
) : FileInfoBase() {

    override val externalPath: String = path.stringRepresentation()
    override val length: Long = -1
    override val modificationTime: Long = -1

    override val isCopySupport: Boolean = false
    override val isActionDeleteSupport: Boolean = false
    override val isMoveSupport: Boolean = false

    override fun isMetaInfoSupport(): Boolean = false

    override fun doDeleteAsync() = object : RemoteFsDeleteTask(path) {
        override fun run(indicator: ProgressIndicator) {
            throw UnsupportedOperationException("Deletion not supported for Confluent Cloud resources")
        }
    }

    override fun doRenameAsync(newPath: RfsPath, overwrite: Boolean): RfsCopyMoveTask {
        throw UnsupportedOperationException("Rename not supported for Confluent Cloud resources")
    }

    override fun doGetReadStream(offset: Long, exportFormat: ExportFormat?): InputStream {
        throw UnsupportedOperationException("Read stream not supported for Confluent Cloud resources")
    }
}

