package com.jetbrains.bigdatatools.kafka.core.rfs.statistics.v2

import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType

abstract class BdtSettingsCollector : BdtStatisticCollector() {
  private lateinit var settingsApply: EventId2<Int, BdtConnectionType>
  private lateinit var settingsCancel: EventId2<Int, BdtConnectionType>
  private lateinit var settingsRunTestConnection: EventId2<Int, BdtConnectionType>
  private lateinit var settingsTestConnectionResult: EventId3<Int, BdtConnectionType, String>

  private lateinit var settingsPerProject: EventId3<Int, BdtConnectionType, Boolean>
  private lateinit var settingsIsEnabled: EventId3<Int, BdtConnectionType, Boolean>

  override fun init() {
    super.init()

    settingsApply = registryEventId1("connection.apply.invoke")
    settingsCancel = registryEventId1("connection.cancel.invoke")
    settingsRunTestConnection = registryEventId1("test.connection.invoke")
    settingsTestConnectionResult = registryActionEnum("test.connection.result",
                                                      StatisticTestConnectionResult.entries.map { it.name })
    settingsPerProject = registryCustomBooleanEvent("connection.per.project.changed")
    settingsIsEnabled = registryCustomBooleanEvent("connection.is.enabled.changed")
  }
  fun logSettingsCancel(): Unit = settingsCancel.log(index.incrementAndGet(), connectionType)
  fun logSettingsRunTestConnection(): Unit = settingsRunTestConnection.log(index.incrementAndGet(), connectionType)
  fun logSettingsTestConnectionFinished(status: StatisticTestConnectionResult): Unit =
    settingsTestConnectionResult.log(index.incrementAndGet(), connectionType, status.name)

  fun logSettingsApply(): Unit = settingsApply.log(index.incrementAndGet(), connectionType)
  fun logSettingsPerProject(selected: Boolean): Unit = settingsPerProject.log(index.incrementAndGet(), connectionType, selected)
  fun logSettingsIsEnabled(selected: Boolean): Unit = settingsIsEnabled.log(index.incrementAndGet(), connectionType, selected)

  companion object {
    fun getInstance(connType: BdtConnectionType): BdtSettingsCollector? = getInstance(connType.nameForStat) as? BdtSettingsCollector
  }
}