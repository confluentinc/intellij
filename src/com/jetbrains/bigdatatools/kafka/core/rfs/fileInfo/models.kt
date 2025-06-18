package com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath

/**
 * [marker] is a continuation token or a filename or null if there is no availability to load more
 * @see software.amazon.awssdk.services.s3.model.ListObjectsV2Request.startAfter
 * @see software.amazon.awssdk.services.s3.model.ListObjectsV2Request.continuationToken
 */
data class RfsListMarker(val marker: String?)

data class RfsChildrenPartId(val rfsPath: RfsPath, val markerId: RfsListMarker? = null)

/**
 * @param fileInfos is null when directory doesn't exist (or it's not a directory)
 * @param fromMarker is null when it is first block
 * @param nextMarker continuation marker which can be used to query next block
 */
data class RfsFileInfoChildren(val fileInfos: List<FileInfo>?, val fromMarker: RfsListMarker? = null, val nextMarker: RfsListMarker? = null)

