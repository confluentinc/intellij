package io.confluent.intellijplugin.core.monitoring.data.updater

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.core.monitoring.data.model.DataModel
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Component
import javax.swing.JProgressBar

class MonitoringProgressComponent(val updater: BdtMonitoringUpdater) : Disposable, MonitoringUpdateListener {
  private val textField = JBLabel()

  private val progressField = JProgressBar(0, 100).also {
    it.alignmentX = Component.LEFT_ALIGNMENT
  }

  val component = panel {
    row {
      cell(textField)
      cell(progressField)
      link("") {
        updater.cancelCurrent()
      }.also {
        it.component.icon = AllIcons.Actions.Cancel
      }
    }
  }

  private var curUpdate: Int? = null

  init {
    Disposer.register(updater, this)
    updater.addListener(this)

    component.isVisible = false
  }

  override fun dispose() {
    updater.removeListener(this)
  }

  override fun onStartRefreshConnection() {
    onEnd(null)
    textField.text = KafkaMessagesBundle.message("monitoring.progress.update.start.refresh.driver")
    component.isVisible = true
    component.revalidate()
    component.repaint()
  }

  override fun onStartRefreshModels(id: Int, models: List<DataModel<*>>) {
    if (curUpdate != null)
      return

    curUpdate = id
    component.isVisible = true
    component.revalidate()
    component.repaint()
  }

  override fun onEnd(id: Int?) {
    if (id != null && curUpdate != id)
      return

    curUpdate = null
    component.isVisible = false
    component.revalidate()
    component.repaint()
  }

  override fun setIntermediate(id: Int, value: Boolean) {
    if (curUpdate != id)
      return
    progressField.isIndeterminate = value
  }

  override fun setText(id: Int, @NlsContexts.Label text: String) {
    if (curUpdate != id)
      return

    textField.text = text
  }

  override fun setProgress(id: Int, progress: Double) {
    if (curUpdate != id)
      return

    progressField.value = (progress * 100).toInt()
  }
}

