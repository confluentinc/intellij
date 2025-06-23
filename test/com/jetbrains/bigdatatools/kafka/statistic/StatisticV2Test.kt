package com.jetbrains.bigdatatools.kafka.statistic

import com.intellij.internal.statistic.eventLog.events.ListEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightPlatformTestCase
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.rfs.statistics.v2.BdtSettingsCollector
import com.jetbrains.bigdatatools.kafka.core.rfs.statistics.v2.BdtStatisticCollector
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.kafka.core.settings.defaultui.SettingsPanelCustomizer
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionGroup
import com.jetbrains.bigdatatools.kafka.settings.KafkaSettingsCustomizer
import com.jetbrains.bigdatatools.kafka.statistics.KafkaSettingsCollector
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import kotlin.reflect.KClass

class StatisticV2Test : LightPlatformTestCase() {

  override fun tearDown() {
    initCollectors()
  }

  fun testStatisticTest() {
    initCollectors()

    BdtStatisticCollector.collectors.map { entry ->
      val collector = entry.value
      println(
        "GroupID: ${collector.group.id}, version: ${collector.group.version}${collector.groupDescription.ifEmpty { null }?.let { " description: $it" } ?: ""}")
      collector.group.events.forEach { event ->
        val fields = event.getFields()
        println("EventID: " + event.eventId + " fields: ${fields.size}")
        fields.forEach { field ->
          val suffix = when (field) {
            is PrimitiveEventField<*> -> {
              ":${field::class.simpleName} ${field.validationRule.joinToString(separator = ", ")}"
            }
            is ListEventField<*> -> {
              ":${field::class.simpleName} ${field.validationRule}"
            }
            else -> {
              error("Unimplemented")
            }
          }
          println("  FieldId: ${field.name}${suffix.takeIf { s -> s.isNotEmpty() } ?: ""}")
        }
      }
      println("\n")
    }
  }

  fun _testKafkaStatisticInit() {
    testStatisticInit(KafkaSettingsCollector::class, KafkaConnectionGroup()) { connectionData, coroutineScope ->
      KafkaSettingsCustomizer(project, connectionData, testRootDisposable, coroutineScope)
    }
  }

  private fun <C : BdtSettingsCollector, D : ConnectionData> testStatisticInit(
    settingsCollectorClass: KClass<C>,
    connectionFactory: ConnectionFactory<D>,
    createSettingsCustomizer: (connectionData: D, coroutineScope: CoroutineScope) -> SettingsPanelCustomizer<D>,
  ) {
    val settingsCollector = BdtSettingsCollector.getInstance(BdtConnectionType.KAFKA)
    Assertions.assertInstanceOf(settingsCollectorClass.java, settingsCollector)

    val connectionData = connectionFactory.createBlankData()
    runBlocking {
      val coroutineScope = this.childScope()
      coroutineScope.cancel("No background processes assumed in this test")
      withContext(Dispatchers.EDT) {
        val settingsCustomizer = createSettingsCustomizer(connectionData, this)
        settingsCustomizer.init(connectionData)
        val defaultFields = settingsCustomizer.getDefaultFields()
        settingsCustomizer.getDefaultComponent(defaultFields, connectionData)
        settingsCustomizer.getAdditionalFields()
        settingsCustomizer.getAdditionalComponent(connectionData)
        Assertions.assertTrue(settingsCollector?.isSetupForFields == true)
      }
    }
  }

  companion object {
    private fun initCollectors() {
      KafkaSettingsCollector()
    }
  }
}