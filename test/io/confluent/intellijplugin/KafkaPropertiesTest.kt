package io.confluent.intellijplugin

import com.intellij.testFramework.UsefulTestCase
import io.confluent.intellijplugin.util.KafkaPropertiesUtils

internal class KafkaPropertiesTest : UsefulTestCase() {
  fun testAdmin() {
    assertNotEmpty(KafkaPropertiesUtils.getAdminPropertiesDescriptions())
  }
}