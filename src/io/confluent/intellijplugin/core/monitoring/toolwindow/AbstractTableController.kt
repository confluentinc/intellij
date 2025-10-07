package io.confluent.intellijplugin.core.monitoring.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.migLayout.createLayoutConstraints
import com.intellij.ui.layout.migLayout.patched.MigLayout
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.table.DataTableCreator
import io.confluent.intellijplugin.core.monitoring.table.TableClickHelper
import io.confluent.intellijplugin.core.monitoring.table.extension.*
import io.confluent.intellijplugin.core.monitoring.table.extension.TableErrorHandler.Companion.CUSTOM_EMPTY_TEXT_PROVIDER
import io.confluent.intellijplugin.core.monitoring.table.getSelectedData
import io.confluent.intellijplugin.core.monitoring.table.model.DataTableColumnModel
import io.confluent.intellijplugin.core.monitoring.table.model.DataTableModel
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.ColumnVisibilitySettings
import io.confluent.intellijplugin.core.table.MaterialJBScrollPane
import io.confluent.intellijplugin.core.ui.ToolbarGreyLabelActionImpl
import io.confluent.intellijplugin.core.util.ToolbarUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import net.miginfocom.layout.ConstraintParser
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

abstract class AbstractTableController<T : RemoteInfo> : ComponentController {
    protected lateinit var columnModel: DataTableColumnModel<T>
    lateinit var dataTable: DataTable<T>
    protected lateinit var tableScrollPane: JBScrollPane
    protected lateinit var decoratedTableComponent: SimpleToolWindowPanel

    override fun getComponent(): JComponent = decoratedTableComponent

    override fun dispose() = Unit

    protected open fun init() {
        columnModel = createColumnModel()
        dataTable = createTable()
        decoratedTableComponent = createDecoratedTable()

        setupActions()
        dataTable.putUserData(CUSTOM_EMPTY_TEXT_PROVIDER, emptyTextProvider())
    }

    protected open fun emptyTextProvider(): CustomEmptyTextProvider? = null
    protected abstract fun getColumnSettings(): ColumnVisibilitySettings
    protected abstract fun getRenderableColumns(): List<LocalizedField<T>>
    protected abstract fun getDataModel(): ObjectDataModel<T>?
    protected open fun showColumnFilter(): Boolean = true

    /** Optional vertical title on left toolbar. */
    protected open fun getToolbarTitle(): @Nls String? = null
    protected open fun getAdditionalActions(): List<AnAction> = emptyList()
    protected open fun getAdditionalContextActions(): List<AnAction> = emptyList()

    private fun setupActions() {
        val actionGroup = DefaultActionGroup().apply {
            addAll(getAdditionalActions())
            addAll(getAdditionalContextActions())
        }

        PopupHandler.installPopupMenu(dataTable, DefaultActionGroup(actionGroup), "AbstractTableController")
    }

    protected fun isRowSelected(): Boolean {
        val selectedRow = dataTable.selectedRow
        return selectedRow != -1
    }

    protected fun getSelectedItem(): T? = dataTable.getSelectedData()

    private fun createTable(): DataTable<T> {
        val dataModel = getDataModel()
        dataModel?.let {
            val listener = object : DataModelListener {
                override fun onLoadMore() {
                    notifyLimitLabel.isVisible = it.loadMore
                }

                override fun onErrorAdditionalLoad(throwable: Throwable?) = additionalLoadException.set(throwable)
            }

            dataModel.addListener(listener)
            notifyLimitLabel.isVisible = dataModel.loadMore
            Disposer.register(this, Disposable {
                it.removeListener(listener)
            })
        }
        val tableModel = DataTableModel(dataModel, columnModel)

        val table = DataTableCreator.create(tableModel, getTableExtensions())

        TableClickHelper.installBrowseOn(table, getRenderableColumns().map { it.field })

        customTableInit(table)
        TableSelectionPreserver.installOn(table, null)
        Disposer.register(this, table)
        Disposer.register(table, columnModel)
        Disposer.register(table, tableModel)
        return table
    }

    protected open fun customTableInit(table: DataTable<T>) = Unit

    protected open fun getTableExtensions(): EnumSet<TableExtensionType> =
        EnumSet.of(
            TableExtensionType.SPEED_SEARCH,
            TableExtensionType.RENDERERS_SETTER,
            TableExtensionType.ERROR_HANDLER,
            TableExtensionType.LOADING_INDICATOR,
            TableExtensionType.COLUMNS_FITTER,
            TableExtensionType.SMART_RESIZER
        )

