package com.jetbrains.bigdatatools.kafka.core.rfs.search.impl

import com.jetbrains.bigdatatools.kafka.core.filestorages.search.StorageSearchElement
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo

data class SearchResult(val results: List<SearchElement>,
                        val nextBatchId: String? = null,
                        val error: Throwable? = null) {
  companion object {
    fun ofFileInfos(query: String, files: List<FileInfo>): SearchResult {
      val elements = files.map {
        StorageSearchElement(query, it.driver, it.path, it)
      }
      return SearchResult(elements, null, null)
    }

    fun ofError(t: Throwable) = SearchResult(emptyList(), null, t)

    val empty = SearchResult(emptyList())
  }
}
