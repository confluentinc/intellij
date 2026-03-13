package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@TestApplication
class CCloudAuthServiceTest {

    private lateinit var parentDisposable: Disposable
    private lateinit var authService: CCloudAuthService

    @BeforeEach
    fun setUp() {
        parentDisposable = Disposer.newDisposable("test")
        authService = CCloudAuthService(CoroutineScope(SupervisorJob()))
    }

    @AfterEach
    fun tearDown() {
        authService.dispose()
        Disposer.dispose(parentDisposable)
    }

    @Nested
    @DisplayName("signOut")
    inner class SignOut {

        @Test
        fun `should clear context when signing out`() {
            authService.context = createMockAuthenticatedContext()

            authService.signOut()

            assertNull(authService.getContext())
            assertFalse(authService.isSignedIn())
        }

        @Test
        fun `should stop refresh bean when signing out`() {
            val mockRefreshBean = mock<CCloudTokenRefreshBean> {
                on { isRunning() } doReturn true
            }
            authService.context = createMockAuthenticatedContext()
            authService.refreshBean = mockRefreshBean

            authService.signOut()

            verify(mockRefreshBean).stop()
            assertNull(authService.refreshBean)
        }

        @Test
        fun `should be idempotent when called multiple times`() {
            authService.context = createMockAuthenticatedContext()

            authService.signOut()
            authService.signOut()

            assertNull(authService.getContext())
            assertFalse(authService.isSignedIn())
        }
    }

    @Nested
    @DisplayName("isSignedIn")
    inner class IsSignedIn {

        @Test
        fun `should return false when context is null`() {
            assertFalse(authService.isSignedIn())
        }

        @Test
        fun `should return false when control plane token is null`() {
            val mockContext = mock<CCloudOAuthContext> {
                on { getControlPlaneToken() } doReturn null
            }
            authService.context = mockContext

            assertFalse(authService.isSignedIn())
        }

        @Test
        fun `should return true when control plane token exists`() {
            val mockToken = Token("test_token", Instant.now().plusSeconds(3600))
            val mockContext = mock<CCloudOAuthContext> {
                on { getControlPlaneToken() } doReturn mockToken
            }
            authService.context = mockContext

            assertTrue(authService.isSignedIn())
        }
    }

    @Nested
    @DisplayName("getUserEmail")
    inner class GetUserEmail {

        @Test
        fun `should return null when not signed in`() {
            assertNull(authService.getUserEmail())
        }

        @Test
        fun `should return email from context when signed in`() {
            val mockContext = mock<CCloudOAuthContext> {
                on { getUserEmail() } doReturn "test@example.com"
            }
            authService.context = mockContext

            assertEquals("test@example.com", authService.getUserEmail())
        }
    }

    @Nested
    @DisplayName("getOrganizationName")
    inner class GetOrganizationName {

        @Test
        fun `should return null when not signed in`() {
            assertNull(authService.getOrganizationName())
        }

        @Test
        fun `should return null when organization is null`() {
            val mockContext = mock<CCloudOAuthContext> {
                on { getCurrentOrganization() } doReturn null
            }
            authService.context = mockContext

            assertNull(authService.getOrganizationName())
        }

        @Test
        fun `should return organization name when signed in`() {
            val mockOrg = OrganizationDetails(
                id = "org-123",
                name = "Test Organization",
                resourceId = "org-res-123",
                sso = SsoDetails(enabled = false, mode = "none", vendor = "none")
            )
            val mockContext = mock<CCloudOAuthContext> {
                on { getCurrentOrganization() } doReturn mockOrg
            }
            authService.context = mockContext

            assertEquals("Test Organization", authService.getOrganizationName())
        }
    }

    @Nested
    @DisplayName("isRefreshRunning")
    inner class IsRefreshRunning {

        @Test
        fun `should return false when refresh bean is null`() {
            assertFalse(authService.isRefreshRunning())
        }

        @Test
        fun `should return false when refresh bean is not running`() {
            val mockRefreshBean = mock<CCloudTokenRefreshBean> {
                on { isRunning() } doReturn false
            }
            authService.refreshBean = mockRefreshBean

            assertFalse(authService.isRefreshRunning())
        }

        @Test
        fun `should return true when refresh bean is running`() {
            val mockRefreshBean = mock<CCloudTokenRefreshBean> {
                on { isRunning() } doReturn true
            }
            authService.refreshBean = mockRefreshBean

            assertTrue(authService.isRefreshRunning())
        }
    }

