package io.confluent.intellijplugin.registry

import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class KafkaRegistryKeyValue(@Nls val presentable: String) {
    KEY(KafkaMessagesBundle.message("registry.key")),
    VALUE(KafkaMessagesBundle.message("registry.value"));
}