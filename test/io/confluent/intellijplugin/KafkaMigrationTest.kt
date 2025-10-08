package io.confluent.intellijplugin

import com.intellij.testFramework.LightPlatformTestCase
import io.confluent.intellijplugin.rfs.KafkaConnectionData

internal class KafkaMigrationTest : LightPlatformTestCase() {
    @Suppress("DEPRECATION")
    fun testMigrate() {
        val connData = KafkaConnectionData(version = 3)
        val expected = "bootstrap.server=127.0.0.1"
        connData.properties = expected
        assertEmpty(connData.secretProperties)
        connData.migrate()
        assertEmpty(connData.properties)
        assertEquals(expected, connData.secretProperties)
    }
}