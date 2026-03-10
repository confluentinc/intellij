package io.confluent.intellijplugin.consumer.client

import java.util.UUID

@JvmInline
internal value class SchemaRegistryClusterId(val id: String)

internal sealed interface SchemaCacheKey {
    @JvmInline
    value class ById(val schemaId: Int) : SchemaCacheKey

    @JvmInline
    value class ByGuid(val guid: UUID) : SchemaCacheKey
}
