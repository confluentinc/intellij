package io.confluent.intellijplugin.core.rfs.tree.node

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import io.confluent.intellijplugin.core.rfs.icons.RfsIcons
import io.confluent.intellijplugin.core.rfs.settings.RemoteFsDriverProvider
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class DisabledRfsTreeNode(project: Project, val connectionData: RemoteFsDriverProvider) : RfsTreeNode(project) {
  init {
    update()
  }

  override val isMount = true

  override val connId: String get() = connectionData.innerId
  private val connName get() = connectionData.name

  override fun isAlwaysLeaf(): Boolean = true

  override fun update(presentation: PresentationData) {
    presentation.clearText()
    presentation.presentableText = connName

    presentation.addText(connName, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    presentation.tooltip = KafkaMessagesBundle.message("tooltip.disabled.connection")
    presentation.setIcon(project?.let { connectionData.getIcon() } ?: RfsIcons.DIRECTORY_ICON)
  }

  override fun getChildren() = emptyList<RfsTreeNode>()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DisabledRfsTreeNode

    if (connId != other.connId) return false
    if (connName != other.connName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + connId.hashCode()
    result = 31 * result + connName.hashCode()
    return result
  }

  override fun toString(): String = "$connName ($connId)"
}