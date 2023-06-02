package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.rfs.settings.RfsConnectionTestingBase
import com.jetbrains.bigdatatools.common.settings.defaultui.SettingsPanelCustomizer
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

class KafkaTestingBase(
  project: Project,
  settingsCustomizer: SettingsPanelCustomizer<KafkaConnectionData>?
) : RfsConnectionTestingBase<KafkaConnectionData>(project, settingsCustomizer)