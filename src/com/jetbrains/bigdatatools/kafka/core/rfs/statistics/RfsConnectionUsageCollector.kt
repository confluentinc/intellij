@file:Suppress("DuplicatedCode")

package com.jetbrains.bigdatatools.kafka.core.rfs.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.*
import com.jetbrains.bigdatatools.kafka.core.rfs.fileType.RfsFileType
import com.jetbrains.bigdatatools.kafka.core.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.kafka.core.util.executeNotOnEdt
import kotlin.time.Duration

class RfsConnectionUsageCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private fun extractExtension(fileType: RfsFileType?): String = fileType?.getId()?.lowercase() ?: UNKNOWN
    private fun extractExtension(fileInfo: FileInfo): String = extractExtension(RfsFileType.getFileType(fileInfo))

    enum class CountRange(val text: String) {
      RANGE_1_1("1"),
      RANGE_2_5("2-5"),
      RANGE_6_25("6-25"),
      RANGE_26_50("26-50"),
      RANGE_51_200("51-200"),
      RANGE_201_MORE("201_more")
    }

    private fun getInfosCount(infos: Iterable<FileInfo>): CountRange =
      when (infos.count()) {
        1 -> CountRange.RANGE_1_1
        in 2..5 -> CountRange.RANGE_2_5
        in 6..25 -> CountRange.RANGE_6_25
        in 26..50 -> CountRange.RANGE_26_50
        in 51..100 -> CountRange.RANGE_51_200
        else -> CountRange.RANGE_201_MORE
      }

    enum class SizeRange(val text: String) {
      RANGE_0_0("0"),
      RANGE_1_1K("1_1k"),
      RANGE_1K_10K("1k_10k"),
      RANGE_10K_1M("10k_1m"),
      RANGE_1M_10M("1m_10m"),
      RANGE_10M_100M("10m_100m"),
      RANGE_100M_1G("100m_1g"),
      RANGE_OVER_1G("over_1g"),
    }

    private fun convertSize(length: Long): SizeRange = when {
      length == 0L -> SizeRange.RANGE_0_0
      length <= 1024 -> SizeRange.RANGE_1_1K
      length <= 10 * 1024 -> SizeRange.RANGE_1K_10K
      length <= 1024 * 1024 -> SizeRange.RANGE_10K_1M
      length <= 1024 * 1024 * 10 -> SizeRange.RANGE_1M_10M
      length <= 1024 * 1024 * 100 -> SizeRange.RANGE_10M_100M
      length <= 1024 * 1024 * 1024 -> SizeRange.RANGE_100M_1G
      else -> SizeRange.RANGE_OVER_1G
    }

    fun collectMoveAction(fileInfo: FileInfo, success: Boolean) = executeNotOnEdt {
      val eventFields = collectSingleFileInfoEventValues(fileInfo, success)
      if (eventFields != null) moveEvent.log(eventFields)
    }

    fun collectCopyFilePathAction(driver: Driver, isDirectory: Boolean) = executeNotOnEdt {
      val eventFields = collectSingleDriverEventValues(driver)
      if (eventFields != null) copyFilePathEvent.log(eventFields, isDirectoryField.with(isDirectory))
    }

    fun collectDeleteAction(infos: Iterable<FileInfo>, errorsCount: Int) = executeNotOnEdt {
      collectMultipleFileInfoEventValues(infos)?.let {
        deleteEvent.log(it + errorsCountField.with(errorsCount))
      }
    }

    fun collectMkDirAction(driver: Driver, success: Boolean) = executeNotOnEdt {
      collectSingleDriverEventValues(driver)?.let {
        mkDirEvent.log(it, successField.with(success))
      }
    }

    fun collectRefreshAction(driver: Driver) = executeNotOnEdt {
      collectSingleDriverEventValues(driver)?.let {
        refreshEvent.log(it)
      }
    }

    fun collectSaveToDiskAction(infos: Iterable<FileInfo>) = executeNotOnEdt {
      collectMultipleFileInfoEventValues(infos)?.let {
        saveToDiskEvent.log(it)
      }
    }

    fun collectShowFileInfoAction(info: FileInfo) = executeNotOnEdt {
      collectSingleFileInfoEventValues(info)?.let {
        showFileInfoActionEvent.log(it + hasMetaField.with(true))
      }
    }

    fun collectShowInFileManagerAction(info: FileInfo) = executeNotOnEdt {
      collectSingleFileInfoEventValues(info)?.let {
        showInFileManagerActionEvent.log(it)
      }
    }

    fun collectUploadFromDiskAction(driver: Driver, files: Iterable<FileInfo>) = executeNotOnEdt {
      val eventValues = collectSingleDriverEventValues(driver)
      if (eventValues != null) {
        val additionalValues = listOf(
          multipleFileInfosContainDirectoriesField.with(files.find { it.isDirectory } != null),
          multipleFileInfosContainFilesField.with(files.find { it.isFile } != null),
          multipleFileInfosFileExtensionsField.with(files.map { extractExtension(it) }.toSet().toList()),
          multipleFileInfosFileSizesField.with(files.map { convertSize(it.length) }.toSet().toList()),
          multipleFileInfosNodesCountField.with(getInfosCount(files))
        )
        uploadFromDiskEvent.log(listOf(eventValues) + additionalValues)
      }
    }

    fun collectFileViewerOpened(fileInfo: FileInfo, success: Boolean) = executeNotOnEdt {
      val eventValues = collectSingleFileInfoEventValues(fileInfo, success, false)
      if (eventValues != null) fileViewerOpenedEvent.log(eventValues)
    }

    fun collectDND(targetDriver: Driver, sources: Iterable<FileInfo>) = executeNotOnEdt {
      val driver = targetDriver as? DriverBase
      val driverType = driver?.driverType
      if (driver != null && driverType != null) {
        val baseValues = listOf(copyPasteTargetDriverTypeField.with(driverType))
        val multipleInfosValues = collectMultipleFileInfoEventValues(sources)
        if (multipleInfosValues != null) dragAndDropEvent.log(baseValues + multipleInfosValues)
      }
    }

    fun collectCopyPaste(targetDriver: Driver, sources: Iterable<FileInfo>) {
      val driver = targetDriver as? DriverBase
      val driverType = driver?.driverType
      if (driver != null && driverType != null) {
        val baseValues = listOf(copyPasteTargetDriverTypeField.with(driverType))
        val multipleInfosValues = collectMultipleFileInfoEventValues(sources)
        if (multipleInfosValues != null) copyPasteEvent.log(baseValues + multipleInfosValues)
      }
    }

    fun collectConnectionRefreshed(driver: DriverBase, result: ReadyConnectionStatus, timeConsumed: Duration, activitySource: ActivitySource) = executeNotOnEdt {
      val driverTypeValue = collectSingleDriverEventValues(driver)
      if (driverTypeValue != null) {
        if (result.isConnected()) {
          connectionRefreshSuccessfulEvent.log(driverTypeValue)
        }
        connectionRefreshedEvent.log(driverTypeValue,
                                     successfulField.with(result.isConnected()),
                                     exceptionClassField.with(result.getException()?.javaClass),
                                     timeConsumedField.with(timeConsumed.inWholeMilliseconds),
                                     activitySourceField.with(activitySource))
      }
    }

    const val UNKNOWN: String = "unknown"
    private val possibleExtensions: List<String> =
      listOf("json", "avro", "csv", "tsv", "no_extension", "parquet", "orc", "sequence", UNKNOWN)

    private val driverTypeRuleField = EventFields.Enum<BdtConnectionType>("driver_type")

    private val successfulField = EventFields.Boolean("success")
    private val exceptionClassField = EventFields.Class("exception_class")
    private val timeConsumedField = EventFields.DurationMs
    private val activitySourceField = EventFields.Enum<ActivitySource>("activity_source")

    private val copyPasteTargetDriverTypeField = EventFields.Enum<BdtConnectionType>("target_driver_type")

    private val multipleFileInfosConnectionDriverTypesField = EventFields.EnumList<BdtConnectionType>("connection_driver_types")
    private val multipleFileInfosContainDirectoriesField = BooleanEventField("contain_directories")
    private val multipleFileInfosContainFilesField = BooleanEventField("contain_files")
    private val multipleFileInfosFileExtensionsField = EventFields.StringList("file_extensions", possibleExtensions)

    private val multipleFileInfosFileSizesField = EventFields.EnumList<SizeRange>("file_sizes") { it.text }
    private val multipleFileInfosNodesCountField = EventFields.Enum<CountRange>("nodes_count") { it.text }

    //single file info id
    private val fileInfoSizeField = EventFields.Enum<SizeRange>("file_size")
    private val fileExtensionField = EventFields.String("file_extension", possibleExtensions)
    private val isDirectoryField = BooleanEventField("is_directory")
    private val successField = BooleanEventField("success")
    private val hasMetaField = BooleanEventField("has_meta")
    private val errorsCountField = IntEventField("errors_count")

    private val singleDriverFieldsArray: Array<EventField<out Any?>> = arrayOf(driverTypeRuleField)
    private val singleFileInfoWithDirectoryFieldsArray: Array<PrimitiveEventField<out Any?>> = arrayOf(driverTypeRuleField,
                                                                                                       isDirectoryField,
                                                                                                       fileExtensionField,
                                                                                                       fileInfoSizeField)

    private val singleFileInfoWithSuccessFieldsArray = arrayOf(driverTypeRuleField, isDirectoryField, fileExtensionField,
                                                               fileInfoSizeField, successField)

    private val multipleFileInfosFields = arrayOf(multipleFileInfosConnectionDriverTypesField,
                                                  multipleFileInfosContainDirectoriesField,
                                                  multipleFileInfosContainFilesField,
                                                  multipleFileInfosFileExtensionsField,
                                                  multipleFileInfosFileSizesField,
                                                  multipleFileInfosNodesCountField)

    private val GROUP = EventLogGroup("bigdatatools.rfs.usages", 12)

    private val connectionRefreshSuccessfulEvent = GROUP.registerVarargEvent("connection.refreshed", *singleDriverFieldsArray)

    private val connectionRefreshedEvent = GROUP.registerVarargEvent("connection.refresh.finished", *arrayOf(driverTypeRuleField,
                                                                                                             successfulField,
                                                                                                             exceptionClassField,
                                                                                                             timeConsumedField,
                                                                                                             activitySourceField))

    private val copyPasteEvent = GROUP.registerVarargEvent("copy.paste",
                                                           *(multipleFileInfosFields +
                                                             copyPasteTargetDriverTypeField))

    private val dragAndDropEvent = GROUP.registerVarargEvent("drag.and.drop",
                                                             *(multipleFileInfosFields +
                                                               copyPasteTargetDriverTypeField))

    private val uploadFromDiskEvent = GROUP.registerVarargEvent("action.upload.from.disk",
                                                                *(singleDriverFieldsArray + arrayOf(
                                                                  multipleFileInfosContainDirectoriesField,
                                                                  multipleFileInfosContainFilesField,
                                                                  multipleFileInfosFileExtensionsField,
                                                                  multipleFileInfosFileSizesField,
                                                                  multipleFileInfosNodesCountField)))

    private val saveToDiskEvent = GROUP.registerVarargEvent("action.save.to.disk", *multipleFileInfosFields)
    private val refreshEvent = GROUP.registerVarargEvent("action.refresh", *singleDriverFieldsArray)
    private val mkDirEvent = GROUP.registerVarargEvent("action.mkdir", *(singleDriverFieldsArray + successField))
    private val deleteEvent = GROUP.registerVarargEvent("action.delete", *(multipleFileInfosFields + errorsCountField))
    private val copyFilePathEvent = GROUP.registerVarargEvent("action.copy.file.path", *(singleDriverFieldsArray + isDirectoryField))
    private val moveEvent = GROUP.registerVarargEvent("action.move", *singleFileInfoWithSuccessFieldsArray)

    private fun collectMultipleFileInfoEventValues(infos: Iterable<FileInfo>): List<EventPair<out Any?>>? {
      val filteredInfos = infos.filter { (it.driver as? DriverBase)?.driverType != null }
      if (filteredInfos.isEmpty()) return null
      val drivers = filteredInfos.map { it.driver as DriverBase }
      return listOf(
        EventPair(multipleFileInfosConnectionDriverTypesField, drivers.mapNotNull { it.driverType }.toSet().toList()),
        EventPair(multipleFileInfosContainDirectoriesField, filteredInfos.find { it.isDirectory } != null),
        EventPair(multipleFileInfosContainFilesField, filteredInfos.find { it.isFile } != null),
        EventPair(multipleFileInfosFileExtensionsField, filteredInfos.map { extractExtension(it) }.toSet().toList()),
        EventPair(multipleFileInfosFileSizesField, filteredInfos.map { convertSize(it.length) }.toSet().toList()),
        EventPair(multipleFileInfosNodesCountField, getInfosCount(filteredInfos))
      )
    }

    private val fileViewerOpenedEvent = GROUP.registerVarargEvent("file.viewer.opened", *singleFileInfoWithSuccessFieldsArray)
    private val showInFileManagerActionEvent = GROUP.registerVarargEvent("action.show.in.file.manager",
                                                                         *singleFileInfoWithDirectoryFieldsArray)
    private val showFileInfoActionEvent = GROUP.registerVarargEvent("action.show.file.info",
                                                                    *(singleFileInfoWithDirectoryFieldsArray + arrayOf(hasMetaField)))

    private fun collectSingleDriverEventValues(driver: Driver): EventPair<out Any?>? {
      val driverType = (driver as? DriverBase)?.driverType ?: return null
      return driverTypeRuleField.with(driverType)
    }

    private fun collectSingleFileInfoEventValues(info: FileInfo,
                                                 opSuccess: Boolean? = null,
                                                 withIsDirectory: Boolean = true): List<EventPair<out Any?>>? {
      val baseInfos = collectSingleDriverEventValues(info.driver) ?: return null
      val isDirectoryValue = if (withIsDirectory) isDirectoryField.with(info.isDirectory) else null
      val successValue = if (opSuccess != null) successField.with(opSuccess) else null
      return listOf(baseInfos) + listOf(fileExtensionField.with(extractExtension(info)),
                                        fileInfoSizeField.with(convertSize(info.length))) +
             listOfNotNull(isDirectoryValue, successValue)
    }

    val Driver.driverType: BdtConnectionType?
      get() = (connectionData as? RemoteFsDriverProvider)?.rfsDriverType()
  }
}