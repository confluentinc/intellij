package io.confluent.kafka.core.rfs.search.impl

import com.intellij.util.ui.EmptyIcon
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.DummyFileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.util.KafkaMessagesBundle

class MoreListElement(driver: Driver, val nextBatchId: String) : ListElement(
  DummyFileInfo(name = KafkaMessagesBundle.message("tree.action.label.load.more"),
                path = RfsPath(listOf(""), isDirectory = false),
                externalPath = "",
                driver = driver), EmptyIcon.ICON_16)
