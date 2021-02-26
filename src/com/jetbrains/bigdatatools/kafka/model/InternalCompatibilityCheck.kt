package com.jetbrains.bigdatatools.kafka.model

import com.fasterxml.jackson.annotation.JsonProperty

class InternalCompatibilityCheck {
  @JsonProperty("is_compatible")
  private val isCompatible = false
}