package io.confluent.intellijplugin.common.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class MessagePackSerializerTest {

    private val serializer = MessagePackSerializer()
    private val deserializer = MessagePackDeserializer()

    private fun roundTrip(json: String): String =
        String(deserializer.deserialize("topic", serializer.serialize("topic", json)), StandardCharsets.UTF_8)

    @Test
    fun `should round-trip json with string and boolean values`() {
        val json = """{"name":"kafka","active":true,"enabled":false,"tags":["a","b"]}"""

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip nested objects and arrays`() {
        val json = """{"outer":{"inner":[{"id":"1"}],"ok":true},"empty":[],"emptyObj":{}}"""

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip integer and floating point numbers preserving precision`() {
        val json = """{"count":3,"big":9223372036854775807,"ratio":1.5,"negative":-7}"""

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip null values`() {
        val json = """{"a":null,"b":1}"""

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip top-level array`() {
        val json = """[1,"two",true,null]"""

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should throw for malformed json input`() {
        assertThrows(Exception::class.java) {
            serializer.serialize("topic", """{"a": """)
        }
    }

    @Test
    fun `should serialize empty input to empty byte array`() {
        val result = serializer.serialize("topic", "")

        assertEquals(0, result.size)
    }

    @Test
    fun `should serialize blank input to empty byte array`() {
        val result = serializer.serialize("topic", "   ")

        assertEquals(0, result.size)
    }

    @Test
    fun `should throw friendly error for empty payload`() {
        val exception = assertThrows(Exception::class.java) {
            deserializer.deserialize("topic", ByteArray(0))
        }

        assertEquals("Invalid MessagePack payload", exception.message)
    }

    @Test
    fun `should throw friendly error for truncated payload`() {
        val exception = assertThrows(Exception::class.java) {
            deserializer.deserialize("topic", byteArrayOf(0xdc.toByte()))
        }

        assertEquals("Invalid MessagePack payload", exception.message)
    }

    @Test
    fun `should round-trip null literal`() {
        val json = "null"

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip bool`() {
        val json = "true"

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip integer`() {
        val json = "10"

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip negative float`() {
        val json = "-0.001"

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip string literal`() {
        val json = "\"text\""

        assertEquals(json, roundTrip(json))
    }

    @Test
    fun `should round-trip empty array`() {
        val json = "[]"

        assertEquals(json, roundTrip(json))
    }
}
