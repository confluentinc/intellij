package io.confluent.intellijplugin.aws.settings.models

import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.core.settings.components.RenderableEntity
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import software.amazon.awssdk.regions.Region

enum class AwsRegionGroup(override val id: String, override val title: String) : RenderableEntity {
    GLOBAL("GLOBAL", KafkaMessagesBundle.message("settings.s3.region.group.global")),
    CHINA("CHINA", KafkaMessagesBundle.message("settings.s3.region.group.china")),
    US_GOV("US_GOV", KafkaMessagesBundle.message("settings.s3.region.group.us.gov"));

    companion object {
        private val chinaRegions = listOf(Region.CN_NORTH_1, Region.CN_NORTHWEST_1)
        private val usGovRegions = listOf(Region.US_GOV_EAST_1, Region.US_GOV_WEST_1)

        //Global
        private val usRegions = listOf(Region.US_EAST_1, Region.US_EAST_2, Region.US_WEST_1, Region.US_WEST_2)

        private val africaRegions = listOf(Region.AF_SOUTH_1)

        private val middleEast = listOf(Region.ME_SOUTH_1, Region.ME_CENTRAL_1, Region.IL_CENTRAL_1)

        private val southAmerica = listOf(Region.SA_EAST_1)

        private val asiaRegions = listOf(
            Region.AP_EAST_1, Region.AP_SOUTHEAST_3, Region.AP_SOUTH_1, Region.AP_SOUTH_2, Region.AP_NORTHEAST_3,
            Region.AP_NORTHEAST_2,
            Region.AP_SOUTHEAST_1, Region.AP_SOUTHEAST_2, Region.AP_SOUTHEAST_4, Region.AP_NORTHEAST_1
        )

        private val canadaRegions = listOf(Region.CA_CENTRAL_1)

        private val europeRegions = listOf(
            Region.EU_CENTRAL_1, Region.EU_CENTRAL_2, Region.EU_WEST_1, Region.EU_WEST_2, Region.EU_SOUTH_1,
            Region.EU_SOUTH_2, Region.EU_WEST_3,
            Region.EU_NORTH_1
        )

        //Do not show in UI!
        private val specialGlobalRegion = listOf(
            Region.AWS_GLOBAL, Region.AWS_CN_GLOBAL,
            Region.AWS_ISO_GLOBAL, Region.AWS_US_GOV_GLOBAL,
            Region.AWS_ISO_B_GLOBAL, Region.US_ISO_EAST_1, Region.US_ISOB_EAST_1, Region.US_ISO_WEST_1
        )

        private val globalRegions =
            usRegions + asiaRegions + europeRegions + canadaRegions + southAmerica + africaRegions + middleEast

        init {
            val otherRegions =
                Region.regions() - globalRegions - usGovRegions - chinaRegions - globalRegions - specialGlobalRegion
            if (otherRegions.isNotEmpty()) {
                thisLogger().error("We have not classified AWS Regions $otherRegions")
            }
        }

        fun getRegionSubgroupsForGroup(group: AwsRegionGroup?): List<List<Region>> = when (group) {
            GLOBAL -> listOf(
                usRegions,
                africaRegions,
                asiaRegions,
                canadaRegions,
                europeRegions,
                middleEast,
                southAmerica
            )

            CHINA -> listOf(chinaRegions)
            US_GOV -> listOf(usGovRegions)
            null -> getRegionSubgroupsForGroup(GLOBAL) + getRegionSubgroupsForGroup(CHINA) + getRegionSubgroupsForGroup(
                US_GOV
            )
        }
    }
}