package io.confluent.intellijplugin.core.settings.connections

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.constants.BdtPluginType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class GeneralConnectionSettingsProviderTest {

    private val provider = GeneralConnectionSettingsProvider()

    @Nested
    @DisplayName("pluginType")
    inner class PluginType {

        @Test
        fun `should return KAFKA plugin type`() {
            assertEquals(BdtPluginType.KAFKA, provider.pluginType)
        }
    }

    @Nested
    @DisplayName("createConnectionGroups")
    inner class CreateConnectionGroups {

        @Test
        fun `should return two groups`() {
            val groups = provider.createConnectionGroups()

            assertEquals(2, groups.size)
        }

        @Test
        fun `should have correct group IDs`() {
            val groups = provider.createConnectionGroups()
            val ids = groups.map { it.id }.toSet()

            assertTrue(ids.contains(BrokerConnectionGroup.GROUP_ID))
            assertTrue(ids.contains(CCloudDisplayGroup.GROUP_ID))
        }
    }
}
