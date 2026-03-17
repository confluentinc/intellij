package io.confluent.intellijplugin.core.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@TestApplication
class CCloudSignInOutActionTest {

    private lateinit var disposable: Disposable
    private lateinit var mockAuthService: CCloudAuthService

    @BeforeEach
    fun setUp() {
        disposable = Disposer.newDisposable("CCloudSignInOutActionTest")
        mockAuthService = mock()
        ApplicationManager.getApplication()
            .replaceService(CCloudAuthService::class.java, mockAuthService, disposable)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    private fun actionWithCCloudSelected(selected: Boolean = true) =
        CCloudSignInOutAction { selected }

    private fun createEvent(action: CCloudSignInOutAction) =
        TestActionEvent.createTestEvent(action)

    @Nested
    @DisplayName("update")
    inner class Update {

        @Test
        fun `should hide action when CCloud node is not selected`() {
            val action = actionWithCCloudSelected(false)
            val event = createEvent(action)

            action.update(event)

            assertFalse(event.presentation.isEnabledAndVisible)
        }

        @Test
        fun `should show action when CCloud node is selected`() {
            val action = actionWithCCloudSelected(true)
            val event = createEvent(action)

            action.update(event)

            assertTrue(event.presentation.isVisible)
        }

        @Test
        fun `should show sign-in text and user icon when not signed in`() {
            val action = actionWithCCloudSelected(true)
            val event = createEvent(action)
            whenever(mockAuthService.isSignedIn()) doReturn false

            action.update(event)

            assertEquals(
                KafkaMessagesBundle.message("confluent.cloud.welcome.panel.cta"),
                event.presentation.text,
            )
            assertEquals(AllIcons.General.User, event.presentation.icon)
        }

        @Test
        fun `should show sign-out text and exit icon when signed in`() {
            val action = actionWithCCloudSelected(true)
            val event = createEvent(action)
            whenever(mockAuthService.isSignedIn()) doReturn true

            action.update(event)

            assertEquals(
                KafkaMessagesBundle.message("confluent.cloud.settings.sign.out"),
                event.presentation.text,
            )
            assertEquals(AllIcons.Actions.Exit, event.presentation.icon)
        }
    }

    @Nested
    @DisplayName("actionPerformed")
    inner class ActionPerformed {

        @Test
        fun `should call signIn when not signed in`() {
            val action = actionWithCCloudSelected(true)
            val event = createEvent(action)
            whenever(mockAuthService.isSignedIn()) doReturn false

            action.actionPerformed(event)

            verify(mockAuthService).signIn(invokedPlace = "settings_panel")
        }

        @Test
        fun `should call signOut when signed in`() {
            val action = actionWithCCloudSelected(true)
            val event = createEvent(action)
            whenever(mockAuthService.isSignedIn()) doReturn true

            action.actionPerformed(event)

            verify(mockAuthService).signOut(invokedPlace = "settings_panel")
        }

        @Test
        fun `should not toggle auth when CCloud node is not selected`() {
            val action = actionWithCCloudSelected(false)
            val event = createEvent(action)

            action.actionPerformed(event)

            verifyNoInteractions(mockAuthService)
        }
    }
}
