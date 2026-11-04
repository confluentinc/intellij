package io.confluent.intellijplugin.registry

import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlin.time.Duration

/** The caller stopped waiting for a schema write; the registry may still have applied it. */
class SchemaWriteTimeoutException(timeout: Duration, cause: Throwable) :
    RuntimeException(
        KafkaMessagesBundle.message("error.schema.registry.write.timeout", timeout.inWholeSeconds),
        cause
    )
