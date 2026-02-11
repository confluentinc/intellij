package io.confluent.intellijplugin.util

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.consumer.models.ConsumerStartWith
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

@TestApplication
class KafkaOffsetUtilsTest {

    @Nested
    @DisplayName("calculateStartTime")
    inner class CalculateStartTime {

        @Test
        fun `should return null for NOW`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.NOW, time = null, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNull(result)
        }

        @Test
        fun `should return timestamp approximately 1 hour ago for LAST_HOUR`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.LAST_HOUR, time = null, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNotNull(result)
            val expectedTime = System.currentTimeMillis() - 3600000L
            val tolerance = 2000L
            assertTrue(
                kotlin.math.abs(result!! - expectedTime) <= tolerance,
                "Expected timestamp approximately $expectedTime, but got $result (diff: ${kotlin.math.abs(result - expectedTime)}ms)"
            )
        }

        @Test
        fun `should return midnight timestamp for TODAY`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.TODAY, time = null, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNotNull(result)
            val resultCalendar = Calendar.getInstance()
            resultCalendar.timeInMillis = result!!
            val expectedCalendar = Calendar.getInstance()
            expectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
            expectedCalendar.set(Calendar.MINUTE, 0)
            expectedCalendar.set(Calendar.SECOND, 0)
            expectedCalendar.set(Calendar.MILLISECOND, 0)
            assertEquals(expectedCalendar.get(Calendar.YEAR), resultCalendar.get(Calendar.YEAR))
            assertEquals(expectedCalendar.get(Calendar.DAY_OF_YEAR), resultCalendar.get(Calendar.DAY_OF_YEAR))
            assertEquals(0, resultCalendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, resultCalendar.get(Calendar.MINUTE))
            assertEquals(0, resultCalendar.get(Calendar.SECOND))
        }

        @Test
        fun `should return yesterday midnight timestamp for YESTERDAY`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.YESTERDAY, time = null, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNotNull(result)
            val resultCalendar = Calendar.getInstance()
            resultCalendar.timeInMillis = result!!
            val expectedCalendar = Calendar.getInstance()
            expectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
            expectedCalendar.set(Calendar.MINUTE, 0)
            expectedCalendar.set(Calendar.SECOND, 0)
            expectedCalendar.set(Calendar.MILLISECOND, 0)
            expectedCalendar.add(Calendar.DAY_OF_YEAR, -1)
            assertEquals(expectedCalendar.get(Calendar.YEAR), resultCalendar.get(Calendar.YEAR))
            assertEquals(expectedCalendar.get(Calendar.DAY_OF_YEAR), resultCalendar.get(Calendar.DAY_OF_YEAR))
            assertEquals(0, resultCalendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, resultCalendar.get(Calendar.MINUTE))
            assertEquals(0, resultCalendar.get(Calendar.SECOND))
        }

        @Test
        fun `should return provided time for SPECIFIC_DATE`() {
            val specificTime = 1700000000000L
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.SPECIFIC_DATE, time = specificTime, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertEquals(specificTime, result)
        }

        @Test
        fun `should return null for OFFSET type`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.OFFSET, time = null, offset = 100L, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNull(result)
        }

        @Test
        fun `should return null for LATEST_OFFSET_MINUS_X type`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.LATEST_OFFSET_MINUS_X, time = null, offset = 50L, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNull(result)
        }

        @Test
        fun `should return null for THE_BEGINNING type`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.THE_BEGINNING, time = null, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNull(result)
        }

        @Test
        fun `should return null for CONSUMER_GROUP type`() {
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.CONSUMER_GROUP, time = null, offset = null, consumerGroup = "test-group"
            )
            val result = KafkaOffsetUtils.calculateStartTime(startWith)
            assertNull(result)
        }
    }
}
