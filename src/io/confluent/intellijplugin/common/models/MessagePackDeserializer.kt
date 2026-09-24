package io.confluent.intellijplugin.common.models

import com.fasterxml.jackson.databind.ObjectMapper
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import org.msgpack.jackson.dataformat.MessagePackFactory

class MessagePackDeserializer : Deserializer<ByteArray> {
    private val jsonMapper = ObjectMapper()
    private val msgpackMapper = ObjectMapper(MessagePackFactory())

    override fun deserialize(topic: String?, data: ByteArray): ByteArray {
        val node = try {
            msgpackMapper.readTree(data)
        } catch (e: Exception) {
            throw SerializationException(KafkaMessagesBundle.message("error.output.row.messagepack.invalid"), e)
        }
        return jsonMapper.writeValueAsBytes(node)
    }
}
