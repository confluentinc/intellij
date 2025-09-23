package io.confluent.kafka.core.rfs.search.impl

import io.confluent.kafka.core.rfs.driver.FileInfo

class ListResult(val results: List<ListElement>,
                 val nextBatchId: String? = null,
                 val error: Throwable? = null) {

  companion object {
    fun ofFileInfos(files: List<FileInfo>): ListResult {
      val elements = files.map {
        ListElement(it, null)
      }
      return ListResult(elements, null, null)
    }

    fun ofError(t: Throwable) = ListResult(emptyList(), null, t)

    val empty = ListResult(emptyList())
  }
}