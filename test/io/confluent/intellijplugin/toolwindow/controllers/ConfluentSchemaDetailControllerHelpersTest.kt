package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class ConfluentSchemaDetailControllerHelpersTest {

    @Nested
    @DisplayName("applyVersionsState")
    inner class ApplyVersionsState {

        @Test
        fun `null versions keeps loading and clears content + error`() {
            val state = freshState(isLoading = false, hasContent = true, hasError = true, isEditMode = true)
            var selected: Long? = null

            applyVersionsState(null, state.isLoading, state.hasContent, state.hasError, state.isEditMode) { selected = it }

            assertTrue(state.isLoading.get())
            assertFalse(state.hasContent.get())
            assertFalse(state.hasError.get())
            assertFalse(state.isEditMode.get())
            assertNull(selected)
        }

        @Test
        fun `empty versions flips to error state`() {
            val state = freshState(isLoading = true, hasContent = true, hasError = false, isEditMode = true)
            var selected: Long? = null

            applyVersionsState(emptyList(), state.isLoading, state.hasContent, state.hasError, state.isEditMode) { selected = it }

            assertFalse(state.isLoading.get())
            assertFalse(state.hasContent.get())
            assertTrue(state.hasError.get())
            assertFalse(state.isEditMode.get())
            assertNull(selected)
        }

        @Test
        fun `single version selects first and disables edit mode`() {
            val state = freshState()
            var selected: Long? = null

            applyVersionsState(listOf(7L), state.isLoading, state.hasContent, state.hasError, state.isEditMode) { selected = it }

            assertEquals(7L, selected)
            assertFalse(state.isEditMode.get())
        }

        @Test
        fun `multiple versions selects first and enables edit mode`() {
            val state = freshState()
            var selected: Long? = null

            applyVersionsState(listOf(3L, 2L, 1L), state.isLoading, state.hasContent, state.hasError, state.isEditMode) { selected = it }

            assertEquals(3L, selected)
            assertTrue(state.isEditMode.get())
        }
    }

    @Nested
    @DisplayName("shouldRunForSchema")
    inner class ShouldRunForSchema {

        @Test
        fun `returns true when expected matches current and not disposed`() {
            assertTrue(shouldRunForSchema("user", "user", isDisposed = false))
        }

        @Test
        fun `returns false when disposed`() {
            assertFalse(shouldRunForSchema("user", "user", isDisposed = true))
        }

        @Test
        fun `returns false when current schema differs`() {
            assertFalse(shouldRunForSchema("user", "address", isDisposed = false))
        }

        @Test
        fun `returns false when current schema is null`() {
            assertFalse(shouldRunForSchema("user", null, isDisposed = false))
        }
    }

    private data class State(
        val isLoading: AtomicBooleanProperty,
        val hasContent: AtomicBooleanProperty,
        val hasError: AtomicBooleanProperty,
        val isEditMode: AtomicBooleanProperty
    )

    private fun freshState(
        isLoading: Boolean = false,
        hasContent: Boolean = false,
        hasError: Boolean = false,
        isEditMode: Boolean = false
    ) = State(
        AtomicBooleanProperty(isLoading),
        AtomicBooleanProperty(hasContent),
        AtomicBooleanProperty(hasError),
        AtomicBooleanProperty(isEditMode)
    )
}
