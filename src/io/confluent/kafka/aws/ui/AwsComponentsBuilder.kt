package io.confluent.kafka.aws.ui

import io.confluent.kafka.aws.settings.models.AwsRegionEntity
import io.confluent.kafka.aws.settings.models.AwsRegionGroup

object AwsComponentsBuilder {

  fun getAwsRegions(regionGroup: AwsRegionGroup?): List<Pair<String, List<AwsRegionEntity>>> {
    val subGroups = AwsRegionGroup.getRegionSubgroupsForGroup(regionGroup)
    return subGroups.map { "" to it.map { region -> AwsRegionEntity(region) } }
  }
}