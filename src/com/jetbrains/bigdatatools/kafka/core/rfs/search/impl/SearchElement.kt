package com.jetbrains.bigdatatools.kafka.core.rfs.search.impl

import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.table.renderers.DataRenderingUtil
import javax.swing.Icon
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

interface SearchElement : RemoteInfo {
  val rfsPath: RfsPath
  val fileName: String // ToDo. Currently this is full path with all folders and file name.
  val query: String
  val connId: String  // For focusing in tree from search results
  val name: String // ToDo. Only name of the file.
  val snippet: String // Used in zeppelin Fulltext search.
  val text: String // Used in zeppelin Fulltext search.
  val id: String  // NoteId, only for Zeppelin
  val header: String  // Zeppelin text search result header of found item with .

  val fileInfo: FileInfo?

  var icon: Icon?

  companion object {
    val renderableColumns: List<KProperty1<SearchElement, *>> by lazy {
      SearchElement::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}