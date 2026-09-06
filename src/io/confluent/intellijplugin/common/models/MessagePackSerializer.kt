package io.confluent.intellijplugin.common.models

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.serialization.Serializer
import org.msgpack.jackson.dataformat.MessagePackFactory

class MessagePackSerializer : Serializer<String> {
    private val jsonMapper = ObjectMapper()
    private val msgpackMapper = ObjectMapper(MessagePackFactory())

    override fun serialize(topic: String?, data: String): ByteArray {
        if (data.isBlank()) return ByteArray(size = 0)
        val node = jsonMapper.readTree(data)
        return msgpackMapper.writeValueAsBytes(node)
    }
}
