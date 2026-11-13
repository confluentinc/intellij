package io.confluent.intellijplugin.aws.settings.models

import io.confluent.intellijplugin.core.settings.components.RenderableEntity
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import software.amazon.awssdk.regions.Region

/**
 * Groups the AWS regions known to the bundled AWS SDK for display in region pickers.
 *
 * Regions are derived from [Region.regions] and classified by their SDK partition, so new regions
 * appear automatically when the SDK is upgraded instead of requiring a hand-maintained list.
 * Pseudo-regions (e.g. `aws-global`) and isolated/sovereign partitions (ISO, EUSC) are not shown.
 */
enum class AwsRegionGroup(
    override val id: String,
    override val title: String,
    internal val partitionId: String,
) : RenderableEntity {
    GLOBAL("GLOBAL", KafkaMessagesBundle.message("settings.s3.region.group.global"), "aws"),
    CHINA("CHINA", KafkaMessagesBundle.message("settings.s3.region.group.china"), "aws-cn"),
    US_GOV("US_GOV", KafkaMessagesBundle.message("settings.s3.region.group.us.gov"), "aws-us-gov");

    companion object {
        /**
         * Display order of the commercial-partition subgroups, keyed by region id prefix
         * (e.g. `ap` in `ap-southeast-6`). Regions with an unlisted prefix go into a trailing subgroup.
         */
        private val globalSubgroupPrefixes = listOf(
            setOf("us", "ca", "mx"), // North America
            setOf("sa"),             // South America
            setOf("eu"),             // Europe
            setOf("me", "il"),       // Middle East
            setOf("af"),             // Africa
            setOf("ap"),             // Asia Pacific
        )
        private val knownPrefixes = globalSubgroupPrefixes.flatten().toSet()

        private val regionsByPartition: Map<String, List<Region>> by lazy {
            Region.regions()
                .filterNot { it.isGlobalRegion }
                .mapNotNull { region -> region.metadata()?.partition()?.id()?.let { it to region } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, regions) -> regions.sortedBy { it.id() } }
        }

        private fun regionsFor(group: AwsRegionGroup): List<Region> =
            regionsByPartition[group.partitionId].orEmpty()

        private fun prefixOf(region: Region): String = region.id().substringBefore('-')

        fun getRegionSubgroupsForGroup(group: AwsRegionGroup?): List<List<Region>> = when (group) {
            GLOBAL -> {
                val regionsByPrefix = regionsFor(GLOBAL).groupBy(::prefixOf)
                val subgroups = globalSubgroupPrefixes.map { prefixes ->
                    prefixes.flatMap { regionsByPrefix[it].orEmpty() }.sortedBy { it.id() }
                }
                val other = regionsByPrefix.filterKeys { it !in knownPrefixes }.values.flatten().sortedBy { it.id() }
                (subgroups + listOf(other)).filter { it.isNotEmpty() }
            }

            CHINA, US_GOV -> listOf(regionsFor(group)).filter { it.isNotEmpty() }

            null -> entries.flatMap { getRegionSubgroupsForGroup(it) }
        }
    }
}
