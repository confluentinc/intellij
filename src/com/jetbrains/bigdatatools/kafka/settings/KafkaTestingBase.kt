package io.confluent.kafka.settings

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.settings.RfsConnectionTestingBase
import io.confluent.kafka.core.settings.defaultui.SettingsPanelCustomizer
import io.confluent.kafka.rfs.KafkaConnectionData

class KafkaTestingBase(
  project: Project,
  settingsCustomizer: SettingsPanelCustomizer<KafkaConnectionData>?
) : RfsConnectionTestingBase<KafkaConnectionData>(project, settingsCustomizer)