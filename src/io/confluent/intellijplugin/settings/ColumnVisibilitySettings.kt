package io.confluent.intellijplugin.core.settings

import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import com.intellij.ide.actions.SmartPopupActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import io.confluent.intellijplugin.core.delegate.Delegate2
import io.confluent.intellijplugin.core.monitoring.table.extension.LocalizedField
import io.confluent.intellijplugin.core.util.StringUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

class ColumnVisibilitySettings(var visibleColumns: MutableList<String>) {

  private open class ConfigColumnsVisibilityAction(columns: List<*>,
                                                   settings: ColumnVisibilitySettings) : SmartPopupActionGroup(), DumbAware {
    init {
      templatePresentation.text = KafkaMessagesBundle.message("show.hide.columns")
      templatePresentation.icon = BigdatatoolsKafkaIcons.Table.ConfigureColumns

      for (field in columns) {
        add(object : DumbAwareToggleAction(field.localizedColumn(), KafkaMessagesBundle.message("show.hide.column"), null) {
          override fun isSelected(e: AnActionEvent) = settings.isColumnVisible(field.name())
          override fun getActionUpdateThread() = ActionUpdateThread.BGT
          override fun setSelected(e: AnActionEvent, state: Boolean) = settings.setColumnVisible(field.name(), state)
        })
      }
    }

    // When column header is empty (for example, spark monitoring), we show 'column.field.name' in column list for 'Show/Hide' action
    fun Any?.localizedColumn(): @Nls String {
      return (this as? LocalizedField<*>)?.let {
        if (it.i18Key != null)
          it.getLocalizedName()
        else StringUtils.camelCaseToReadableString(it.name)
      } ?: StringUtils.camelCaseToReadableString(this.toString())
    }

    fun Any?.name(): String {
      return (this as? LocalizedField<*>)?.name ?: this.toString()
    }
  }

  companion object {
    fun createAction(columns: List<LocalizedField<*>>, settings: ColumnVisibilitySettings): SmartPopupActionGroup {
      return ConfigColumnsVisibilityAction(columns, settings)
    }

    fun createRightAlignedAction(columns: List<LocalizedField<*>>, settings: ColumnVisibilitySettings): SmartPopupActionGroup {
      return object : ConfigColumnsVisibilityAction(columns, settings), RightAlignedToolbarAction {}
    }

    fun createAction(settings: ColumnVisibilitySettings, columns: List<String>): SmartPopupActionGroup {
      return ConfigColumnsVisibilityAction(columns, settings)
    }
  }

  val onColumnVisibilityChanged = Delegate2<String, Boolean, Unit>()

  fun isColumnVisible(name: String) = visibleColumns.contains(name)

  fun setColumnVisible(name: String, visible: Boolean) {
    if (visible) {
      visibleColumns.add(name)
    }
    else {
      visibleColumns.remove(name)
    }

    onColumnVisibilityChanged.notify(name, visible)
  }
}