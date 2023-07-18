package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.kafka.producer.models.Mode
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerFlowParams
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle


class KafkaFlowController {
  private lateinit var flowRecordsCountPerRequest: Cell<JBIntSpinner>
  private lateinit var requestInterval: Cell<JBIntSpinner>
  private lateinit var totalRequest: Cell<JBIntSpinner>
  private lateinit var totalElapsedTime: Cell<JBIntSpinner>
  private lateinit var generateRandomKeys: Cell<JBCheckBox>
  private lateinit var generateRandomValues: Cell<JBCheckBox>
  private lateinit var mode: SegmentedButton<Mode>
  private lateinit var autoParams: Panel

  fun getComponent(panel: Panel) = panel.apply {
    collapsibleGroup(KafkaMessagesBundle.message("producer.group.flow")) {
      row(KafkaMessagesBundle.message("producer.flow.records.count")) {
        flowRecordsCountPerRequest = spinner(1..1000, 1)
      }
      row {
        generateRandomKeys = checkBox(KafkaMessagesBundle.message("producer.flow.generate.random.key"))

      }
      row {
        generateRandomValues = checkBox(KafkaMessagesBundle.message("producer.flow.generate.random.value"))
      }
      row(KafkaMessagesBundle.message("producer.flow.mode.label")) {
        mode = this.segmentedButton(Mode.values().toList()) { text = it.label }
        mode.selectedItem = Mode.MANUAL
      }
      autoParams = panel {
        row(KafkaMessagesBundle.message("producer.flow.interval")) {
          requestInterval = spinner(1000..60000, 1000)
        }
        groupRowsRange(KafkaMessagesBundle.message("producer.flow.stop.conditions.title"), indent = true, topGroupGap = false) {
          row(KafkaMessagesBundle.message("producer.flow.stop.conditions.count")) {
            totalRequest = spinner(0..1000, 10)
          }
          row(KafkaMessagesBundle.message("producer.flow.stop.conditions.elapsed.time")) {
            totalElapsedTime = spinner(0..600000, 1000)
          }.bottomGap(BottomGap.MEDIUM)
        }
      }
    }.bottomGap(BottomGap.NONE).topGap(TopGap.NONE)

    mode.whenItemSelected {
      updateModeVisibility()
    }
    updateModeVisibility()
  }

  fun getParams(): ProducerFlowParams = ProducerFlowParams(
    mode = mode.selectedItem ?: Mode.MANUAL,
    flowRecordsCountPerRequest = flowRecordsCountPerRequest.component.number,
    generateRandomKeys = generateRandomKeys.selected.invoke(),
    generateRandomValues = generateRandomValues.selected.invoke(),
    requestInterval = requestInterval.component.number,
    totalRequests = totalRequest.component.number,
    totalElapsedTime = totalElapsedTime.component.number,
  )

  fun setParams(params: ProducerFlowParams) {
    mode.selectedItem = params.mode
    generateRandomKeys.selected(params.generateRandomKeys)
    generateRandomValues.selected(params.generateRandomValues)
    flowRecordsCountPerRequest.component.number = params.flowRecordsCountPerRequest
    requestInterval.component.number = params.requestInterval
    totalRequest.component.number = params.totalRequests
    totalElapsedTime.component.number = params.totalElapsedTime
  }

  private fun updateModeVisibility() {
    autoParams.visible(mode.selectedItem == Mode.AUTO)
  }
}