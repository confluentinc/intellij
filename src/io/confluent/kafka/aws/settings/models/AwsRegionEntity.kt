package io.confluent.kafka.aws.settings.models

import io.confluent.kafka.core.settings.components.RenderableEntity
import org.jetbrains.annotations.Nls
import software.amazon.awssdk.regions.Region

data class AwsRegionEntity(override val id: String, @Nls override val title: String) : RenderableEntity {
  @Suppress("HardCodedStringLiteral") // description() comes from amazon.awssdk and cannot be localized.
  constructor(region: Region) : this(region.id(), region.metadata()?.description() ?: "")
}