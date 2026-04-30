package io.confluent.intellijplugin.toolwindow

import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowController.Companion.CONNECTION_ID
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings

/** Owns Confluent Cloud tab visibility — add/remove on demand, re-show on ccloud sign-in. */
internal class ConfluentCloudTabPresenter(
    private val pluginSettings: KafkaPluginSettings,
    private val contentManagerProvider: () -> ContentManager?,
    private val ccloudContentFactory: (ContentManager) -> Content,
    private val emptyContentFactory: () -> Content,
) {
    fun add() {
        val cm = contentManagerProvider() ?: return
        if (pluginSettings.hideConfluentCloudTab) return
        if (cm.contents.any { it.getUserData(CONNECTION_ID) == "ccloud" }) return

        cm.contents
            .filter { it.getUserData(CONNECTION_ID) == null }
            .forEach { cm.removeContent(it, true) }

        cm.addContent(ccloudContentFactory(cm))
    }

    fun remove() {
        val cm = contentManagerProvider() ?: return
        val content = cm.contents.firstOrNull { it.getUserData(CONNECTION_ID) == "ccloud" } ?: return
        if (cm.contents.size == 1) cm.addContent(emptyContentFactory())
        cm.removeContent(content, true)
    }

    // Treat a fresh sign-in like a new ccloud connection: unhide and re-add the tab.
    val authListener = object : CCloudAuthService.AuthStateListener {
        override fun onSignedIn(email: String) {
            pluginSettings.hideConfluentCloudTab = false
            add()
        }
    }
}
