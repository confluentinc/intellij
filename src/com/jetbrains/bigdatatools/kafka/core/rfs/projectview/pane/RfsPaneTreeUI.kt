package io.confluent.kafka.core.rfs.projectview.pane

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.tree.ui.DefaultTreeUI
import io.confluent.kafka.core.rfs.projectview.actions.RfsPaneOwner
import java.awt.event.MouseEvent
import javax.swing.tree.AbstractLayoutCache

object RfsPaneTreeUI {
  fun prepareUiForPane(pane: RfsPaneOwner) = DefaultTreeUIPatched(pane)

  class DefaultTreeUIPatched(private val pane: RfsPaneOwner) : DefaultTreeUI() {
    // We have a number of situation with our tree. They are described in
    // https://youtrack.jetbrains.com/issue/BDIDE-4820/Rework-our-connections-RFS-tree
    override fun createLayoutCache(): AbstractLayoutCache {
      val newLayout = Registry.`is`("ide.tree.experimental.layout.cache", true)
      if (!newLayout) {
        Registry.get("ide.tree.experimental.layout.cache").setValue(true)
      }
      val layoutCache = super.createLayoutCache()
      if (!newLayout) {
        Registry.get("ide.tree.experimental.layout.cache").setValue(false)
      }
      return layoutCache
    }

    // Double click should not expand file node (to show its structure) it should only to open it.
    // https://youtrack.jetbrains.com/issue/BDIDE-2792
    override fun isToggleEvent(event: MouseEvent): Boolean {
      if (!super.isToggleEvent(event)) return false
      return pane.getSelectedDriverNode()?.fileInfo?.isFile != true
    }
  }
}