package com.jetbrains.bigdatatools.kafka.common.models

data class RegistrySchemaInEditor(val schemaName: String) : Comparable<RegistrySchemaInEditor> {
  override fun compareTo(other: RegistrySchemaInEditor): Int {
    return schemaName.compareTo(other.schemaName)
  }

  override fun toString() = schemaName
}