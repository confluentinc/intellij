package io.confluent.intellijplugin.common.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import io.confluent.intellijplugin.common.settings.ConfigChangeListener
import io.confluent.intellijplugin.common.settings.KafkaRunConfig
import io.confluent.intellijplugin.common.settings.StorageConfig
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListCellRenderer

open class Presets<T : StorageConfig>(private val runConfig: KafkaRunConfig,
                                      renderer: ListCellRenderer<T>) : ConfigChangeListener<T>, Disposable {
  private val model = DefaultListModel<T>()
  private val presetsList = JBList(model).apply {
    cellRenderer = renderer
  }

  var onApply: ((T) -> Unit)? = null

  val component: JPanel = ToolbarDecorator.createDecorator(presetsList)
    .setMoveDownAction(null)
    .setMoveUpAction(null)
    .addExtraAction(object : DumbAwareAction(KafkaMessagesBundle.message("producer.preset.apply"), null, AllIcons.Actions.Checked) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = presetsList.selectedIndex != -1
      }

      override fun actionPerformed(e: AnActionEvent) {
        presetsList.selectedValue?.let { onApply?.invoke(it) }
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    })
    .setRemoveAction {
      presetsList.selectedValue?.let {
        runConfig.removeConfig(it)
      }
    }.createPanel().apply {
      border = BorderFactory.createEmptyBorder()
    }

  init {
    presetsList.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(mouseEvent: MouseEvent) {
        if (mouseEvent.clickCount == 2) {
          val index = presetsList.locationToIndex(mouseEvent.point)
          if (index >= 0) {
            onApply?.invoke(presetsList.model.getElementAt(index))
          }
        }
      }
    })

    model.addAll(runConfig.loadConfigs() as List<T>)
    runConfig.addChangeListener(this as ConfigChangeListener<StorageConfig>)
  }

  override fun dispose() {
    runConfig.removeChangeListener(this as ConfigChangeListener<StorageConfig>)
  }

  override fun configAdded(config: T) = model.addElement(config)

  override fun configRemoved(config: T) {
    model.removeElement(config)
  }
}