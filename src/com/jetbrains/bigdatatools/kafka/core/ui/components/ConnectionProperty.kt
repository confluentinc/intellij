package com.jetbrains.bigdatatools.kafka.core.ui.components

import com.squareup.moshi.Json

data class ConnectionProperty(
  @Json(name = "Property Name")
  val propertyName: String,
  @Json(name = "Default")
  val default: String,
  @Json(name = "Meaning")
  val meaning: String,
  @Json(name = "Since Version")
  val rightSideInfo: String
)