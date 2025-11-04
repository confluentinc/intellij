package io.confluent.intellijplugin

import io.confluent.intellijplugin.util.KafkaPropertiesUtils
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KafkaPropertiesTest {
    @Test
    fun testAdmin() {
        assertTrue(KafkaPropertiesUtils.getAdminPropertiesDescriptions().isNotEmpty())
    }
}
