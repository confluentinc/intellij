package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.core.util.ConnectionUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@TestApplication
class ConfluentCloudTabActionTest {

    // Kafka connection tabs use the connection's innerId (a UUID) as their CONNECTION_ID
    private val kafkaConnectionId = "550e8400-e29b-41d4-a716-446655440000"

    private val disposable = Disposer.newDisposable("ConfluentCloudTabActionTest")
    private lateinit var mockAuthService: CCloudAuthService
    private val action = ConfluentCloudTabAction()

    @BeforeEach
    fun setUp() {
        mockAuthService = mock()
        ApplicationManager.getApplication()
            .replaceService(CCloudAuthService::class.java, mockAuthService, disposable)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    private fun eventWithConnectionId(id: String?) =
        TestActionEvent.createTestEvent(action) { key ->
            if (key == ConnectionUtil.CONNECTION_ID.name) id else null
        }

    @Nested
    @DisplayName("update")
    inner class Update {

        @Test
        fun `should hide action when tab is a kafka connection`() {
            val event = eventWithConnectionId(kafkaConnectionId)

            action.update(event)

            assertFalse(event.presentation.isVisible)
            assertFalse(event.presentation.isEnabled)
        }

        @Test
        fun `should hide action when connection id is null`() {
            val event = eventWithConnectionId(null)

            action.update(event)

            assertFalse(event.presentation.isVisible)
        }

        @Test
        fun `should show sign-in text and user icon when not signed in`() {
            val event = eventWithConnectionId("ccloud")
            whenever(mockAuthService.isSignedIn()) doReturn false

            action.update(event)

            assertEquals("Sign in", event.presentation.text)
            assertEquals(AllIcons.General.User, event.presentation.icon)
        }

        @Test
        fun `should show sign-out text and exit icon when signed in`() {
            val event = eventWithConnectionId("ccloud")
            whenever(mockAuthService.isSignedIn()) doReturn true

            action.update(event)

            assertEquals("Sign out", event.presentation.text)
            assertEquals(AllIcons.Actions.Exit, event.presentation.icon)
        }
    }

    @Nested
    @DisplayName("actionPerformed")
    inner class ActionPerformed {

        @Test
        fun `should do nothing when tab is a kafka connection`() {
            val event = eventWithConnectionId(kafkaConnectionId)

            action.actionPerformed(event)

            verify(mockAuthService, never()).signIn(any())
            verify(mockAuthService, never()).signOut(any(), any())
        }

        @Test
        fun `should call signIn when not signed in`() {
            val event = eventWithConnectionId("ccloud")
            whenever(mockAuthService.isSignedIn()) doReturn false

            action.actionPerformed(event)

            verify(mockAuthService).signIn("tool_window_action")
            verify(mockAuthService, never()).signOut(any(), any())
        }

        @Test
        fun `should call signOut when signed in`() {
            val event = eventWithConnectionId("ccloud")
            whenever(mockAuthService.isSignedIn()) doReturn true

            action.actionPerformed(event)

            verify(mockAuthService).signOut(invokedPlace = "tool_window_action")
            verify(mockAuthService, never()).signIn(any())
        }
    }
}
