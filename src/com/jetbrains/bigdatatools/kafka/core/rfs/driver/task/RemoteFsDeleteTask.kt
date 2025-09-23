package io.confluent.kafka.core.rfs.driver.task

import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.util.KafkaMessagesBundle

abstract class RemoteFsDeleteTask(path: RfsPath) :
  RemoteFsTask(KafkaMessagesBundle.message("fs.task.delete.title", path.stringRepresentation()))