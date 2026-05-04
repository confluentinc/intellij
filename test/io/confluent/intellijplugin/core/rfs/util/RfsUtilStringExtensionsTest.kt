package io.confluent.intellijplugin.core.rfs.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RfsUtil string extensions")
class RfsUtilStringExtensionsTest {

    @Nested
    @DisplayName("withSlash")
    inner class WithSlash {

        @Test
        fun `should append slash when missing`() {
            assertEquals("path/", "path".withSlash())
        }

        @Test
        fun `should not double up an existing trailing slash`() {
            assertEquals("path/", "path/".withSlash())
        }

        @Test
        fun `should leave empty string untouched`() {
            assertEquals("", "".withSlash())
        }

        @Test
        fun `should append slash to multi-segment path`() {
            assertEquals("a/b/c/", "a/b/c".withSlash())
        }
    }

    @Nested
    @DisplayName("withPrefixSlash")
    inner class WithPrefixSlash {

        @Test
        fun `should prepend slash when missing`() {
            assertEquals("/path", "path".withPrefixSlash())
        }

        @Test
        fun `should not double up an existing leading slash`() {
            assertEquals("/path", "/path".withPrefixSlash())
        }

        @Test
        fun `should produce single slash from empty string`() {
            assertEquals("/", "".withPrefixSlash())
        }

        @Test
        fun `should normalize regardless of trailing slash`() {
            assertEquals("/path/", "path/".withPrefixSlash())
        }
    }
}
