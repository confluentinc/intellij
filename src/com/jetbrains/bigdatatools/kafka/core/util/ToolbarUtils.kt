package com.jetbrains.bigdatatools.kafka.core.util

import com.intellij.openapi.actionSystem.*
import javax.swing.JComponent

// Every toolbar have a special space on the right which is used for showing collapsed state where it is not enough place for all items.
//  To remove this space, set layoutStrategy = ToolbarLayoutStrategy.HORIZONTAL_NOWRAP_STRATEGY // For removing empty space on the right.
object ToolbarUtils {
  fun createVerticalToolbar(actions: List<AnAction>, place: String): ActionToolbar? {
    if (actions.isEmpty()) {
      return null
    }

    return createActionToolbar(place, DefaultActionGroup(actions), horizontal = false)
  }

  fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar {
    return ActionManager.getInstance().createActionToolbar(place, group, horizontal)
  }

  fun createActionToolbar(targetComponent: JComponent, place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar {
    return ActionManager.getInstance().createActionToolbar(place, group, horizontal).apply { this.targetComponent = targetComponent }
  }

  fun createActionToolbar(targetComponent: JComponent, place: String, actions: List<AnAction>, horizontal: Boolean): ActionToolbar {
    return ActionManager.getInstance().createActionToolbar(place, DefaultActionGroup(actions),
                                                           horizontal).apply { this.targetComponent = targetComponent }
  }
}
