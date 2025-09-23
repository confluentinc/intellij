package io.confluent.kafka.util

import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.monitoring.table.extension.LocalizedField
import kotlin.reflect.KProperty1

class KafkaLocalizedField<T : RemoteInfo>(field: KProperty1<T, *>, i18Key: String?) : LocalizedField<T>(field, i18Key) {
  override fun getLocalizedName() = i18Key?.let { KafkaMessagesBundle.message(it) } ?: ""
}