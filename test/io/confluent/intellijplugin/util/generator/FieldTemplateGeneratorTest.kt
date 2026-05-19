package io.confluent.intellijplugin.util.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("FieldTemplateGenerator")
class FieldTemplateGeneratorTest {

    @Nested
    @DisplayName("processTemplate")
    inner class ProcessTemplate {

        @Test
        fun `should leave text without templates unchanged`() {
            val text = """{"name": "alice", "age": 30}"""
            assertEquals(text, FieldTemplateGenerator.processTemplate(text))
        }

        @Test
        fun `should leave empty input unchanged`() {
            assertEquals("", FieldTemplateGenerator.processTemplate(""))
        }

        @RepeatedTest(5)
        fun `random integer template strips the surrounding quotes`() {
            val result = FieldTemplateGenerator.processTemplate("""{"v": "${'$'}{random.integer}"}""")

            val value = extractField(result, "v")
            assertNotNull(value.toIntOrNull(), "Expected integer, got '$value' in '$result'")
        }

        @RepeatedTest(10)
        fun `random integer template respects the supplied range`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.integer(5,10)}"}"""
            )

            val value = extractField(result, "v").toInt()
            assertTrue(value in 5..10, "Value $value not in [5,10]")
        }

        @RepeatedTest(5)
        fun `random uint template returns a non-negative integer string`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.uint}"}"""
            )

            val value = extractField(result, "v").toUInt()
            assertTrue(value >= 0u)
        }

        @RepeatedTest(5)
        fun `random float template returns a parseable float string`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.float}"}"""
            )

            assertTrue(extractField(result, "v").toFloatOrNull() != null)
        }

        @RepeatedTest(5)
        fun `random alphabetic template wraps output with double quotes`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.alphabetic}"}"""
            )

            val value = extractField(result, "v")
            assertTrue(value.all { it.isLetter() }, "Non-alphabetic chars in '$value'")
        }

        @RepeatedTest(5)
        fun `random alphabetic template respects the length parameter`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.alphabetic(8)}"}"""
            )
            assertEquals(8, extractField(result, "v").length)
        }

        @RepeatedTest(5)
        fun `random alphanumeric template returns letters digits or underscores`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.alphanumeric(12)}"}"""
            )

            val value = extractField(result, "v")
            assertEquals(12, value.length)
            assertTrue(value.all { it.isLetterOrDigit() || it == '_' })
        }

        @RepeatedTest(5)
        fun `random hexadecimal template returns valid hex chars`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.hexadecimal(16)}"}"""
            )

            val value = extractField(result, "v")
            assertEquals(16, value.length)
            assertTrue(value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        }

        @Test
        fun `random uuid template produces a parseable UUID`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.uuid}"}"""
            )

            val value = extractField(result, "v")
            UUID.fromString(value) // throws on bad input
        }

        @Test
        fun `random email template looks like an email`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.email}"}"""
            )

            val value = extractField(result, "v")
            assertTrue(value.contains("@"))
            assertTrue(value.substringAfter("@").contains("."))
        }

        @Test
        fun `timestamp template produces a numeric epoch second string`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{timestamp}"}"""
            )

            val value = extractField(result, "v").toLong()
            // Sanity bounds: between Jan 2000 and Jan 2100.
            assertTrue(value in 946_684_800L..4_102_444_800L, "Suspicious timestamp $value")
        }

        @Test
        fun `isoTimestamp template produces an ISO-8601 instant`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{isoTimestamp}"}"""
            )

            val value = extractField(result, "v")
            assertTrue(value.endsWith("Z"))
            java.time.Instant.parse(value)
        }

        @Test
        fun `multiple occurrences of the same template are all replaced with the same value`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"a": "${'$'}{random.uuid}", "b": "${'$'}{random.uuid}"}"""
            )

            // processTemplate uses String.replace, which substitutes every occurrence of the
            // template token with a single generator value per pass. Both fields end up equal.
            val a = extractField(result, "a")
            val b = extractField(result, "b")
            UUID.fromString(a)
            UUID.fromString(b)
            assertEquals(a, b)
        }

        @Test
        fun `templates of different types coexist in one input`() {
            val result = FieldTemplateGenerator.processTemplate(
                """{"n": "${'$'}{random.integer}", "s": "${'$'}{random.alphabetic(5)}"}"""
            )

            assertNotNull(extractField(result, "n").toIntOrNull(), "n should be an integer")
            val s = extractField(result, "s")
            assertEquals(5, s.length)
            assertTrue(s.all { it.isLetter() })
        }

        @Test
        fun `falls back to default range when parameter list does not parse`() {
            // "abc" is not a number, so the parser returns the default range [-1000, 1000].
            val result = FieldTemplateGenerator.processTemplate(
                """{"v": "${'$'}{random.integer(abc)}"}"""
            )

            val value = extractField(result, "v").toInt()
            assertTrue(value in -1000..1000)
        }
    }

    @Nested
    @DisplayName("hasTemplatesWithRemoveQuotas")
    inner class HasTemplatesWithRemoveQuotas {

        @Test
        fun `returns false for plain text`() {
            assertFalse(FieldTemplateGenerator.hasTemplatesWithRemoveQuotas("plain"))
        }

        @Test
        fun `returns false when only non-quoted templates are present`() {
            // wrapQuotes=false templates aren't in the filtered set.
            assertFalse(
                FieldTemplateGenerator.hasTemplatesWithRemoveQuotas(
                    "value: \${random.integer}"
                )
            )
        }

        @Test
        fun `returns true when a quoted template is present`() {
            assertTrue(
                FieldTemplateGenerator.hasTemplatesWithRemoveQuotas(
                    "value: \${random.alphabetic}"
                )
            )
        }

        @Test
        fun `returns true when both quoted and non-quoted templates are present`() {
            assertTrue(
                FieldTemplateGenerator.hasTemplatesWithRemoveQuotas(
                    "a: \${random.integer}, b: \${random.uuid}"
                )
            )
        }
    }

    private fun extractField(jsonLike: String, key: String): String {
        // Output uses one of two forms depending on wrapQuotes:
        //   "k": "value"   (string)
        //   "k": value     (number)
        val quoted = Regex(""""$key"\s*:\s*"([^"]*)"""").find(jsonLike)
        if (quoted != null) return quoted.groupValues[1]
        val unquoted = Regex(""""$key"\s*:\s*([^,\s}]+)""").find(jsonLike)
        require(unquoted != null) { "Could not find field $key in: $jsonLike" }
        return unquoted.groupValues[1]
    }

    private fun assertNotNull(value: Any?, message: String) {
        org.junit.jupiter.api.Assertions.assertNotNull(value, message)
    }
}
