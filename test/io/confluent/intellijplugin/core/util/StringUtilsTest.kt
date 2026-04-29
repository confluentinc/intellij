package io.confluent.intellijplugin.core.util

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@TestApplication
@DisplayName("StringUtils")
class StringUtilsTest {

    @Test
    fun `should split camelCase into capitalized words`() {
        assertEquals("Time To Load", StringUtils.camelCaseToReadableString("timeToLoad"))
    }

    @Test
    fun `should handle single lowercase word`() {
        assertEquals("Name", StringUtils.camelCaseToReadableString("name"))
    }

    @Test
    fun `should return empty string for empty input`() {
        assertEquals("", StringUtils.camelCaseToReadableString(""))
    }

    @Test
    fun `should handle PascalCase input`() {
        assertEquals("Time To Load", StringUtils.camelCaseToReadableString("TimeToLoad"))
    }

    @Test
    fun `should split multi-word camelCase`() {
        assertEquals("First Name Last Name", StringUtils.camelCaseToReadableString("firstNameLastName"))
    }

    @Test
    fun `should keep single character upper-cased`() {
        assertEquals("A", StringUtils.camelCaseToReadableString("a"))
    }
}
