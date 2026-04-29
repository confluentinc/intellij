package io.confluent.intellijplugin.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ValidationUtils")
class ValidationUtilsTest {

    @Test
    fun `labelToName should strip a single trailing colon`() {
        assertEquals("Hostname", labelToName("Hostname:"))
    }

    @Test
    fun `labelToName should leave a label without trailing colon untouched`() {
        assertEquals("Hostname", labelToName("Hostname"))
    }

    @Test
    fun `labelToName should only strip the final colon, not internal ones`() {
        assertEquals("Foo: Bar", labelToName("Foo: Bar:"))
    }

    @Test
    fun `labelToName should return empty string for empty input`() {
        assertEquals("", labelToName(""))
    }

    @Test
    fun `labelToName should return empty string for input that is just a colon`() {
        assertEquals("", labelToName(":"))
    }
}
