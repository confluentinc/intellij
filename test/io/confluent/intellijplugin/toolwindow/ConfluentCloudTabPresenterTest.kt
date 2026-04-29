package io.confluent.intellijplugin.toolwindow

import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowController.Companion.CONNECTION_ID
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
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

class ConfluentCloudTabPresenterTest {

    private lateinit var settings: KafkaPluginSettings
    private lateinit var contentManager: ContentManager
    private lateinit var ccloudContent: Content
    private lateinit var emptyContent: Content
    private var contentManagerOverride: ContentManager? = null

    @BeforeEach
    fun setUp() {
        settings = KafkaPluginSettings()
        contentManager = mock()
        ccloudContent = mock()
        emptyContent = mock()
        contentManagerOverride = contentManager
    }

    private fun presenter() = ConfluentCloudTabPresenter(
        pluginSettings = settings,
        contentManagerProvider = { contentManagerOverride },
        ccloudContentFactory = { ccloudContent },
        emptyContentFactory = { emptyContent },
    )

    private fun mockContent(connectionId: String?): Content = mock {
        on { getUserData(CONNECTION_ID) } doReturn connectionId
    }

    @Nested
    @DisplayName("add")
    inner class Add {

        @Test
        fun `is a no-op when content manager is unavailable`() {
            contentManagerOverride = null

            presenter().add()

            verify(contentManager, never()).addContent(any())
        }

        @Test
        fun `is a no-op when hide flag is set`() {
            settings.hideConfluentCloudTab = true

            presenter().add()

            verify(contentManager, never()).addContent(any())
        }

        @Test
        fun `is a no-op when ccloud tab already exists`() {
            val existing = mockContent("ccloud")
            whenever(contentManager.contents).doReturn(arrayOf(existing))

            presenter().add()

            verify(contentManager, never()).addContent(any())
        }

        @Test
        fun `replaces empty placeholder with ccloud content`() {
            val placeholder = mockContent(null)
            whenever(contentManager.contents).doReturn(arrayOf(placeholder))

            presenter().add()

            verify(contentManager).removeContent(placeholder, true)
            verify(contentManager).addContent(ccloudContent)
        }
    }

    @Nested
    @DisplayName("remove")
    inner class Remove {

        @Test
        fun `is a no-op when content manager is unavailable`() {
            contentManagerOverride = null

            presenter().remove()

            verify(contentManager, never()).removeContent(any(), any())
        }

        @Test
        fun `is a no-op when no ccloud tab exists`() {
            val other = mockContent("kafka-1")
            whenever(contentManager.contents).doReturn(arrayOf(other))

            presenter().remove()

            verify(contentManager, never()).removeContent(any(), any())
        }

        @Test
        fun `inserts empty placeholder when removing the last tab`() {
            val ccloud = mockContent("ccloud")
            whenever(contentManager.contents).doReturn(arrayOf(ccloud))

            presenter().remove()

            verify(contentManager).addContent(emptyContent)
            verify(contentManager).removeContent(ccloud, true)
        }

        @Test
        fun `does not insert placeholder when other tabs remain`() {
            val ccloud = mockContent("ccloud")
            val other = mockContent("kafka-1")
            whenever(contentManager.contents).doReturn(arrayOf(ccloud, other))

            presenter().remove()

            verify(contentManager, never()).addContent(any())
            verify(contentManager).removeContent(ccloud, true)
        }
    }

    @Nested
    @DisplayName("authListener")
    inner class AuthListener {

        @Test
        fun `onSignedIn clears the hide flag and re-adds the tab`() {
            settings.hideConfluentCloudTab = true
            whenever(contentManager.contents).doReturn(emptyArray())

            presenter().authListener.onSignedIn("user@example.com")

            assertFalse(settings.hideConfluentCloudTab)
            verify(contentManager).addContent(ccloudContent)
        }
    }
}
