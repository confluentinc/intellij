package com.jetbrains.bigdatatools.kafka.aws.ui

import com.jetbrains.bigdatatools.kafka.aws.settings.models.AwsRegionEntity
import com.jetbrains.bigdatatools.kafka.aws.settings.models.AwsRegionGroup

object AwsComponentsBuilder {

  fun getAwsRegions(regionGroup: AwsRegionGroup?): List<Pair<String, List<AwsRegionEntity>>> {
    val subGroups = AwsRegionGroup.getRegionSubgroupsForGroup(regionGroup)
    return subGroups.map { "" to it.map { region -> AwsRegionEntity(region) } }
  }
}