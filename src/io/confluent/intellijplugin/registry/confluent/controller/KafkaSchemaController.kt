package io.confluent.intellijplugin.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.json.JsonLanguage
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.layout.migLayout.createLayoutConstraints
import com.intellij.ui.layout.migLayout.patched.MigLayout
import io.confluent.intellijplugin.common.editor.SchemaVersionDiffController
import io.confluent.intellijplugin.common.editor.SchemaVersionsComboboxController
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.monitoring.toolwindow.DetailsMonitoringController
import io.confluent.intellijplugin.core.ui.CustomComponentActionImpl
import io.confluent.intellijplugin.core.util.ToolbarUtils
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.registry.schema.SchemaTreePanel
import io.confluent.intellijplugin.registry.ui.KafkaRegistrySchemaEditor
import io.confluent.intellijplugin.registry.ui.KafkaSchemaInfoDialog
import io.confluent.intellijplugin.toolwindow.config.KafkaClusterConfig
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import net.miginfocom.layout.ConstraintParser
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.function.BiFunction
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class KafkaSchemaController(
    private val project: Project,
    private val dataManager: BaseClusterDataManager
) : ComponentController,
    DetailsMonitoringController<String> {
    private val config: KafkaClusterConfig
        get() = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)

    private val isStructure = AtomicBooleanProperty(false)
    private val isSchema = AtomicBooleanProperty(true)
    private val isEditMode = AtomicBooleanProperty(false)
    private val isNotEditMode = AtomicBooleanProperty(true)
    private val isEditModeAvailable = AtomicBooleanProperty(false)
    private val isLoading = AtomicBooleanProperty(false)
    private val hasContent = AtomicBooleanProperty(false)

    @NlsSafe
    private var schemaName: String? = null
    private val selectedVersionController = SchemaVersionsComboboxController(this, dataManager) { versions ->
        isEditModeAvailable.set(versions.size > 1)
        if (versions.isNotEmpty()) {
            selectedVersion.component.item = versions.first()
            updateSelectedVersion()
        }
    }.also {
        Disposer.register(this, it)
    }

    private val comparedVersionController = SchemaVersionsComboboxController(this, dataManager) {}.also {
        Disposer.register(this, it)
    }
    private lateinit var selectedVersion: Cell<ComboBox<Long>>
    private lateinit var comparedVersion: Cell<ComboBox<Long>>
    private lateinit var viewType: SegmentedButton<ViewType>

    private var selectedVersionSchema: SchemaVersionInfo? = null
    private var comparedVersionSchema: SchemaVersionInfo? = null

    private val component = JPanel(BorderLayout())

    private val schemaView = KafkaRegistrySchemaEditor(project, parentDisposable = this, isEditable = false).apply {
        component.preferredSize = Dimension(20, 20)
    }
    private val structureView = SchemaTreePanel()

    private val diffViewController = SchemaVersionDiffController(project).also {
        Disposer.register(this, it)
    }

    private val internalComponent = panel {
        row {
            label(KafkaMessagesBundle.message("confluent.cloud.details.schema.loading")).align(Align.CENTER)
        }.resizableRow().visibleIf(isLoading)

        row {
            cell(structureView.getComponent()).align(Align.FILL)
        }.resizableRow().visibleIf(isStructure.and(hasContent))

        row {
            cell(schemaView.component).align(Align.FILL)
        }.resizableRow().visibleIf(isSchema.and(hasContent).and(isNotEditMode))

        row {
            cell(diffViewController.component).align(Align.FILL)
        }.resizableRow().visibleIf(isEditMode)
    }

    init {
        init()
    }

    override fun dispose() {}

    fun init() {
        component.add(createToolbar(internalComponent), BorderLayout.NORTH)
        component.add(internalComponent, BorderLayout.CENTER)
        onViewTypeUpdate()

        // Clear "Nothing to show" text on all components after EDT layout completes
        invokeLater {
            clearEmptyTextRecursive(internalComponent)
        }
    }

    private fun clearEmptyTextRecursive(comp: Component) {
        when (comp) {
            is JBTable -> comp.emptyText.clear()
            is JBList<*> -> comp.emptyText.clear()
            is JBPanelWithEmptyText -> comp.emptyText.clear()
            is Tree -> comp.emptyText.clear()
            is JBScrollPane -> comp.viewport?.view?.let {
                (it as? Component)?.let(::clearEmptyTextRecursive)
            }
        }

        (comp as? Container)?.components?.forEach(::clearEmptyTextRecursive)
    }

    override fun getComponent(): JComponent = component

    override fun setDetailsId(@NlsSafe id: String) {
        isLoading.set(true)
        hasContent.set(false)

        selectedVersionController.setSchema(id)
        comparedVersionController.setSchema(id)
        schemaName = id
        updateSelectedVersion()
        updateComparedVersion()
    }

    private fun updateSelectedVersion() {
        val schemaName = schemaName
        if (schemaName == null)
            return
        val version = selectedVersion.component.item ?: return

        dataManager.getSchemaVersionInfo(schemaName, version).onSuccess {
            if (selectedVersionSchema == it) {
                invokeLater {
                    if (Disposer.isDisposed(this@KafkaSchemaController)) return@invokeLater
                    isLoading.set(false)
                    hasContent.set(true)
                }
                return@onSuccess
            }

            selectedVersionSchema = it
            val prettySchema = KafkaRegistryUtil.getPrettySchema(schemaType = it.type.name, schema = it.schema)
            val parsedSchema = dataManager.parseSchemaForDisplay(it).getOrNull()
                ?: return@onSuccess
            invokeLater {
                // Guard against disposed controller
                if (Disposer.isDisposed(diffViewController)) {
                    return@invokeLater
                }
                schemaView.setText(
                    prettySchema, if (it.type != KafkaRegistryFormat.PROTOBUF) JsonLanguage.INSTANCE
                    else KafkaRegistryUtil.protobufLanguage
                )
                structureView.update(parsedSchema)
                diffViewController.updatePrevious(it)

                isLoading.set(false)
                hasContent.set(true)
            }
        }
    }

    private fun updateComparedVersion() {
        val schemaName = schemaName
        if (schemaName == null)
            return
        val version = comparedVersion.component.item ?: return
        dataManager.getSchemaVersionInfo(schemaName, version).onSuccess {
            if (comparedVersionSchema == it)
                return@onSuccess

            comparedVersionSchema = it
            invokeLater {
                // Guard against disposed controller
                if (!Disposer.isDisposed(diffViewController)) {
                    diffViewController.updateNew(it)
                }
            }
        }
    }

    private fun createToolbar(targetComponent: JComponent): JPanel {
        val leftActionGroup = createLeftActionGroup()
        val rightActionGroup = createRightActionGroup()

        val leftToolbar = ToolbarUtils.createActionToolbar("KafkaSchemaToolbarLeft", leftActionGroup, true).apply {
            this.targetComponent = targetComponent
        }

        val rightToolbar = ToolbarUtils.createActionToolbar("KafkaSchemaToolbarRight", rightActionGroup, true).apply {
            layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY // For removing empty space on the right.
            this.targetComponent = targetComponent
        }

        val toolbarPanel = JPanel(
            MigLayout(
                createLayoutConstraints(0, 0).noVisualPadding().fill(),
                ConstraintParser.parseColumnConstraints("[grow][pref!]")
            )
        ).apply {
            border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
            add(leftToolbar.component)
            add(rightToolbar.component)
        }
        return toolbarPanel
    }

    private fun createLeftActionGroup(): DefaultActionGroup {

        val panel = panel {
            row {
                viewType = segmentedButton(ViewType.entries) { text = it.title }
                    .customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 25))
                viewType.selectedItem = if (config.isStructure) ViewType.STRUCTURE else ViewType.SCHEMA
                viewType.whenItemSelected {
                    onViewTypeUpdate()
                }

                selectedVersion = cell(selectedVersionController.getComponent()).onChanged {
                    updateSelectedVersion()
                }.customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 0))
                    .visibleIf(selectedVersionController.isVisible)

                link(KafkaMessagesBundle.message("link.label.compare")) {
                    isNotEditMode.set(false)
                    isEditMode.set(true)
                }.visibleIf(isEditModeAvailable.and(isNotEditMode))
                    .customize(UnscaledGaps(top = 0, left = 10, bottom = 0, right = 0))

                icon(AllIcons.General.ArrowRight).visibleIf(isEditMode).gap(RightGap.SMALL)
                    .customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 0))

                comparedVersion = cell(comparedVersionController.getComponent()).visibleIf(isEditMode).onChanged {
                    updateComparedVersion()
                }.customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 0))

                actionButton(DumbAwareAction.create(AllIcons.Windows.CloseInactive) {
                    isNotEditMode.set(true)
                    isEditMode.set(false)
                }, ActionPlaces.TOOLBAR).visibleIf(isEditMode)
                    .customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 0))
            }.bottomGap(BottomGap.NONE).topGap(TopGap.NONE)
        }

        return DefaultActionGroup(CustomComponentActionImpl(panel))
    }

    private fun createRightActionGroup(): DefaultActionGroup {
        val updateSchemaAction = object : DumbAwareAction(
            KafkaMessagesBundle.message("action.create.new.version"),
            null,
            AllIcons.Actions.EditScheme
        ) {
            init {
                templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
            }

            override fun actionPerformed(e: AnActionEvent) {
                val versionInfo = selectedVersionSchema ?: return

                KafkaSchemaInfoDialog.showDiff(
                    KafkaMessagesBundle.message("update.dialog.title"),
                    project,
                    versionInfo
                ) { newText ->
                    val versionModel = dataManager.getSchemaVersionsModel(versionInfo.schemaName)
                    versionModel.addListener(object : DataModelListener {
                        override fun onChanged() {
                            invokeLater { selectedVersion.component.item = versionModel.originObject?.firstOrNull() }
                            versionModel.removeListener(this)
                        }
                    })
                    dataManager.updateSchema(versionInfo, newText)
                }
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = selectedVersionSchema != null
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        val moreAction = MoreActionGroup()
        moreAction.add(object : DumbAwareAction(KafkaMessagesBundle.message("action.delete.version.text")) {
            override fun actionPerformed(e: AnActionEvent) {
                val versionSchema = selectedVersionSchema ?: return
                if (dataManager.registryType == KafkaRegistryType.AWS_GLUE) {
                    // AWS Glue: simple dialog, soft delete only
                    val askRes = Messages.showOkCancelDialog(
                        project,
                        KafkaMessagesBundle.message(
                            "action.remove.version.confirm.dialog.msg", versionSchema.version,
                            versionSchema.schemaName
                        ),
                        KafkaMessagesBundle.message("action.delete.version.text"),
                        Messages.getOkButton(),
                        Messages.getCancelButton(),
                        Messages.getQuestionIcon()
                    )
                    if (askRes == Messages.OK) {
                        dataManager.deleteRegistrySchemaVersion(versionSchema)
                    }
                } else {
                    // Confluent: checkbox for permanent deletion (displayed but ignored)
                    Messages.showCheckboxMessageDialog(
                        KafkaMessagesBundle.message(
                            "action.remove.version.confirm.dialog.msg", versionSchema.version,
                            versionSchema.schemaName
                        ),
                        KafkaMessagesBundle.message("action.delete.version.text"),
                        arrayOf(Messages.getOkButton(), Messages.getCancelButton()),
                        KafkaMessagesBundle.message("action.remove.version.confirm.dialog.option"),
                        false, 0, 0,
                        Messages.getQuestionIcon(),
                        BiFunction { exitCode: Int, _: JCheckBox ->
                            if (exitCode == Messages.OK) {
                                dataManager.deleteRegistrySchemaVersion(versionSchema)
                            }
                            exitCode
                        })
                }
            }

            override fun update(e: AnActionEvent) {
                e.presentation.description = ""
                e.presentation.isEnabledAndVisible = selectedVersionSchema != null && selectedVersion.component.itemCount > 1
                if (e.presentation.isEnabledAndVisible && dataManager.registryType == KafkaRegistryType.AWS_GLUE) {
                    val versions = selectedVersion.component.getUserData(SchemaVersionsComboboxController.VERSIONS_LIST_KEY)
                    val selectedItem = selectedVersion.component.item
                    e.presentation.isEnabledAndVisible = selectedItem != versions?.lastOrNull()
                }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        })
        return DefaultActionGroup(updateSchemaAction, moreAction)
    }

    private fun onViewTypeUpdate() {
        val selectedViewType = viewType.selectedItem ?: ViewType.SCHEMA
        config.isStructure = selectedViewType == ViewType.STRUCTURE

        isStructure.set(selectedViewType == ViewType.STRUCTURE)
        isSchema.set(selectedViewType == ViewType.SCHEMA)
        isNotEditMode.set(selectedViewType == ViewType.SCHEMA)
        isEditMode.set(false)
    }

    companion object {
        enum class ViewType(@Nls val title: String) {
            STRUCTURE(KafkaMessagesBundle.message("schema.view.type.structure")),
            SCHEMA(KafkaMessagesBundle.message("schema.view.type.schema"))
        }
    }
}