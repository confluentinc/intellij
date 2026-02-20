package io.confluent.intellijplugin.core.settings.connections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.auth.CCloudOAuthContext
import io.confluent.intellijplugin.ccloud.auth.OrganizationDetails
import io.confluent.intellijplugin.ccloud.auth.SsoDetails
import io.confluent.intellijplugin.ccloud.auth.Token
import io.confluent.intellijplugin.core.settings.connections.CCloudDisplayGroup.Companion.formatSessionExpiry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.awt.CardLayout
import java.time.Instant
import javax.swing.JPanel

@TestApplication
class CCloudDisplayGroupTest {

    private val disposable = Disposer.newDisposable("CCloudDisplayGroupTest")
    private lateinit var mockAuthService: CCloudAuthService

    @BeforeEach
    fun setUp() {
        mockAuthService = CCloudAuthService(CoroutineScope(SupervisorJob()))
        ApplicationManager.getApplication()
            .replaceService(CCloudAuthService::class.java, mockAuthService, disposable)
    }

    @AfterEach
    fun tearDown() {
        mockAuthService.dispose()
        Disposer.dispose(disposable)
    }

    @Nested
    @DisplayName("createOptionsPanel")
    inner class CreateOptionsPanel {

        @Test
        fun `should return a panel with CardLayout`() {
            val group = CCloudDisplayGroup()

            val panel = group.createOptionsPanel()

            assertNotNull(panel)
            assertTrue(panel is JPanel)
            assertTrue((panel as JPanel).layout is CardLayout)

            group.disposeOptionsPanel()
        }

        @Test
        fun `should register auth state listener on creation`() {
            val group = CCloudDisplayGroup()
            val listenersBefore = mockAuthService.authStateListeners.size

            group.createOptionsPanel()

            assertEquals(listenersBefore + 1, mockAuthService.authStateListeners.size)

            group.disposeOptionsPanel()
        }

        @Test
        fun `should include sign-out link when signed in`() {
            mockAuthService.context = createMockAuthenticatedContext()
            val group = CCloudDisplayGroup()

            val panel = group.createOptionsPanel()

            val links = UIUtil.findComponentsOfType(panel as JPanel, ActionLink::class.java)
            assertTrue(links.isNotEmpty(), "Should contain a sign-out link")

            group.disposeOptionsPanel()
        }

        @Test
        fun `should build signed-in panel with org and expiry when authenticated`() {
            mockAuthService.context = createMockAuthenticatedContext()
            val group = CCloudDisplayGroup()

            val panel = group.createOptionsPanel() as JPanel

            // Count all components in the signed-in panel tree to verify org name
            // and session expiry branches are exercised (comment() renders as HTML labels)
            val componentCountWithOrg = countComponents(panel)

            group.disposeOptionsPanel()

            // Now create without org/expiry to verify branches produce different output
            mockAuthService.context = createMockAuthenticatedContext(
                orgName = null, endOfLifetime = null
            )
            val group2 = CCloudDisplayGroup()
            val panel2 = group2.createOptionsPanel() as JPanel
            val componentCountWithout = countComponents(panel2)

            group2.disposeOptionsPanel()

            assertTrue(
                componentCountWithOrg > componentCountWithout,
                "Panel with org + expiry ($componentCountWithOrg) should have more components " +
                    "than without ($componentCountWithout)"
            )
        }
    }

    @Nested
    @DisplayName("authStateListener")
    inner class AuthStateListener {

        @Test
        fun `should switch to signed-in card on sign-in`() {
            val group = CCloudDisplayGroup()
            val panel = group.createOptionsPanel() as JPanel
            val listener = mockAuthService.authStateListeners.last()

            mockAuthService.context = createMockAuthenticatedContext()
            listener.onSignedIn("test@example.com")

            // The panel should have been rebuilt — verify it still has components
            assertTrue(panel.componentCount > 0, "Panel should have cards after sign-in")

            group.disposeOptionsPanel()
        }

        @Test
        fun `should switch to sign-in card on sign-out`() {
            mockAuthService.context = createMockAuthenticatedContext()
            val group = CCloudDisplayGroup()
            val panel = group.createOptionsPanel() as JPanel
            val listener = mockAuthService.authStateListeners.last()

            listener.onSignedOut()

            // Panel should still have cards
            assertTrue(panel.componentCount > 0, "Panel should have cards after sign-out")

            group.disposeOptionsPanel()
        }
    }