    private fun createColumnModel(): DataTableColumnModel<T> =
        DataTableColumnModel(getRenderableColumns(), getColumnSettings())

    private fun createToolbar(): ActionToolbar? {
        val columnFilter = if (showColumnFilter()) {
            createColumnFilterAction()
        } else {
            null
        }

        //val toolbarTitle = getToolbarTitle()
        //val titleActions: List<AnAction> = if (toolbarTitle != null) {
        //  listOf(ToolbarVerticalLabelAction.create(toolbarTitle), Separator())
        //}
        //else {
        //  emptyList()
        //}

        val additionalActions = getAdditionalActions()
        val addActionsWithSeparator = if (additionalActions.isNotEmpty())
            listOf(Separator()) + additionalActions
        else
            emptyList()
        return ToolbarUtils.createVerticalToolbar(
            listOfNotNull(columnFilter) + addActionsWithSeparator,
            place = TABLE_TOOLBAR_PLACE
        )
    }

    fun createColumnFilterAction() = ColumnVisibilitySettings.createAction(columnModel.allColumns, getColumnSettings())

    protected open fun createTopLeftToolbarActions(): List<AnAction> = emptyList()
    protected open fun createTopRightToolbarActions(): List<AnAction> = emptyList()

    private var additionalLoadException = AtomicProperty<Throwable?>(null)

    private lateinit var notifyErrorPanel: Panel

    private val notifyLimitLabel =
        JLabel(KafkaMessagesBundle.message("monitoring.increase.the.limit.to.display.more.rows")).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground(false)
            icon = AllIcons.General.Note
            isVisible = false
        }

    private fun createDecoratedTable() = SimpleToolWindowPanel(false, true).apply {

        val tableScrollPane = MaterialJBScrollPane(dataTable).apply {
            TableHeightFitter.installOn(this, dataTable)
        }
        setContent(tableScrollPane)

        createToolbar()?.let {
            it.targetComponent = this
            toolbar = it.component
        }

        val topLeftToolbarActions = createTopLeftToolbarActions()

        val topLeftToolbar = if (topLeftToolbarActions.isEmpty())
            null
        else {
            ToolbarUtils.createActionToolbar(
                targetComponent = this, place = TABLE_TOOLBAR_PLACE,
                actions = topLeftToolbarActions, horizontal = true
            )
        }

        val toolbarTitle = getToolbarTitle()
        val titleActions =
            if (toolbarTitle != null) listOf(ToolbarGreyLabelActionImpl(toolbarTitle), Separator()) else emptyList()

        val topRightToolbarActions = titleActions + createTopRightToolbarActions()

        val topRightToolbar = if (topRightToolbarActions.isEmpty()) null
        else ToolbarUtils.createActionToolbar(
            targetComponent = this,
            TABLE_TOOLBAR_PLACE,
            topRightToolbarActions,
            horizontal = true
        ).apply {
            layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
        }

        when {
            topLeftToolbar == null -> {}
            topRightToolbar == null -> {
                add(topLeftToolbar.component.apply { border = JBUI.Borders.empty() }, BorderLayout.NORTH)
            }

            else -> {
                val toolbarPanel = JPanel(
                    MigLayout(
                        createLayoutConstraints(0, 0).noVisualPadding().fill(),
                        ConstraintParser.parseColumnConstraints("[grow][pref!]")
                    )
                ).apply {
                    border = JBUI.Borders.empty()
                    add(topLeftToolbar.component)
                    add(topRightToolbar.component)
                }

                add(toolbarPanel, BorderLayout.NORTH)
            }
        }

        val south = panel {
            row {
                cell(notifyLimitLabel).align(Align.FILL).resizableColumn()
            }

            notifyErrorPanel = panel {
                row {
                    label(KafkaMessagesBundle.message("monitoring.additional.info.load.error.label")).component.also {
                        it.foreground = JBUI.CurrentTheme.Label.disabledForeground(false)
                        it.icon = AllIcons.General.Warning
                    }
                    link(KafkaMessagesBundle.message("monitoring.additional.info.load.error.link")) {
                        val e = additionalLoadException.get() ?: return@link
                        RfsNotificationUtils.showExceptionMessage(
                            project = null,
                            title = KafkaMessagesBundle.message("monitoring.additional.info.load.error.title"),
                            e = e
                        )
                    }
                }.visibleIf(additionalLoadException.isNotNull())
            }
        }
        add(south, BorderLayout.SOUTH)
    }

    companion object {
        const val TABLE_TOOLBAR_PLACE = "BDTTable"
    }
}