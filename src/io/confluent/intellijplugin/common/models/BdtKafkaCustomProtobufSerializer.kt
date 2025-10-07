package io.confluent.intellijplugin.common.models

import com.google.protobuf.Message
import org.apache.kafka.common.serialization.Serializer

class BdtKafkaCustomProtobufSerializer : Serializer<Message> {
    override fun serialize(topic: String?, data: Message?): ByteArray {
        return data?.toByteArray() ?: byteArrayOf(0)
    }
}