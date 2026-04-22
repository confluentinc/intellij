package io.confluent.intellijplugin.consumer.search

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SearchBarControllerTest {

    @Nested
    inner class SearchKeyMap {

        @Test
        fun `consumer map has offset at index 5`() {
            val map = SearchBarController.searchKeyMap(isProducer = false)
            assertEquals(5, map["offset"])
            assertNull(map["duration"])
        }

        @Test
        fun `producer map has duration at index 5`() {
            val map = SearchBarController.searchKeyMap(isProducer = true)
            assertEquals(5, map["duration"])
            assertNull(map["offset"])
        }

        @Test
        fun `shared columns have same model indices across consumer and producer`() {
            val consumer = SearchBarController.searchKeyMap(isProducer = false)
            val producer = SearchBarController.searchKeyMap(isProducer = true)
            listOf("topic", "timestamp", "key", "value", "partition").forEach { col ->
                assertEquals(consumer[col], producer[col], "mismatch for column '$col'")
            }
        }

        @Test
        fun `all search keys map to expected model indices`() {
            val map = SearchBarController.searchKeyMap(isProducer = false)
            assertEquals(0, map["topic"])
            assertEquals(1, map["timestamp"])
            assertEquals(2, map["key"])
            assertEquals(3, map["value"])
            assertEquals(4, map["partition"])
            assertEquals(5, map["offset"])
        }
    }
}
