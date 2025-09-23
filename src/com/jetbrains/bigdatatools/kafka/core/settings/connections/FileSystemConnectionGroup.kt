package io.confluent.kafka.core.settings.connections

import com.intellij.icons.AllIcons
import io.confluent.kafka.util.KafkaMessagesBundle

/** Global super group for file system connections like "Local", "HDFS", "SFTP" */
class FileSystemConnectionGroup : ConnectionGroup(
  id = GROUP_ID,
  name = KafkaMessagesBundle.message("connection.group.name.fs"),
  icon = AllIcons.Nodes.CopyOfFolder
) {
  companion object {
    const val GROUP_ID = "FSConnectionGroup"
  }
}