package io.confluent.intellijplugin.core.rfs.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.io.IOUtil

object RfsFileUtil {
    fun getDriverId(file: VirtualFile): String? = readKeyOrAttribute(file, DRIVER_ID_KEY, DRIVER_ID_ATTRIBUTE)

    fun getPath(file: VirtualFile): String? = readKeyOrAttribute(file, RFS_PATH_KEY, RFS_PATH_ATTRIBUTE)

    abstract class Result

    private fun writeKeyOrAttribute(file: VirtualFile, value: String, key: Key<String>, attribute: FileAttribute) {
        file.putUserData(key, value)
        setPersistentAttribute(attribute, file, value)
    }

    private fun readKeyOrAttribute(file: VirtualFile, key: Key<String>, attribute: FileAttribute): String? {
        val cachedValue = file.getUserData(key)
        if (cachedValue != null)
            return cachedValue
        val value = readFromAttribute(attribute, file)
        value ?: return null
        file.putUserData(key, value)
        return value
    }

    fun readFromAttribute(remoteConfigIdAttribute: FileAttribute, file: VirtualFile): String? {
        if (file is LightVirtualFile || file !is VirtualFileWithId)
            return null

        val attributeStream = remoteConfigIdAttribute.readFileAttribute(file) ?: return null

        return attributeStream.use {
            if (it.available() > 0) {
                IOUtil.readString(it)
            } else {
                null
            }
        }
    }

    fun setPersistentAttribute(
        remoteConfigIdAttribute: FileAttribute,
        file: VirtualFile,
        value: String
    ) {
        if (file is LightVirtualFile || file !is VirtualFileWithId)
            return
        val os = remoteConfigIdAttribute.writeFileAttribute(file)
        os.use {
            IOUtil.writeString(value, it)
        }
    }

    private val DRIVER_ID_KEY = Key<String>("BdtDriverId")
    private val RFS_PATH_KEY = Key<String>("BdtRfsPath")
    private val DRIVER_ID_ATTRIBUTE = FileAttribute("BdtRfsDriverId", 1, true)
    private val RFS_PATH_ATTRIBUTE = FileAttribute("BdtRfsPathId", 1, true)
}