package io.confluent.intellijplugin.core.rfs.tree.node

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.client.SchemaInfoPart
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.util.toPresentableText

class RfsMetaInfoNode(driver: Driver,
                      project: Project,
                      val schemaInfoPart: SchemaInfoPart
) : DriverRfsTreeNode(driver, project) {
  var savedChildren = emptyList<RfsMetaInfoNode>()

  override val isLoading: Boolean = false
  override val isMount: Boolean = false

  override fun getChildren() = savedChildren

  override fun update(presentation: PresentationData) {
    presentation.tooltip = error?.toPresentableText() ?: ""
    presentation.presentableText = schemaInfoPart.text
    presentation.setIcon(schemaInfoPart.icon)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RfsMetaInfoNode

    if (connId != other.connId) return false
    if (schemaInfoPart != other.schemaInfoPart) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + connId.hashCode()
    result = 31 * result + schemaInfoPart.hashCode()
    return result
  }
}