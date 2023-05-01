package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle


class KafkaFlowController {
  private lateinit var flowRecordsCountPerRequest: Cell<JBIntSpinner>
  private lateinit var generateRandomKey: Cell<JBCheckBox>
  private lateinit var generateRandomValue: Cell<JBCheckBox>
  private lateinit var mode: SegmentedButton<Mode>
  private lateinit var autoParams: Panel

  fun getComponent(panel: Panel) = panel.apply {
    collapsibleGroup(KafkaMessagesBundle.message("producer.group.flow")) {
      row(KafkaMessagesBundle.message("producer.flow.records.count")) {
        flowRecordsCountPerRequest = spinner(1..1000, 1)
      }
      row {
        generateRandomKey = checkBox(KafkaMessagesBundle.message("producer.flow.generate.random.key"))

      }
      row {
        generateRandomValue = checkBox(KafkaMessagesBundle.message("producer.flow.generate.random.value"))
      }
      row(KafkaMessagesBundle.message("producer.flow.mode.label")) {
        mode = this.segmentedButton(Mode.values().toList()) { it.label }
        mode.selectedItem = Mode.MANUAL
      }
      autoParams = panel {
        row(KafkaMessagesBundle.message("producer.flow.interval")) {
          spinner(1000..60000, 100)
        }
        groupRowsRange(KafkaMessagesBundle.message("producer.flow.stop.conditions.title"), indent = true, topGroupGap = false) {
          row(KafkaMessagesBundle.message("producer.flow.stop.conditions.count")) {
            spinner(0..1000, 10)
          }
          row(KafkaMessagesBundle.message("producer.flow.stop.conditions.elapsed.time")) {
            spinner(0..60000, 100)
          }.bottomGap(BottomGap.MEDIUM)
        }
      }
    }.bottomGap(BottomGap.NONE).topGap(TopGap.NONE)

    mode.whenItemSelected {
      updateModeVisibility()
    }
    updateModeVisibility()
  }

  private fun updateModeVisibility() {
    autoParams.visible(mode.selectedItem == Mode.AUTO)
  }

  enum class Mode(val label: String) {
    MANUAL(KafkaMessagesBundle.message("producer.flow.mode.manual")),
    AUTO(KafkaMessagesBundle.message("producer.flow.mode.auto"))
  }
}