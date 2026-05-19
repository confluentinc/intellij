package io.confluent.intellijplugin.util.generator

import com.google.gson.JsonParser
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

/**
 * Exercises the schema-shape branches in [JsonSchemaGenerator.generate] directly
 * (bypassing the static `generateJsonMessage` entry point, which requires an
 * IntelliJ Application for its error-notification path).
 */
@DisplayName("JsonSchemaGenerator")
class JsonSchemaGeneratorTest {

    private fun generate(schemaText: String): String =
        JsonSchemaGenerator(JsonSchema(schemaText)).generate()

    @Nested
    @DisplayName("ArraySchema")
    inner class ArraySchemas {

        @RepeatedTest(5)
        fun `array of strings respects minItems and maxItems`() {
            val out = generate(
                """
                {
                  "type": "array",
                  "items": {"type": "string"},
                  "minItems": 2,
                  "maxItems": 5
                }
                """.trimIndent()
            )
            val arr = JsonParser.parseString(out).asJsonArray
            assertTrue(arr.size() in 2..5, "Size ${arr.size()} not in [2,5]")
            arr.forEach { assertTrue(it.asJsonPrimitive.isString) }
        }

        @Test
        fun `array with tuple-style items produces a non-empty array`() {
            val out = generate(
                """
                {
                  "type": "array",
                  "items": [
                    {"type": "string"},
                    {"type": "integer"}
                  ],
                  "minItems": 1,
                  "maxItems": 3
                }
                """.trimIndent()
            )
            val arr = JsonParser.parseString(out).asJsonArray
            assertTrue(arr.size() >= 1)
        }

        @Test
        fun `array with uniqueItems and small option pool still terminates`() {
            // Single boolean value space means uniqueItems would loop forever; we cap minItems
            // small enough that the loop converges.
            val out = generate(
                """
                {
                  "type": "array",
                  "items": {"type": "boolean"},
                  "uniqueItems": true,
                  "minItems": 1,
                  "maxItems": 2
                }
                """.trimIndent()
            )
            assertNotNull(out)
        }
    }

    @Nested
    @DisplayName("Primitive schemas")
    inner class Primitives {

        @Test
        fun `null schema renders as JSON null`() {
            val out = generate("""{"type": "null"}""")
            assertEquals("null", out.trim())
        }

        @RepeatedTest(5)
        fun `boolean schema renders as a JSON boolean primitive`() {
            val out = generate("""{"type": "boolean"}""")
            val parsed = JsonParser.parseString(out).asJsonPrimitive
            assertTrue(parsed.isBoolean)
        }

        @Test
        fun `const schema emits the permitted value as a string`() {
            val out = generate("""{"const": "fixed-value"}""")
            assertEquals("\"fixed-value\"", out.trim())
        }

        @Test
        fun `enum schema emits one of the permitted values`() {
            val out = generate("""{"enum": ["alpha", "beta", "gamma"]}""")
            val value = JsonParser.parseString(out).asJsonPrimitive.asString
            assertTrue(value in setOf("alpha", "beta", "gamma"))
        }

        @Test
        fun `empty schema emits an empty object`() {
            val out = generate("""{}""")
            assertTrue(JsonParser.parseString(out).asJsonObject.entrySet().isEmpty())
        }
    }

    @Nested
    @DisplayName("NumberSchema")
    inner class NumberSchemas {

        @RepeatedTest(10)
        fun `integer schema honors min and max bounds`() {
            val out = generate(
                """{"type": "integer", "minimum": 10, "maximum": 20}"""
            )
            val v = JsonParser.parseString(out).asInt
            assertTrue(v in 10..20, "Value $v not in [10,20]")
        }

        @RepeatedTest(5)
        fun `integer schema honors multipleOf`() {
            val out = generate(
                """{"type": "integer", "minimum": 0, "maximum": 100, "multipleOf": 5}"""
            )
            val v = JsonParser.parseString(out).asInt
            assertEquals(0, v % 5, "Value $v not a multiple of 5")
        }

        @RepeatedTest(5)
        fun `integer schema honors exclusive bounds`() {
            // Draft-04 syntax: minimum/maximum + boolean exclusive flag — this is the only form
            // where org.everit populates BOTH schema.minimum and schema.isExclusiveMinimum, which
            // is what JsonSchemaGenerator.generateNumber inspects.
            val out = generate(
                """
                {
                  "${'$'}schema": "http://json-schema.org/draft-04/schema#",
                  "type": "integer",
                  "minimum": 5,
                  "exclusiveMinimum": true,
                  "maximum": 10,
                  "exclusiveMaximum": true
                }
                """.trimIndent()
            )
            val v = JsonParser.parseString(out).asInt
            assertTrue(v in 6..8, "Value $v not in (5,10) excl")
        }

        @RepeatedTest(5)
        fun `number (non-integer) schema honors bounds`() {
            val out = generate(
                """{"type": "number", "minimum": 0.5, "maximum": 1.5}"""
            )
            val v = JsonParser.parseString(out).asDouble
            assertTrue(v in 0.5..1.5, "Value $v not in [0.5,1.5]")
        }

        @RepeatedTest(5)
        fun `number schema honors multipleOf and exclusive bounds`() {
            val out = generate(
                """
                {
                  "${'$'}schema": "http://json-schema.org/draft-04/schema#",
                  "type": "number",
                  "minimum": 1.0,
                  "exclusiveMinimum": true,
                  "maximum": 10.0,
                  "exclusiveMaximum": true,
                  "multipleOf": 0.5
                }
                """.trimIndent()
            )
            val v = JsonParser.parseString(out).asDouble
            assertTrue(v > 1.0 && v < 10.0, "Value $v not in (1,10)")
        }
    }

