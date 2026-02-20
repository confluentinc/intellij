package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@TestApplication
class ConfluentTabControllerTest {

    private val disposable = Disposer.newDisposable("ConfluentTabControllerTest")
    private lateinit var mockAuthService: CCloudAuthService
    private lateinit var mockProject: Project

    @BeforeEach
    fun setUp() {
        mockAuthService = CCloudAuthService(CoroutineScope(SupervisorJob()))
        mockProject = mock()
        ApplicationManager.getApplication()
            .replaceService(CCloudAuthService::class.java, mockAuthService, disposable)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    @Nested
    @DisplayName("initialization")
    inner class Initialization {

        @Test
        fun `should show sign-in view when not signed in`() {
            val controller = ConfluentTabController(mockProject)
            Disposer.register(disposable, controller)

            val component = controller.getComponent()
            assertNotNull(component)
            assertNull(controller.getDriver(), "Driver should be null when not signed in")
        }

        @Test
        fun `should register as auth state listener`() {
            val controller = ConfluentTabController(mockProject)
            Disposer.register(disposable, controller)

            assertTrue(mockAuthService.authStateListeners.contains(controller))
        }
    }

    @Nested
    @DisplayName("signOut")
    inner class SignOut {

        @Test
        fun `should be safe to call signOut when not signed in`() {
            val controller = ConfluentTabController(mockProject)
            Disposer.register(disposable, controller)

            // Should not throw
            controller.signOut()
            controller.signOut()

            assertNull(controller.getDriver())
        }
    }

    @Nested
    @DisplayName("dispose")
    inner class Dispose {

        @Test
        fun `should remove auth state listener on dispose`() {
            val controller = ConfluentTabController(mockProject)

            assertTrue(mockAuthService.authStateListeners.contains(controller))

            controller.dispose()

            assertFalse(mockAuthService.authStateListeners.contains(controller))
        }
    }

}
