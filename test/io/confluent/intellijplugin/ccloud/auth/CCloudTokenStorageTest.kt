package io.confluent.intellijplugin.ccloud.auth

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

@TestApplication
class CCloudTokenStorageTest {

    @AfterEach
    fun tearDown() {
        CCloudTokenStorage.clearSession()
    }

    @Nested
    @DisplayName("saveSession")
    inner class SaveSession {

        @Test
        fun `should save session with valid context`() {
            val context = createMockContext(
                refreshToken = "test_refresh_token",
                refreshTokenExpiresAt = Instant.now().plusSeconds(86400),
                endOfLifetime = Instant.now().plusSeconds(86400),
                userEmail = "test@example.com",
                orgId = "org-123",
                orgName = "Test Org"
            )

            CCloudTokenStorage.saveSession(context)

            val loaded = CCloudTokenStorage.loadSession()
            assertNotNull(loaded)
            assertEquals("test_refresh_token", loaded?.refreshToken)
            assertEquals("test@example.com", loaded?.userEmail)
            assertEquals("org-123", loaded?.organizationId)
            assertEquals("Test Org", loaded?.organizationName)
        }

        @Test
        fun `should not save session when refresh token is null`() {
            val context = mock<CCloudOAuthContext> {
                on { getRefreshToken() } doReturn null
            }

            CCloudTokenStorage.saveSession(context)

            assertNull(CCloudTokenStorage.loadSession())
        }

        @Test
        fun `should save session with null organization`() {
            val context = createMockContext(
                refreshToken = "test_token",
                refreshTokenExpiresAt = Instant.now().plusSeconds(86400),
                endOfLifetime = Instant.now().plusSeconds(604800),
                userEmail = "test@example.com",
                orgId = null,
                orgName = null
            )

            CCloudTokenStorage.saveSession(context)

            val loaded = CCloudTokenStorage.loadSession()
            assertNotNull(loaded)
            assertNull(loaded?.organizationId)
            assertNull(loaded?.organizationName)
        }
    }

    @Nested
    @DisplayName("loadSession")
    inner class LoadSession {

        @Test
        fun `should return null when no session stored`() {
            assertNull(CCloudTokenStorage.loadSession())
        }

        @Test
        fun `should return null when refresh token expired`() {
            val context = createMockContext(
                refreshToken = "expired_token",
                refreshTokenExpiresAt = Instant.now().minusSeconds(100), // Expired
                endOfLifetime = Instant.now().plusSeconds(604800),
                userEmail = "test@example.com",
                orgId = "org-123",
                orgName = "Test Org"
            )

            CCloudTokenStorage.saveSession(context)

            assertNull(CCloudTokenStorage.loadSession())
        }

        @Test
        fun `should return null when end of lifetime passed`() {
            val context = createMockContext(
                refreshToken = "valid_token",
                refreshTokenExpiresAt = Instant.now().plusSeconds(86400),
                endOfLifetime = Instant.now().minusSeconds(100), // Expired
                userEmail = "test@example.com",
                orgId = "org-123",
                orgName = "Test Org"
            )

            CCloudTokenStorage.saveSession(context)

            assertNull(CCloudTokenStorage.loadSession())
        }

        @Test
        fun `should return valid session when not expired`() {
            val context = createMockContext(
                refreshToken = "valid_token",
                refreshTokenExpiresAt = Instant.now().plusSeconds(86400),
                endOfLifetime = Instant.now().plusSeconds(604800),
                userEmail = "user@test.com",
                orgId = "org-456",
                orgName = "My Org"
            )

            CCloudTokenStorage.saveSession(context)

            val loaded = CCloudTokenStorage.loadSession()
            assertNotNull(loaded)
            assertEquals("valid_token", loaded?.refreshToken)
            assertEquals("user@test.com", loaded?.userEmail)
            assertEquals("org-456", loaded?.organizationId)
        }

        @Test
        fun `should clear expired session from storage after loading`() {
            val expiredContext = createMockContext(
                refreshToken = "expired_token",
                refreshTokenExpiresAt = Instant.now().minusSeconds(100),
                endOfLifetime = Instant.now().plusSeconds(86400),
                userEmail = "test@example.com",
                orgId = "org-123",
                orgName = "Test Org"
            )
            CCloudTokenStorage.saveSession(expiredContext)

            assertNull(CCloudTokenStorage.loadSession())

            val validContext = createMockContext(
                refreshToken = "new_valid_token",
                refreshTokenExpiresAt = Instant.now().plusSeconds(86400),
                endOfLifetime = Instant.now().plusSeconds(86400),
                userEmail = "new@example.com",
                orgId = "",
                orgName = "New Org"
            )
            CCloudTokenStorage.saveSession(validContext)

            val loaded = CCloudTokenStorage.loadSession()
            assertNotNull(loaded)
            assertEquals("new_valid_token", loaded?.refreshToken)
            assertEquals("", loaded?.organizationId)
        }
    }

    @Nested
    @DisplayName("clearSession")
    inner class ClearSession {

        @Test
        fun `should clear stored session`() {
            val context = createMockContext(
                refreshToken = "test_token",
                refreshTokenExpiresAt = Instant.now().plusSeconds(86400),
                endOfLifetime = Instant.now().plusSeconds(604800),
                userEmail = "test@example.com",
                orgId = "org-123",
                orgName = "Test Org"
            )
            CCloudTokenStorage.saveSession(context)
            assertNotNull(CCloudTokenStorage.loadSession())

            CCloudTokenStorage.clearSession()

            assertNull(CCloudTokenStorage.loadSession())
        }

        @Test
        fun `should be safe to call when no session exists`() {
            CCloudTokenStorage.clearSession()

            assertNull(CCloudTokenStorage.loadSession())
        }
    }

    // Helper

    private fun createMockContext(
        refreshToken: String,
        refreshTokenExpiresAt: Instant,
        endOfLifetime: Instant?,
        userEmail: String,
        orgId: String?,
        orgName: String?
    ): CCloudOAuthContext {
        val mockToken = Token(refreshToken, refreshTokenExpiresAt)
        val mockOrg = if (orgId != null && orgName != null) {
            OrganizationDetails(
                id = orgId,
                name = orgName,
                resourceId = "org-res-123",
                sso = SsoDetails(enabled = false, mode = "none", vendor = "none")
            )
        } else null

        return mock {
            on { getRefreshToken() } doReturn mockToken
            on { getEndOfLifetime() } doReturn endOfLifetime
            on { getUserEmail() } doReturn userEmail
            on { getCurrentOrganization() } doReturn mockOrg
        }
    }
}
