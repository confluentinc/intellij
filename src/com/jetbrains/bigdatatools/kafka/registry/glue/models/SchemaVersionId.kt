package com.jetbrains.bigdatatools.kafka.registry.glue.models

import software.amazon.awssdk.services.glue.model.SchemaId

data class SchemaVersionId(val schemaId: SchemaId, val versionId: Long)