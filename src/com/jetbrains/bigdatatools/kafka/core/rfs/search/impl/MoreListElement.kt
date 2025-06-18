package com.jetbrains.bigdatatools.kafka.core.rfs.search.impl

import com.intellij.util.ui.EmptyIcon
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.DummyFileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class MoreListElement(driver: Driver, val nextBatchId: String) : ListElement(
  DummyFileInfo(name = KafkaMessagesBundle.message("tree.action.label.load.more"),
                path = RfsPath(listOf(""), isDirectory = false),
                externalPath = "",
                driver = driver), EmptyIcon.ICON_16)
