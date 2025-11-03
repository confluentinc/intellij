package io.confluent.intellijplugin

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class KafkaMigrationTest {
    @Test
    @Suppress("DEPRECATION")
    fun testMigrate() {
        val connData = KafkaConnectionData(version = 3)
        val expected = "bootstrap.server=127.0.0.1"
        connData.properties = expected
        assertTrue(connData.secretProperties.isEmpty())
        connData.migrate()
        assertTrue(connData.properties.isEmpty())
        assertEquals(expected, connData.secretProperties)
    }
}
