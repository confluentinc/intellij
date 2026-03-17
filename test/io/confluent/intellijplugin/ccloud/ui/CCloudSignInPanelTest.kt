package io.confluent.intellijplugin.ccloud.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import javax.swing.JButton
import javax.swing.JPanel

@TestApplication
class CCloudSignInPanelTest {

    private val disposable = Disposer.newDisposable("CCloudSignInPanelTest")
    private lateinit var mockAuthService: CCloudAuthService

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

    @Nested
    @DisplayName("create without callback")
    inner class WithoutCallback {

        @Test
        fun `should return a JPanel`() {
            val panel = CCloudSignInPanel.create()

            assertTrue(panel is JPanel)
        }

        @Test
        fun `should contain a sign-in button`() {
            val panel = CCloudSignInPanel.create()

            val signInButton = findSignInButton(panel)

            assertNotNull(signInButton)
            assertEquals("Sign in", signInButton?.text)
        }

        @Test
        fun `should not show create connection link`() {
            val panel = CCloudSignInPanel.create()

            val links = UIUtil.findComponentsOfType(panel, ActionLink::class.java)

            assertTrue(links.isEmpty())
        }
    }

    @Nested
    @DisplayName("create with callback")
    inner class WithCallback {

        @Test
        fun `should show create connection link`() {
            val panel = CCloudSignInPanel.create() { }

            val links = UIUtil.findComponentsOfType(panel, ActionLink::class.java)

            assertFalse(links.isEmpty())
        }

        @Test
        fun `should show create connection link with correct text`() {
            val panel = CCloudSignInPanel.create() { }

            val link = UIUtil.findComponentOfType(panel, ActionLink::class.java)

            assertEquals("or Create a Kafka connection", link?.text)
        }

        @Test
        fun `should invoke callback when link is clicked`() {
            var invoked = false
            val panel = CCloudSignInPanel.create() { invoked = true }

            UIUtil.findComponentOfType(panel, ActionLink::class.java)?.doClick()

            assertTrue(invoked)
        }

        @Test
        fun `should still contain a sign-in button`() {
            val panel = CCloudSignInPanel.create() { }

            assertNotNull(findSignInButton(panel))
        }
    }

    @Nested
    @DisplayName("sign-in button")
    inner class SignInButton {

        @Test
        fun `should call signIn on CCloudAuthService when clicked`() {
            val panel = CCloudSignInPanel.create()

            findSignInButton(panel)?.doClick()

            verify(mockAuthService).signIn(null)
        }
    }

    private fun findSignInButton(panel: javax.swing.JComponent): JButton? =
        UIUtil.findComponentsOfType(panel, JButton::class.java)
            .filterNot { it is ActionLink }
            .firstOrNull()
}
