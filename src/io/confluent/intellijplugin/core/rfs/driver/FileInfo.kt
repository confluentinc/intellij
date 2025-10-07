package io.confluent.intellijplugin.core.rfs.driver

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.SafeResult
import io.confluent.intellijplugin.core.rfs.driver.task.RemoteFsMoveTask
import io.confluent.intellijplugin.core.rfs.driver.task.RemoteFsTask
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.io.InputStream

interface FileInfo {

    /**
     * Unlike [path], contains information about connection.
     * Coincides to URI when possible or uses URI-like format
     */
    val externalPath: String

    /**
     * Identifies the file within the driver.
     */
    val path: RfsPath

    val name: String

    val innerId: String
        @TestOnly get() = driver.getExternalId() + path

    /** In bytes. */
    val length: Long

    val modificationTime: Long

    val permission: FilePermission?

    val driver: Driver

    val isDirectory: Boolean
    val isFile: Boolean
    val isSymbolicLink: Boolean
    val isSynthetic: Boolean

    /**
     * Name, which suitable for the Driver.
     *
     * For instance, Zeppelin has files like 'test' but for LocalDriver it will be 'test.zpln'
     */
    @NlsSafe
    fun nameForDriver(driver: Driver, exportFormat: ExportFormat?): String

    val isActionDeleteSupport: Boolean
        get() = true
    val isCopySupport: Boolean
    val isMoveSupport: Boolean
        get() = true

    fun isMetaInfoSupport(): Boolean = true
    fun isMkDirSupport(): Boolean = driver.isMkDirSupported
    fun isCreateFileSupport(): Boolean = driver.isCreateFileSupported

    @Nls
    fun getOpenViewerError(): String? = null
    fun getCopyFormatsFor(targetDriver: Driver): List<ExportFormat> = emptyList()

    // Methods above return simple values and do not perform any network queries returning cached values instead
    // -------

    fun renameAsync(newPath: RfsPath, overwrite: Boolean = true): SafeResult<RemoteFsMoveTask>

    fun deleteAsync(): SafeResult<RemoteFsTask>

    fun delete(): SafeResult<Unit>

    fun readStream(offset: Long, exportFormat: ExportFormat?): SafeResult<InputStream>
}

data class ExportFormat(val id: String, @NlsContexts.Label val displayName: String, val extension: String)