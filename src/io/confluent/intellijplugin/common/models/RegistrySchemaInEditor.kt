package io.confluent.intellijplugin.common.models

import com.intellij.openapi.util.NlsSafe
import io.confluent.intellijplugin.registry.KafkaRegistryFormat

data class RegistrySchemaInEditor(
    @NlsSafe val schemaName: String,
    val schemaFormat: KafkaRegistryFormat?
) : Comparable<RegistrySchemaInEditor> {
    override fun compareTo(other: RegistrySchemaInEditor) = schemaName.compareTo(other.schemaName)

    override fun toString() = schemaName
}