    @Nested
    @DisplayName("getControlPlaneToken")
    inner class GetControlPlaneToken {

        @Test
        fun `should return null when not signed in`() {
            assertNull(authService.getControlPlaneToken())
        }

        @Test
        fun `should return null when token is null`() {
            val mockContext = mock<CCloudOAuthContext> {
                on { getControlPlaneToken() } doReturn null
            }
            authService.context = mockContext

            assertNull(authService.getControlPlaneToken())
        }

        @Test
        fun `should return token string when available`() {
            val mockToken = Token("cp_token_123", Instant.now().plusSeconds(3600))
            val mockContext = mock<CCloudOAuthContext> {
                on { getControlPlaneToken() } doReturn mockToken
            }
            authService.context = mockContext

            assertEquals("cp_token_123", authService.getControlPlaneToken())
        }
    }

    @Nested
    @DisplayName("getDataPlaneToken")
    inner class GetDataPlaneToken {

        @Test
        fun `should return null when not signed in`() {
            assertNull(authService.getDataPlaneToken())
        }

        @Test
        fun `should return null when token is null`() {
            val mockContext = mock<CCloudOAuthContext> {
                on { getDataPlaneToken() } doReturn null
            }
            authService.context = mockContext

            assertNull(authService.getDataPlaneToken())
        }

        @Test
        fun `should return token string when available`() {
            val mockToken = Token("dp_token_456", Instant.now().plusSeconds(3600))
            val mockContext = mock<CCloudOAuthContext> {
                on { getDataPlaneToken() } doReturn mockToken
            }
            authService.context = mockContext

            assertEquals("dp_token_456", authService.getDataPlaneToken())
        }
    }

    @Nested
    @DisplayName("getContext")
    inner class GetContext {

        @Test
        fun `should return null when not signed in`() {
            assertNull(authService.getContext())
        }

        @Test
        fun `should return context when signed in`() {
            val mockContext = createMockAuthenticatedContext()
            authService.context = mockContext

            assertNotNull(authService.getContext())
            assertEquals(mockContext, authService.getContext())
        }
    }

    @Nested
    @DisplayName("dispose")
    inner class Dispose {

        @Test
        fun `should stop refresh bean on dispose`() {
            val mockRefreshBean = mock<CCloudTokenRefreshBean>()
            authService.refreshBean = mockRefreshBean

            authService.dispose()

            verify(mockRefreshBean).stop()
        }

        @Test
        fun `should clear context on dispose`() {
            authService.context = createMockAuthenticatedContext()

            authService.dispose()

            assertNull(authService.context)
        }

        @Test
        fun `should clear refresh bean on dispose`() {
            val mockRefreshBean = mock<CCloudTokenRefreshBean>()
            authService.refreshBean = mockRefreshBean

            authService.dispose()

            assertNull(authService.refreshBean)
        }

        @Test
        fun `should be safe to dispose when nothing is initialized`() {
            authService.dispose()

            assertNull(authService.getContext())
            assertFalse(authService.isRefreshRunning())
        }
    }

    @Nested
    @DisplayName("getSessionEndOfLifetime")
    inner class GetSessionEndOfLifetime {

        @Test
        fun `should return null when not signed in`() {
            assertNull(authService.getSessionEndOfLifetime())
        }

        @Test
        fun `should return end of lifetime from context`() {
            val expectedInstant = Instant.now().plusSeconds(7200)
            val mockContext = mock<CCloudOAuthContext> {
                on { getEndOfLifetime() } doReturn expectedInstant
            }
            authService.context = mockContext

            assertEquals(expectedInstant, authService.getSessionEndOfLifetime())
        }
    }

    @Nested
    @DisplayName("addAuthStateListener / removeAuthStateListener")
    inner class AuthStateListenerRegistration {

        @Test
        fun `should add listener`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()

            authService.addAuthStateListener(listener)

            assertTrue(authService.authStateListeners.contains(listener))
        }

        @Test
        fun `should remove listener`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()
            authService.addAuthStateListener(listener)

            authService.removeAuthStateListener(listener)

