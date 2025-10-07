package io.confluent.intellijplugin.core.rfs.fileType

import com.intellij.openapi.extensions.ExtensionPointName
import io.confluent.intellijplugin.core.settings.connections.InternalConnectionSettingsProvider
import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil

interface RfsFileTypeProvider {
    fun getFileType(): RfsFileType

    companion object {
        private val EP_NAME =
            ExtensionPointName.create<RfsFileTypeProvider>("com.intellij.bigdatatools.rfs.rfsFileTypeProvider")

        fun getAll(): List<RfsFileTypeProvider> =
            if (BdIdeRegistryUtil.isInternalFeaturesAvailable())
                EP_NAME.extensionList
            else
                EP_NAME.extensionList.filter { it !is InternalConnectionSettingsProvider }
    }
}