    @Nested
    @DisplayName("disposeOptionsPanel")
    inner class DisposeOptionsPanel {

        @Test
        fun `should remove auth state listener on dispose`() {
            val group = CCloudDisplayGroup()
            group.createOptionsPanel()
            val listenersAfterCreate = mockAuthService.authStateListeners.size

            group.disposeOptionsPanel()

            assertEquals(listenersAfterCreate - 1, mockAuthService.authStateListeners.size)
        }

        @Test
        fun `should be safe to call without prior createOptionsPanel`() {
            val group = CCloudDisplayGroup()

            // Should not throw
            group.disposeOptionsPanel()
        }

        @Test
        fun `should stop expiry timer on dispose`() {
            val group = CCloudDisplayGroup()
            group.createOptionsPanel()

            // Access the private expiryTimer field to verify it was started
            val timerField = CCloudDisplayGroup::class.java.getDeclaredField("expiryTimer")
            timerField.isAccessible = true
            val timer = timerField.get(group) as javax.swing.Timer
            assertTrue(timer.isRunning, "Timer should be running before dispose")

            group.disposeOptionsPanel()

            assertFalse(timer.isRunning, "Timer should be stopped after dispose")
        }
    }

    @Nested
    @DisplayName("formatSessionExpiry")
    inner class FormatSessionExpiry {

        @Test
        fun `should return empty string when endOfLifetime is null`() {
            assertEquals("", formatSessionExpiry(null))
        }

        @Test
        fun `should return expired message when endOfLifetime is in the past`() {
            val pastInstant = Instant.now().minusSeconds(60)

            val result = formatSessionExpiry(pastInstant)

            assertTrue(result.contains("expired", ignoreCase = true), "Expected expired message but got: $result")
        }

        @Test
        fun `should show hours and minutes when both are present`() {
            val futureInstant = Instant.now().plusSeconds(2 * 3600 + 30 * 60 + 30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in 2h 30m"), "Expected relative time 'in 2h 30m' but got: $result")
            assertTrue(result.contains("("), "Expected absolute time in parentheses but got: $result")
        }

        @Test
        fun `should show only hours when minutes are zero`() {
            val futureInstant = Instant.now().plusSeconds(3 * 3600 + 30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in 3h"), "Expected relative time 'in 3h' but got: $result")
            assertTrue(!result.startsWith("in 3h 0m"), "Should not show '0m' but got: $result")
        }

        @Test
        fun `should show only minutes when hours are zero`() {
            val futureInstant = Instant.now().plusSeconds(45 * 60 + 30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in 45m"), "Expected relative time 'in 45m' but got: $result")
        }

        @Test
        fun `should show less than one minute when under 60 seconds remain`() {
            val futureInstant = Instant.now().plusSeconds(30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in <1m"), "Expected 'in <1m' but got: $result")
        }

        @Test
        fun `should include absolute time in parentheses for future expiry`() {
            val futureInstant = Instant.now().plusSeconds(3600)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.matches(Regex("in .+ \\(.+\\)")), "Expected format 'in Xh (absolute)' but got: $result")
        }
    }

    private fun createMockAuthenticatedContext(
        orgName: String? = "Test Org",
        endOfLifetime: Instant? = Instant.now().plusSeconds(3600)
    ): CCloudOAuthContext {
        val mockToken = Token("test_token", Instant.now().plusSeconds(3600))
        val org = orgName?.let {
            OrganizationDetails(
                id = "org-123",
                name = it,
                resourceId = "org-res-123",
                sso = SsoDetails(enabled = false, mode = "none", vendor = "none")
            )
        }
        return mock {
            on { getControlPlaneToken() } doReturn mockToken
            on { getDataPlaneToken() } doReturn mockToken
            on { getUserEmail() } doReturn "test@example.com"
            on { getEndOfLifetime() } doReturn endOfLifetime
            on { getCurrentOrganization() } doReturn org
        }
    }

    private fun countComponents(container: java.awt.Container): Int {
        var count = container.componentCount
        for (child in container.components) {
            if (child is java.awt.Container) {
                count += countComponents(child)
            }
        }
        return count
    }
}
