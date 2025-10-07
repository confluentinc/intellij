package io.confluent.intellijplugin.model

import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.util.KafkaLocalizedField
import io.confluent.intellijplugin.util.generator.PrimitivesGenerator

data class SchemaRegistryFieldsInfo(
    val name: String,
    val type: String,
    val default: String,
    val description: String,
    val required: String
) : RemoteInfo {
    val id = "$name${PrimitivesGenerator.generateLong()}"

    override fun toString(): String = name

    companion object {
        val renderableColumns: List<KafkaLocalizedField<SchemaRegistryFieldsInfo>> by lazy {
            listOf(
                KafkaLocalizedField(SchemaRegistryFieldsInfo::name, "data.SchemaRegistryFieldsInfo.name"),
                KafkaLocalizedField(SchemaRegistryFieldsInfo::type, "data.SchemaRegistryFieldsInfo.type"),
                KafkaLocalizedField(SchemaRegistryFieldsInfo::default, "data.SchemaRegistryFieldsInfo.default"),
                KafkaLocalizedField(SchemaRegistryFieldsInfo::description, "data.SchemaRegistryFieldsInfo.description"),
                KafkaLocalizedField(SchemaRegistryFieldsInfo::required, "data.SchemaRegistryFieldsInfo.required"),
                KafkaLocalizedField(SchemaRegistryFieldsInfo::id, "data.SchemaRegistryFieldsInfo.id")
            )
        }
    }
}