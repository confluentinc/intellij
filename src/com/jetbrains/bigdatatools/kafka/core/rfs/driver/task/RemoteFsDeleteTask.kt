package com.jetbrains.bigdatatools.kafka.core.rfs.driver.task

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

abstract class RemoteFsDeleteTask(path: RfsPath) :
  RemoteFsTask(KafkaMessagesBundle.message("fs.task.delete.title", path.stringRepresentation()))