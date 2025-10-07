package io.confluent.intellijplugin.core.rfs.driver.task

import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.util.KafkaMessagesBundle

abstract class RemoteFsDeleteTask(path: RfsPath) :
    RemoteFsTask(KafkaMessagesBundle.message("fs.task.delete.title", path.stringRepresentation()))