package io.confluent.intellijplugin.registry.glue

import com.amazonaws.services.schemaregistry.deserializers.protobuf.ProtobufSchemaParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Glue serde 1.1.25 is built against Wire 5.2 but runs against the Wire 6 that Schema Registry 8.3 brings in (#637)
class GlueProtobufWireCompatTest {

    @Test
    fun `should parse a Protobuf schema with Glue against the bundled Wire`() {
        val schema = javaClass.getResourceAsStream("/fixtures/glue/wire-compat.proto")!!.reader().readText()

        val descriptor = ProtobufSchemaParser.parse(schema, "wire-compat.proto")

        val order = descriptor.findMessageTypeByName("Order")
        assertEquals("demo.v1", descriptor.`package`)
        assertEquals(listOf("payment"), order.realOneofs.map { it.name })
        assertTrue(order.findFieldByName("quantities").isMapField)
        assertEquals("google.protobuf.Timestamp", order.findFieldByName("created_at").messageType.fullName)
        assertEquals(setOf("Line", "QuantitiesEntry"), order.nestedTypes.map { it.name }.toSet())
    }
}
