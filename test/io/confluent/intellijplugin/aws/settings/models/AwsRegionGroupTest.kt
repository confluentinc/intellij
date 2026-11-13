package io.confluent.intellijplugin.aws.settings.models

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.regions.Region

@TestApplication
class AwsRegionGroupTest {

    private fun shownRegions(group: AwsRegionGroup?): List<Region> =
        AwsRegionGroup.getRegionSubgroupsForGroup(group).flatten()

    @Nested
    inner class Global {

        @Test
        fun `should include every commercial-partition region known to the SDK`() {
            val expected = Region.regions()
                .filter { !it.isGlobalRegion && it.metadata()?.partition()?.id() == "aws" }
                .toSet()

            assertEquals(expected, shownRegions(AwsRegionGroup.GLOBAL).toSet())
        }

        @Test
        fun `should include regions added after the original hardcoded list`() {
            val shown = shownRegions(AwsRegionGroup.GLOBAL)

            // Region.of rather than constants: the compile classpath can resolve an older SDK than the
            // runtime classpath (e.g. no AP_SOUTHEAST_6 constant), which is why the list is derived at runtime.
            listOf("ap-southeast-5", "ap-southeast-6", "ap-southeast-7", "ap-east-2", "mx-central-1")
                .map { Region.of(it) }
                .forEach { assertTrue(it in shown, "Expected $it to be shown") }
        }

        @Test
        fun `should not contain empty subgroups`() {
            assertTrue(AwsRegionGroup.getRegionSubgroupsForGroup(AwsRegionGroup.GLOBAL).none { it.isEmpty() })
        }
    }

    @Nested
    inner class PartitionGroups {

        @Test
        fun `should put China regions only in the China group`() {
            assertEquals(setOf(Region.CN_NORTH_1, Region.CN_NORTHWEST_1), shownRegions(AwsRegionGroup.CHINA).toSet())
        }

        @Test
        fun `should put GovCloud regions only in the US_GOV group`() {
            assertEquals(setOf(Region.US_GOV_EAST_1, Region.US_GOV_WEST_1), shownRegions(AwsRegionGroup.US_GOV).toSet())
        }
    }

    @Nested
    inner class AllGroups {

        @Test
        fun `should combine all groups without duplicates`() {
            val all = shownRegions(null)

            assertEquals(all.size, all.toSet().size)
            assertEquals(
                AwsRegionGroup.entries.flatMap { shownRegions(it) }.toSet(),
                all.toSet(),
            )
        }

        @Test
        fun `should hide global pseudo-regions and isolated partitions`() {
            val all = shownRegions(null)

            assertFalse(all.any { it.isGlobalRegion }, "Global pseudo-regions must not be shown")
            listOf(Region.US_ISO_EAST_1, Region.US_ISOB_EAST_1, Region.US_ISO_WEST_1)
                .forEach { assertFalse(it in all, "Expected $it to be hidden") }
        }
    }
}
