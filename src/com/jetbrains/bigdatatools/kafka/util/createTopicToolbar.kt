package com.jetbrains.bigdatatools.kafka.util

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.MoreActionGroup

object KafkaControllerUtils {
  fun createTopicToolbar(): DefaultActionGroup {
    val createProducer = ActionManager.getInstance().getAction("kafka.create.producer")
    val createConsumer = ActionManager.getInstance().getAction("kafka.create.consumer")
    val moreActionGroup = MoreActionGroup()
    moreActionGroup.add(ActionManager.getInstance().getAction("kafka.ClearTopicAction"))
    moreActionGroup.add(ActionManager.getInstance().getAction("kafka.DeleteTopicAction"))
    return DefaultActionGroup(createConsumer, createProducer, moreActionGroup)
  }

}
