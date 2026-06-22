package io.confluent.intellijplugin.common.editor

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.models.KafkaFieldType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class FieldViewerResolverTest {

    @Nested
    inner class `detectAutoType` {

        @Test
        fun `should detect JSON for a JSON-looking STRING`() {
            assertEquals(
                FieldViewerType.JSON,
                FieldViewerResolver.detectAutoType(KafkaFieldType.STRING, """{"a":1}""")
            )
        }

        @Test
        fun `should detect TEXT for a non-JSON STRING`() {
            assertEquals(
                FieldViewerType.TEXT,
                FieldViewerResolver.detectAutoType(KafkaFieldType.STRING, "plain value")
            )
        }

        @Test
        fun `should always detect JSON for JSON field type regardless of text`() {
            assertEquals(FieldViewerType.JSON, FieldViewerResolver.detectAutoType(KafkaFieldType.JSON, "not json"))
        }

        @Test
        fun `should detect TEXT for numeric field types`() {
            listOf(
                KafkaFieldType.LONG,
                KafkaFieldType.INTEGER,
                KafkaFieldType.DOUBLE,
                KafkaFieldType.FLOAT,
                KafkaFieldType.NULL
            ).forEach { type ->
                assertEquals(FieldViewerType.TEXT, FieldViewerResolver.detectAutoType(type, "123"), "type=$type")
            }
        }

        @Test
        fun `should detect DECODED_BASE64 for BASE64 field type`() {
            assertEquals(
                FieldViewerType.DECODED_BASE64,
                FieldViewerResolver.detectAutoType(KafkaFieldType.BASE64, "aGVsbG8=")
            )
        }

        @Test
        fun `should detect JSON for registry and custom schema field types`() {
            listOf(
                KafkaFieldType.SCHEMA_REGISTRY,
                KafkaFieldType.PROTOBUF_CUSTOM,
                KafkaFieldType.AVRO_CUSTOM
            ).forEach { type ->
                assertEquals(FieldViewerType.JSON, FieldViewerResolver.detectAutoType(type, ""), "type=$type")
            }
        }
    }

    @Nested
    inner class `resolve` {

        @Test
        fun `should honor an explicit non-AUTO selection`() {
            assertEquals(
                FieldViewerType.TEXT,
                FieldViewerResolver.resolve(FieldViewerType.TEXT, KafkaFieldType.JSON, """{"a":1}""")
            )
        }

        @Test
        fun `should auto-detect when selection is AUTO`() {
            assertEquals(
                FieldViewerType.JSON,
                FieldViewerResolver.resolve(FieldViewerType.AUTO, KafkaFieldType.STRING, """{"a":1}""")
            )
        }
    }

    @Nested
    inner class `link and json predicates` {

        @Test
        fun `load file link is visible only for base64 decode view`() {
            assertTrue(FieldViewerResolver.isLoadFileLinkVisible(FieldViewerType.DECODED_BASE64))
            assertFalse(FieldViewerResolver.isLoadFileLinkVisible(FieldViewerType.JSON))
            assertFalse(FieldViewerResolver.isLoadFileLinkVisible(FieldViewerType.TEXT))
        }

        @Test
        fun `json view is true only for JSON`() {
            assertTrue(FieldViewerResolver.isJsonView(FieldViewerType.JSON))
            assertFalse(FieldViewerResolver.isJsonView(FieldViewerType.TEXT))
            assertFalse(FieldViewerResolver.isJsonView(FieldViewerType.DECODED_BASE64))
        }
    }
}
