package com.jetbrains.bigdatatools.kafka

import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils

internal class KafkaPropertiesTest : UsefulTestCase() {
  fun testAdmin() {
    assertNotEmpty(KafkaPropertiesUtils.getAdminPropertiesDescriptions())
  }
}