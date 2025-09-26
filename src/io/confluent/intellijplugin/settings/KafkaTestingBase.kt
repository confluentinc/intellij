package io.confluent.intellijplugin.settings

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.settings.RfsConnectionTestingBase
import io.confluent.intellijplugin.core.settings.defaultui.SettingsPanelCustomizer
import io.confluent.intellijplugin.rfs.KafkaConnectionData

class KafkaTestingBase(
  project: Project,
  settingsCustomizer: SettingsPanelCustomizer<KafkaConnectionData>?
) : RfsConnectionTestingBase<KafkaConnectionData>(project, settingsCustomizer)