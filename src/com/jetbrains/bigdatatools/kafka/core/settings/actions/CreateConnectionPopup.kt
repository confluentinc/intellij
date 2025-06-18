package com.jetbrains.bigdatatools.kafka.core.settings.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.jetbrains.bigdatatools.kafka.core.settings.paneadd.StandaloneCreateConnectionUtil
import java.awt.Component

object CreateConnectionPopup {
  fun createPopup(actionGroup: ActionGroup, dataContext: DataContext): JBPopup {
    return JBPopupFactory.getInstance().createActionGroupPopup(null, StandaloneCreateConnectionUtil.FlattenedGroup(actionGroup),
                                                               dataContext,
                                                               JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                                                               ActionPlaces.getPopupPlace("CreateConnectionPopup"))
  }
  fun createPopup(actionGroup: ActionGroup, e: AnActionEvent): JBPopup {
    return createPopup(actionGroup, e.dataContext)
  }
}

fun JBPopup.showForToolbarOrInBestPositionFor(dataContext: DataContext, component: Component?) {
  if (component != null) {
    showUnderneathOf(component)
  }
  else {
    showInBestPositionFor(dataContext)
  }
}

fun JBPopup.showForToolbarOrInBestPositionFor(e: AnActionEvent) {
  showForToolbarOrInBestPositionFor(e.dataContext, e.inputEvent?.component.takeIf { it is ActionButtonComponent })
}