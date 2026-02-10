package io.confluent.intellijplugin.consumer.editor

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

@TestApplication
class ConsumerEditorUtilsTest {

    @Nested
    @DisplayName("parsePartitionsText")
    inner class ParsePartitionsText {

        @Test
        fun `should return empty list for null input`() {
            val result = ConsumerEditorUtils.parsePartitionsText(null)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list for blank input`() {
            val result = ConsumerEditorUtils.parsePartitionsText("")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should parse single partition number`() {
            val result = ConsumerEditorUtils.parsePartitionsText("3")
            assertEquals(listOf(3), result)
        }

        @Test
        fun `should parse comma-separated partition numbers`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0,1,2")
            assertEquals(listOf(0, 1, 2), result)
        }

        @Test
        fun `should parse range notation`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0-5")
            assertEquals(listOf(0, 1, 2, 3, 4, 5), result)
        }

        @Test
        fun `should parse mixed single and range notation`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0,2-4,7")
            assertEquals(listOf(0, 2, 3, 4, 7), result)
        }

        @Test
        fun `should ignore whitespace`() {
            val result = ConsumerEditorUtils.parsePartitionsText(" 0 , 2 - 4 , 7 ")
            assertEquals(listOf(0, 2, 3, 4, 7), result)
        }

        @Test
        fun `should ignore non-numeric entries`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0,abc,2")
            assertEquals(listOf(0, 2), result)
        }

        @Test
        fun `should handle invalid range gracefully`() {
            val result = ConsumerEditorUtils.parsePartitionsText("a-b")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should handle single element range`() {
            val result = ConsumerEditorUtils.parsePartitionsText("3-3")
            assertEquals(listOf(3), result)
        }
    }

    @Nested
    @DisplayName("getStartWith")
    inner class GetStartWith {

        @Test
        fun `should create OFFSET start with positive offset`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.OFFSET, "100", null, null
            )
            assertEquals(ConsumerStartType.OFFSET, result.type)
            assertEquals(100L, result.offset)
            assertNull(result.time)
        }

        @Test
        fun `should create LATEST_OFFSET_MINUS_X with negated offset`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.LATEST_OFFSET_MINUS_X, "50", null, null
            )
            assertEquals(ConsumerStartType.LATEST_OFFSET_MINUS_X, result.type)
            assertEquals(-50L, result.offset)
            assertNull(result.time)
        }

        @Test
        fun `should create THE_BEGINNING with offset 0`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.THE_BEGINNING, "", null, null
            )
            assertEquals(ConsumerStartType.THE_BEGINNING, result.type)
            assertEquals(0L, result.offset)
            assertNull(result.time)
        }

        @Test
        fun `should create SPECIFIC_DATE with timestamp from Date`() {
            val date = Date(1700000000000L)
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.SPECIFIC_DATE, "", date, null
            )
            assertEquals(ConsumerStartType.SPECIFIC_DATE, result.type)
            assertEquals(1700000000000L, result.time)
            assertNull(result.offset)
        }

        @Test
        fun `should set null time for non-SPECIFIC_DATE types`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.NOW, "", null, null
            )
            assertEquals(ConsumerStartType.NOW, result.type)
            assertNull(result.time)
            assertNull(result.offset)
        }

        @Test
        fun `should pass through consumer group string`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.CONSUMER_GROUP, "", null, "my-group"
            )
            assertEquals(ConsumerStartType.CONSUMER_GROUP, result.type)
            assertEquals("my-group", result.consumerGroup)
        }

        @Test
        fun `should handle blank offset text as null offset`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.OFFSET, "", null, null
            )
            assertEquals(ConsumerStartType.OFFSET, result.type)
            assertNull(result.offset)
        }

        @Test
        fun `should handle non-numeric offset text as null`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.OFFSET, "abc", null, null
            )
            assertEquals(ConsumerStartType.OFFSET, result.type)
            assertNull(result.offset)
        }
    }
}
