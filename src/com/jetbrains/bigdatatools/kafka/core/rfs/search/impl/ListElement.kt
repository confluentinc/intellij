package com.jetbrains.bigdatatools.kafka.core.rfs.search.impl

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import javax.swing.Icon

open class ListElement(val fileInfo: FileInfo, var icon: Icon?) {
  val rfsPath: RfsPath
    get() = fileInfo.path
}