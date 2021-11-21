package com.jetbrains.bigdatatools.kafka

import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils

class KafkaPropertiesTests : UsefulTestCase() {
  fun testAdmin() {
    assertNotEmpty(KafkaPropertiesUtils.getAdminPropertiesDescriptions())
  }
}