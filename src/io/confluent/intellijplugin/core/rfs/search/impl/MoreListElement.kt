package io.confluent.intellijplugin.core.rfs.search.impl

import com.intellij.util.ui.EmptyIcon
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.DummyFileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class MoreListElement(driver: Driver, val nextBatchId: String) : ListElement(
  DummyFileInfo(name = KafkaMessagesBundle.message("tree.action.label.load.more"),
                path = RfsPath(listOf(""), isDirectory = false),
                externalPath = "",
                driver = driver), EmptyIcon.ICON_16)
