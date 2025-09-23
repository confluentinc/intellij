package io.confluent.kafka.core.monitoring.toolwindow

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.TabInfo
import io.confluent.kafka.core.ui.JBRunnerTabsBorderless

abstract class TabbedMonitoringController(val project: Project) : ComponentController, UiDataProvider {
  lateinit var tabs: JBRunnerTabs
  protected abstract val tabsControllers: List<Pair<String, ComponentController>>

  protected fun init() {
    tabs = object : JBRunnerTabsBorderless(project, this@TabbedMonitoringController) {
      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        DataSink.uiDataSnapshot(sink, this@TabbedMonitoringController)
      }
    }
    val childControllers = tabsControllers
    childControllers.forEach { (name, controller) ->
      Disposer.register(this, controller)

      val tabInfo = TabInfo(controller.getComponent()).apply {
        setText(name)
        val allActions = controller.getActions() + getActions()
        if (allActions.isNotEmpty()) {
          setTabPaneActions(DefaultActionGroup(allActions))
        }
      }

      tabs.addTab(tabInfo)
    }
  }

  fun selectByLabel(label: String?): ActionCallback? {
    val info = tabs.getInfoToLabel().entries.firstOrNull { it.key.text == label }?.key ?: return null
    return tabs.select(info, requestFocus = false)
  }

  override fun getComponent() = tabs.component
  override fun dispose() {}

  override fun uiDataSnapshot(sink: DataSink) {
  }
}