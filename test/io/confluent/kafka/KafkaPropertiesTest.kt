package io.confluent.kafka

import com.intellij.testFramework.UsefulTestCase
import io.confluent.kafka.util.KafkaPropertiesUtils

internal class KafkaPropertiesTest : UsefulTestCase() {
  fun testAdmin() {
    assertNotEmpty(KafkaPropertiesUtils.getAdminPropertiesDescriptions())
  }
}