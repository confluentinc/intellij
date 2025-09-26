package io.confluent.intellijplugin.core.rfs.url

import com.intellij.ide.actions.SmartPopupActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class UrlDriverChooserAction(val project: Project, val url: String) : SmartPopupActionGroup(), DumbAware {
  private val openers = UrlFileOpener.getOpenersFor(url)

  init {
    templatePresentation.text = KafkaMessagesBundle.message("url.opener.action.title")

    val (enabled, disabled) = openers
      .flatMap { opener -> opener.getConnections(project).map { it to opener } }
      .partition { it.first.isEnabled }

    enabled.forEach { (connection, opener) ->
      add(DumbAwareAction.create(connection.name) {
        opener.openIfRfsViewer(project, url, connection)
      })
    }

    if (disabled.isNotEmpty()) {
      addSeparator(KafkaMessagesBundle.message("url.opener.separator.title.disabled"))
    }

    disabled.forEach { (connection, opener) ->
      add(DumbAwareAction.create(connection.name) {
        ConnectionUtil.enableConnectionByData(project, connection)
        opener.openIfRfsViewer(project, url, connection)
      })
    }

    addSeparator(KafkaMessagesBundle.message("url.opener.separator.title.create"))

    openers.forEach { opener ->
      add(DumbAwareAction.create(KafkaMessagesBundle.message("url.opener.action.create", opener.name)) {
        opener.createConnection(project, url)
      })
    }
  }
}
