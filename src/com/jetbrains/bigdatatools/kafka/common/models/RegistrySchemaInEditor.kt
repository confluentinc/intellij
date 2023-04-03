package com.jetbrains.bigdatatools.kafka.common.models

data class RegistrySchemaInEditor(val schemaName: String, val registryName: String) : Comparable<RegistrySchemaInEditor> {
  override fun compareTo(other: RegistrySchemaInEditor): Int {
    val compareName = schemaName.compareTo(other.schemaName)
    return if (compareName != 0)
      compareName
    else
      registryName.compareTo(other.registryName)
  }

  override fun toString() = if (registryName.isBlank())
    schemaName
  else
    "$schemaName ($registryName)"

  companion object {
    val TOPIC_SCHEMA = RegistrySchemaInEditor(schemaName = "(Topic Name)", registryName = "")
  }
}