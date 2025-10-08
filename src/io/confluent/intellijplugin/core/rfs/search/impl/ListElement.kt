package io.confluent.intellijplugin.core.rfs.search.impl

import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import javax.swing.Icon

open class ListElement(val fileInfo: FileInfo, var icon: Icon?) {
    val rfsPath: RfsPath
        get() = fileInfo.path
}