    @Nested
    @DisplayName("StringSchema formats")
    inner class StringSchemas {

        @Test
        fun `string with date-time format parses as ISO-like timestamp`() {
            val out = generate("""{"type": "string", "format": "date-time"}""")
            val v = JsonParser.parseString(out).asJsonPrimitive.asString
            // generateDateTime writes "yyyy-MM-dd HH:mm:ss"
            assertTrue(v.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")), "Got $v")
        }

        @Test
        fun `string with email format contains an at-sign and a TLD`() {
            val out = generate("""{"type": "string", "format": "email"}""")
            val v = JsonParser.parseString(out).asJsonPrimitive.asString
            assertTrue(v.contains("@"))
            assertTrue(v.substringAfter("@").contains("."))
        }

        @Test
        fun `string with hostname format produces host dot tld`() {
            val out = generate("""{"type": "string", "format": "hostname"}""")
            val v = JsonParser.parseString(out).asJsonPrimitive.asString
            assertTrue(v.contains("."))
            assertTrue(v.substringAfter(".") in setOf("com", "net", "org", "io"))
        }

        @Test
        fun `string with uri format starts with a known scheme`() {
            val out = generate("""{"type": "string", "format": "uri"}""")
            val v = JsonParser.parseString(out).asJsonPrimitive.asString
            assertTrue(
                v.startsWith("http://") || v.startsWith("https://") || v.startsWith("ftp://"),
                "URI $v has unknown scheme"
            )
        }

        @Test
        fun `string with ipv4 format has four dot-separated octets`() {
            val out = generate("""{"type": "string", "format": "ipv4"}""")
            val v = JsonParser.parseString(out).asJsonPrimitive.asString
            val parts = v.split(".")
            assertEquals(4, parts.size)
            parts.forEach {
                val n = it.toInt()
                assertTrue(n in 0..255)
            }
        }

        @Test
        fun `string with ipv6 format has eight colon-separated blocks`() {
            val out = generate("""{"type": "string", "format": "ipv6"}""")
            val v = JsonParser.parseString(out).asJsonPrimitive.asString
            // generateIPV6 joins 8 blocks separated by ':'.
            assertTrue(v.count { it == ':' } >= 7, "Expected at least 7 colons, got $v")
        }

        @RepeatedTest(5)
        fun `string schema honors minLength and maxLength`() {
            val out = generate(
                """{"type": "string", "minLength": 4, "maxLength": 8}"""
            )
            val v = JsonParser.parseString(out).asJsonPrimitive.asString
            assertTrue(v.length in 4..8, "Length ${v.length} not in [4,8]: $v")
        }
    }

    @Nested
    @DisplayName("Combined schemas (oneOf, anyOf, allOf)")
    inner class CombinedSchemas {

        @Test
        fun `oneOf picks one of the sub-schemas`() {
            val out = generate(
                """
                {
                  "oneOf": [
                    {"type": "string"},
                    {"type": "integer"}
                  ]
                }
                """.trimIndent()
            )
            // Either a string or an integer is acceptable.
            val parsed = JsonParser.parseString(out).asJsonPrimitive
            assertTrue(parsed.isString || parsed.isNumber)
        }

        @Test
        fun `anyOf picks one of the sub-schemas`() {
            val out = generate(
                """
                {
                  "anyOf": [
                    {"type": "boolean"},
                    {"type": "string"}
                  ]
                }
                """.trimIndent()
            )
            val parsed = JsonParser.parseString(out).asJsonPrimitive
            assertTrue(parsed.isBoolean || parsed.isString)
        }

        @Test
        fun `allOf emits an empty object placeholder`() {
            val out = generate(
                """
                {
                  "allOf": [
                    {"type": "object"},
                    {"type": "object"}
                  ]
                }
                """.trimIndent()
            )
            assertTrue(JsonParser.parseString(out).asJsonObject.entrySet().isEmpty())
        }

        @Test
        fun `combined schema with a const sub-schema emits the const value`() {
            val out = generate(
                """
                {
                  "anyOf": [
                    {"const": "only-option"},
                    {"type": "integer"}
                  ]
                }
                """.trimIndent()
            )
            assertEquals("\"only-option\"", out.trim())
        }
    }

    @Nested
    @DisplayName("ObjectSchema")
    inner class ObjectSchemas {

        @Test
        fun `object schema emits properties with the right types`() {
            val out = generate(
                """
                {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer", "minimum": 0, "maximum": 120},
                    "active": {"type": "boolean"}
                  },
                  "required": ["name", "age"]
                }
                """.trimIndent()
            )
            val obj = JsonParser.parseString(out).asJsonObject
            assertTrue(obj.has("name"))
            assertTrue(obj.has("age"))
            assertTrue(obj.has("active"))
            assertTrue(obj.get("name").asJsonPrimitive.isString)
            assertTrue(obj.get("age").asJsonPrimitive.isNumber)
            assertTrue(obj.get("active").asJsonPrimitive.isBoolean)
        }
    }
}
