package io.confluent.intellijplugin.util

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager

object KafkaControllerUtils {
    fun createTopicToolbar(): ActionGroup {
        //val createConsumer = ActionManager.getInstance().getAction("Kafka.Actions") as ActionGroup
        //val moreActionGroup = MoreActionGroup()
        //moreActionGroup.add(ActionManager.getInstance().getAction("kafka.ClearTopicAction"))
        //moreActionGroup.add(ActionManager.getInstance().getAction("kafka.DeleteTopicAction"))
        //return DefaultActionGroup(createConsumer, ActionManager.getInstance().getAction("kafka.create.producer"), Separator(),
        //                          ActionManager.getInstance().getAction("kafka.ClearTopicAction"),
        //                          ActionManager.getInstance().getAction("kafka.DeleteTopicAction"))

        return ActionManager.getInstance().getAction("Kafka.Actions") as ActionGroup
    }

}