            assertFalse(authService.authStateListeners.contains(listener))
        }

        @Test
        fun `should only remove specified listener`() {
            val listener1 = mock<CCloudAuthService.AuthStateListener>()
            val listener2 = mock<CCloudAuthService.AuthStateListener>()
            authService.addAuthStateListener(listener1)
            authService.addAuthStateListener(listener2)

            authService.removeAuthStateListener(listener1)

            assertFalse(authService.authStateListeners.contains(listener1))
            assertTrue(authService.authStateListeners.contains(listener2))
        }
    }

    @Nested
    @DisplayName("auth state listener and notification dispatch")
    inner class AuthStateListenerDispatch {

        private lateinit var spyService: CCloudAuthService

        @BeforeEach
        fun setUpSpy() {
            spyService = spy(CCloudAuthService(CoroutineScope(SupervisorJob())))
            doNothing().whenever(spyService).showSignOutNotification()
            doNothing().whenever(spyService).showSessionExpiredNotification()
            doNothing().whenever(spyService).showSignInSuccessNotification(any())
        }

        @AfterEach
        fun tearDownSpy() {
            spyService.dispose()
        }

        @Test
        fun `should notify listener and show notification on sign in`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()
            spyService.addAuthStateListener(listener)

            spyService.notifySignedIn("test@example.com")
            SwingUtilities.invokeAndWait {}

            verify(listener).onSignedIn("test@example.com")
            verify(spyService).showSignInSuccessNotification("test@example.com")
        }

        @Test
        fun `should notify listener and show notification on sign out`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()
            spyService.addAuthStateListener(listener)
            spyService.context = createMockAuthenticatedContext()

            spyService.signOut()
            SwingUtilities.invokeAndWait {}

            verify(listener).onSignedOut()
            verify(spyService).showSignOutNotification()
        }

        @Test
        fun `should not notify on sign out when not signed in`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()
            spyService.addAuthStateListener(listener)

            spyService.signOut()
            SwingUtilities.invokeAndWait {}

            verify(listener, never()).onSignedOut()
            verify(spyService, never()).showSignOutNotification()
        }

        @Test
        fun `should notify multiple listeners`() {
            val listener1 = mock<CCloudAuthService.AuthStateListener>()
            val listener2 = mock<CCloudAuthService.AuthStateListener>()
            spyService.addAuthStateListener(listener1)
            spyService.addAuthStateListener(listener2)
            spyService.context = createMockAuthenticatedContext()

            spyService.signOut()
            SwingUtilities.invokeAndWait {}

            verify(listener1).onSignedOut()
            verify(listener2).onSignedOut()
        }

        @Test
        fun `should not notify removed listener on sign out`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()
            spyService.addAuthStateListener(listener)
            spyService.removeAuthStateListener(listener)
            spyService.context = createMockAuthenticatedContext()

            spyService.signOut()
            SwingUtilities.invokeAndWait {}

            verify(listener, never()).onSignedOut()
        }

        @Test
        fun `should not notify removed listener on sign in`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()
            spyService.addAuthStateListener(listener)
            spyService.removeAuthStateListener(listener)

            spyService.notifySignedIn("test@example.com")
            SwingUtilities.invokeAndWait {}

            verify(listener, never()).onSignedIn(any())
        }

        @Test
        fun `should only notify once during idempotent sign out`() {
            val listener = mock<CCloudAuthService.AuthStateListener>()
            spyService.addAuthStateListener(listener)
            spyService.context = createMockAuthenticatedContext()

            spyService.signOut()
            spyService.signOut()
            SwingUtilities.invokeAndWait {}

            verify(listener, times(1)).onSignedOut()
            verify(spyService, times(1)).showSignOutNotification()
        }

        @Test
        fun `should show session expired notification when reason is session_expired`() {
            spyService.context = createMockAuthenticatedContext()

            spyService.signOut(reason = "session_expired")
            SwingUtilities.invokeAndWait {}

            verify(spyService).showSessionExpiredNotification()
            verify(spyService, never()).showSignOutNotification()
        }

        @Test
        fun `should show session expired notification when reason is refresh_failed`() {
            spyService.context = createMockAuthenticatedContext()

            spyService.signOut(reason = "refresh_failed")
            SwingUtilities.invokeAndWait {}

            verify(spyService).showSessionExpiredNotification()
            verify(spyService, never()).showSignOutNotification()
        }

        @Test
        fun `should show regular sign out notification for user initiated sign out`() {
            spyService.context = createMockAuthenticatedContext()

            spyService.signOut(reason = "user_initiated")
            SwingUtilities.invokeAndWait {}

            verify(spyService).showSignOutNotification()
            verify(spyService, never()).showSessionExpiredNotification()
        }
    }

    // Helper

    private fun createMockAuthenticatedContext(): CCloudOAuthContext {
        val mockToken = Token("test_token", Instant.now().plusSeconds(3600))
        return mock {
            on { getControlPlaneToken() } doReturn mockToken
            on { getDataPlaneToken() } doReturn mockToken
            on { getUserEmail() } doReturn "test@example.com"
            on { getCurrentOrganization() } doReturn OrganizationDetails(
                id = "org-123",
                name = "Test Org",
                resourceId = "org-res-123",
                sso = SsoDetails(enabled = false, mode = "none", vendor = "none")
            )
        }
    }
}
