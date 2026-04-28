package io.confluent.intellijplugin.util.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

@DisplayName("PrimitivesGenerator")
class PrimitivesGeneratorTest {

    @Nested
    @DisplayName("generateString")
    inner class GenerateString {

        @RepeatedTest(20)
        fun `should produce length within range with default bounds`() {
            val s = PrimitivesGenerator.generateString()
            assertTrue(s.length in 1..20, "length=${s.length}")
        }

        @RepeatedTest(20)
        fun `should respect explicit min and max`() {
            val s = PrimitivesGenerator.generateString(minLength = 5, maxLength = 10)
            assertTrue(s.length in 4..10, "length=${s.length}")
        }

        @RepeatedTest(20)
        fun `should contain only letters when requireLetters is true`() {
            val s = PrimitivesGenerator.generateString(requireLetters = true)
            assertTrue(s.all { it.isLetter() }, "Got: $s")
        }

        @RepeatedTest(20)
        fun `should allow letters and digits when requireLetters is false`() {
            val s = PrimitivesGenerator.generateString(requireLetters = false)
            assertTrue(s.all { it.isLetterOrDigit() }, "Got: $s")
        }
    }

    @Nested
    @DisplayName("generateLong / generateInt")
    inner class NumericRanges {

        @RepeatedTest(50)
        fun `generateLong should be within the specified range`() {
            val v = PrimitivesGenerator.generateLong(from = 100L, until = 200L)
            assertTrue(v in 100L..199L, "v=$v")
        }

        @RepeatedTest(50)
        fun `generateInt should be within the specified range`() {
            val v = PrimitivesGenerator.generateInt(from = 0, until = 10)
            assertTrue(v in 0..9, "v=$v")
        }

        @Test
        fun `generateInt should fall back to default range when args are null`() {
            val v = PrimitivesGenerator.generateInt(from = null, until = null)
            assertTrue(v >= Int.MIN_VALUE && v < Int.MAX_VALUE)
        }
    }

    @Nested
    @DisplayName("generateDouble / generateFloat")
    inner class FloatingPoint {

        @RepeatedTest(50)
        fun `generateDouble should be within range`() {
            val v = PrimitivesGenerator.generateDouble(from = -1.0, until = 1.0)
            assertTrue(v in -1.0..1.0, "v=$v")
        }

        @RepeatedTest(50)
        fun `generateFloat should be within range`() {
            val v = PrimitivesGenerator.generateFloat(from = 0f, until = 100f)
            assertTrue(v in 0f..100f, "v=$v")
        }
    }

    @Nested
    @DisplayName("generateBytes / generateBytesBase64")
    inner class Bytes {

        @RepeatedTest(20)
        fun `generateBytes should respect size range`() {
            val bytes = PrimitivesGenerator.generateBytes(minSize = 5, maxSize = 15)
            assertTrue(bytes.size in 5..14, "size=${bytes.size}")
        }

        @RepeatedTest(20)
        fun `generateBytesBase64 should produce decodable output`() {
            val s = PrimitivesGenerator.generateBytesBase64(minSize = 5, maxSize = 15)
            val decoded = Base64.getDecoder().decode(s)
            assertTrue(decoded.size in 5..14, "decoded.size=${decoded.size}")
        }
    }

    @Nested
    @DisplayName("generateUUID")
    inner class Uuid {

        @Test
        fun `should produce a valid UUID`() {
            val uuid = PrimitivesGenerator.generateUUID()
            assertNotNull(uuid)
            assertEquals(uuid, UUID.fromString(uuid.toString()))
        }

        @Test
        fun `should produce different values on subsequent calls`() {
            assertNotEquals(PrimitivesGenerator.generateUUID(), PrimitivesGenerator.generateUUID())
        }
    }

    @Nested
    @DisplayName("generateEmail")
    inner class Email {

        private val knownDomains = setOf("com", "org", "nl", "cz", "de", "io", "cy", "us", "uk", "cn", "it")

        @RepeatedTest(20)
        fun `should produce a parseable email with a known top-level domain`() {
            val email = PrimitivesGenerator.generateEmail()
            val parts = email.split("@")
            assertEquals(2, parts.size, "Expected single @ in $email")
            val (user, host) = parts
            assertTrue(user.isNotEmpty(), "user empty in $email")
            assertTrue(host.contains("."), "host missing dot in $email")
            val tld = host.substringAfterLast(".")
            assertTrue(tld in knownDomains, "TLD '$tld' not in known set")
        }
    }

    @Nested
    @DisplayName("createTimestamp / createIsoTimestamp")
    inner class Timestamps {

        @Test
        fun `createTimestamp should return seconds-precision epoch close to now`() {
            val before = Instant.now().epochSecond
            val ts = PrimitivesGenerator.createTimestamp().toLong()
            val after = Instant.now().epochSecond
            assertTrue(ts in before..after, "ts=$ts not in [$before,$after]")
        }

        @Test
        fun `createIsoTimestamp should be parseable as Instant`() {
            val s = PrimitivesGenerator.createIsoTimestamp()
            val instant = Instant.parse(s)
            assertNotNull(instant)
        }
    }

    @Nested
    @DisplayName("createHex")
    inner class Hex {

        @RepeatedTest(20)
        fun `should produce only hex characters`() {
            val hex = PrimitivesGenerator.createHex(16)
            assertEquals(16, hex.length)
            assertTrue(hex.all { it in '0'..'9' || it in 'A'..'F' }, "Got: $hex")
        }

        @Test
        fun `should handle length 1`() {
            val hex = PrimitivesGenerator.createHex(1)
            assertEquals(1, hex.length)
            assertTrue(hex[0] in '0'..'9' || hex[0] in 'A'..'F')
        }
    }

    @Nested
    @DisplayName("createAlphaNum / generateAlphabetic")
    inner class StringSequences {

        @RepeatedTest(20)
        fun `createAlphaNum should produce only letters digits and underscore`() {
            val s = PrimitivesGenerator.createAlphaNum(20)
            assertEquals(20, s.length)
            assertTrue(s.all { it.isLetterOrDigit() || it == '_' }, "Got: $s")
        }

        @RepeatedTest(20)
        fun `generateAlphabetic should produce only letters`() {
            val s = PrimitivesGenerator.generateAlphabetic(15)
            assertEquals(15, s.length)
            assertTrue(s.all { it.isLetter() }, "Got: $s")
        }
    }

    @Nested
    @DisplayName("generateBoolean")
    inner class Booleans {

        @Test
        fun `should produce both true and false across many calls`() {
            val results = (1..200).map { PrimitivesGenerator.generateBoolean() }.toSet()
            assertEquals(setOf(true, false), results)
        }
    }
}
