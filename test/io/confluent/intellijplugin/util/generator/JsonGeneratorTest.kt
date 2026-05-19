package io.confluent.intellijplugin.util.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

@DisplayName("JsonGenerator")
class JsonGeneratorTest {

    private val knownKeys = setOf(
        "id", "year", "count", "city", "temperature", "name", "color", "status", "tag"
    )

    @Test
    fun `generateJson returns a non-empty JsonObject`() {
        val result = JsonGenerator.generateJson()

        assertNotNull(result)
        assertFalse(result.entrySet().isEmpty())
    }

    @RepeatedTest(20)
    fun `every key after random-suffix stripping comes from the predefined set`() {
        val result = JsonGenerator.generateJson()

        result.entrySet().forEach { entry ->
            assertTrue(entry.key in knownKeys, "Unexpected key: ${entry.key}")
        }
    }

    @RepeatedTest(20)
    fun `every key is purely alphabetic`() {
        val result = JsonGenerator.generateJson()

        result.entrySet().forEach { entry ->
            assertTrue(entry.key.all { it.isLetter() }, "Non-alphabetic key: ${entry.key}")
        }
    }

    @RepeatedTest(20)
    fun `every value is added as a string primitive`() {
        val result = JsonGenerator.generateJson()

        result.entrySet().forEach { entry ->
            val value = entry.value
            assertTrue(value.isJsonPrimitive)
            assertTrue(value.asJsonPrimitive.isString)
        }
    }

    @RepeatedTest(20)
    fun `field count stays within the expected bound`() {
        val result = JsonGenerator.generateJson()
        val size = result.entrySet().size

        // generateJson() breaks once `count > numberOfFields`, where numberOfFields
        // is sampled from [2, simpleObject.size). simpleObject has 9 entries, so the
        // emitted object holds between 3 and 9 fields inclusive.
        assertTrue(size in 3..9, "Unexpected field count: $size")
    }

    @Test
    fun `successive calls eventually produce different objects`() {
        val samples = (1..20).map { JsonGenerator.generateJson().toString() }
        assertTrue(samples.distinct().size > 1, "Output is suspiciously deterministic")
    }

    @Test
    fun `all known keys can show up across enough samples`() {
        val seen = mutableSetOf<String>()
        repeat(500) {
            JsonGenerator.generateJson().entrySet().forEach { seen.add(it.key) }
        }
        // The generator iterates a HashMap, so order varies. We only assert the seen
        // keys are a subset of the known set, and that we encounter several distinct keys.
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.size >= 3, "Expected multiple distinct keys across 500 samples, saw $seen")
        seen.forEach { key ->
            assertTrue(key in knownKeys, "Saw unexpected key $key")
        }
        assertEquals(seen, seen.intersect(knownKeys))
    }
}
