package com.jetbrains.bigdatatools.kafka.core.rfs.statistics.v2

import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import java.util.concurrent.atomic.AtomicInteger

interface StatisticInfoProvider {
  fun attachCollector(eventId: BaseEventId, index: AtomicInteger, type: BdtConnectionType) {}
  fun attachActionCollector(eventId: BaseEventId, index: AtomicInteger, type: BdtConnectionType) {}
}