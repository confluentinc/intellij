package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.icons.AllIcons
import com.intellij.json.JsonLanguage
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.*
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
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.registry.schema.SchemaTreePanel
import io.confluent.intellijplugin.registry.ui.KafkaRegistrySchemaEditor
import io.confluent.intellijplugin.registry.ui.KafkaSchemaInfoDialog
import io.confluent.intellijplugin.toolwindow.config.KafkaClusterConfig
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.util.KafkaMessagesBundle.message
import net.miginfocom.layout.ConstraintParser
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.function.BiFunction
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

internal class ConfluentSchemaDetailController(
    private val project: Project,
    private val dataManager: CCloudClusterDataManager
) : ComponentController, DetailsMonitoringController<String> {

    private val isStructure = AtomicBooleanProperty(false)
    private val isSchema = AtomicBooleanProperty(false)
    private val isEditMode = AtomicBooleanProperty(false)
    private val isNotEditMode = AtomicBooleanProperty(true)
    private val isLoading = AtomicBooleanProperty(true)
    private val hasContent = AtomicBooleanProperty(false)
    private val hasError = AtomicBooleanProperty(false)
    private val isEditModeAvailable = AtomicBooleanProperty(false)

    @NlsSafe
    private var schemaName: String? = null

    private var selectedVersionSchema: SchemaVersionInfo? = null
    private var comparedVersionSchema: SchemaVersionInfo? = null

    private val selectedVersionController = SchemaVersionsComboboxController(this, dataManager) { versions ->
        isEditModeAvailable.set(versions.size > 1)
        if (versions.isNotEmpty()) {
            selectedVersion.component.item = versions.first()
        } else {
            isLoading.set(true)
            hasContent.set(false)
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
            label(message("confluent.cloud.details.schema.loading")).align(Align.CENTER)
        }.resizableRow().visibleIf(isLoading)

        row {
            label(message("confluent.cloud.details.schema.load.failed")).align(Align.CENTER)
        }.resizableRow().visibleIf(hasError)

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

    private fun getSchemaRegistryConfig(): KafkaClusterConfig {
        return KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.getSchemaRegistryConfigId())
    }

    private fun init() {
        component.add(createToolbar(internalComponent), BorderLayout.NORTH)
        component.add(internalComponent, BorderLayout.CENTER)
        onViewTypeUpdate()
    }

    override fun getComponent(): JComponent = component

    override fun setDetailsId(@Nls id: String) {
        selectedVersionController.setSchema(id)
        comparedVersionController.setSchema(id)
        schemaName = id
    }

    private fun updateSelectedVersion() {
        val schemaName = schemaName ?: return
        val version = selectedVersion.component.item ?: return

        isLoading.set(true)
        hasContent.set(false)
        hasError.set(false)

        dataManager.getSchemaVersionInfo(schemaName, version)
            .onSuccess { applySelectedVersion(schemaName, it) }
            .onError { onSelectedVersionFailed(schemaName, version, it) }
    }

    private fun applySelectedVersion(schemaName: String, versionInfo: SchemaVersionInfo) {
        if (selectedVersionSchema == versionInfo) {
            onEdtIfCurrent(schemaName) {
                isLoading.set(false)
                hasContent.set(true)
            }
            return
        }

        selectedVersionSchema = versionInfo
        val prettySchema = KafkaRegistryUtil.getPrettySchema(schemaType = versionInfo.type.name, schema = versionInfo.schema)
        val parsedSchema = dataManager.parseSchemaForDisplay(versionInfo)
            .onFailure { thisLogger().warn("Failed to parse schema for '$schemaName' version ${versionInfo.version}", it) }
            .getOrNull()

        onEdtIfCurrent(schemaName) {
            schemaView.setText(
                prettySchema,
                if (versionInfo.type != KafkaRegistryFormat.PROTOBUF) JsonLanguage.INSTANCE
                else KafkaRegistryUtil.protobufLanguage
            )
            parsedSchema?.let {
                try {
                    structureView.update(it)
                } catch (e: IllegalArgumentException) {
                    // schema has no type definitions — tree view stays empty
                }
            }
            diffViewController.updatePrevious(versionInfo)
            isLoading.set(false)
            hasContent.set(true)
        }
    }

    private fun onSelectedVersionFailed(schemaName: String, version: Long, error: Throwable) {
        thisLogger().warn("Failed to load schema version info for '$schemaName' version $version", error)
        onEdtIfCurrent(schemaName) {
            isLoading.set(false)
            hasContent.set(false)
            hasError.set(true)
        }
    }

    private fun onEdtIfCurrent(expectedSchemaName: String, block: () -> Unit) = invokeLater {
        if (Disposer.isDisposed(this)) return@invokeLater
        if (expectedSchemaName != schemaName) return@invokeLater
        block()
    }

    private fun updateComparedVersion() {
        val schemaName = schemaName ?: return
        val version = comparedVersion.component.item ?: return

        dataManager.getSchemaVersionInfo(schemaName, version).onSuccess {
            if (comparedVersionSchema == it) {
                return@onSuccess
            }

            comparedVersionSchema = it
            invokeLater {
                if (!Disposer.isDisposed(this@ConfluentSchemaDetailController)) {
                    diffViewController.updateNew(it)
                }
            }
        }.onError { error ->
            thisLogger().warn("Failed to load compared version info for '$schemaName' version $version", error)
        }
    }

    private fun createToolbar(targetComponent: JComponent): JPanel {
        val leftActionGroup = createLeftActionGroup()
        val rightActionGroup = createRightActionGroup()

        val leftToolbar = ToolbarUtils.createActionToolbar("ConfluentSchemaToolbarLeft", leftActionGroup, true).apply {
            this.targetComponent = targetComponent
        }

        val rightToolbar = ToolbarUtils.createActionToolbar("ConfluentSchemaToolbarRight", rightActionGroup, true).apply {
            layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
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
        val config = getSchemaRegistryConfig()

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
                val versionInfo = selectedVersionSchema ?: return

                Messages.showCheckboxMessageDialog(
                    KafkaMessagesBundle.message(
                        "action.remove.version.confirm.dialog.msg",
                        versionInfo.version,
                        versionInfo.schemaName
                    ),
                    KafkaMessagesBundle.message("action.delete.version.text"),
                    arrayOf(Messages.getOkButton(), Messages.getCancelButton()),
                    KafkaMessagesBundle.message("action.remove.version.confirm.dialog.option"),
                    false, 0, 0,
                    Messages.getQuestionIcon(),
                    BiFunction { exitCode: Int, _: JCheckBox ->
                        if (exitCode == Messages.OK) {
                            dataManager.deleteRegistrySchemaVersion(versionInfo)
                        }
                        exitCode
                    })
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible =
                    selectedVersionSchema != null && selectedVersion.component.itemCount > 1
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        })

        return DefaultActionGroup(updateSchemaAction, moreAction)
    }

    private fun onViewTypeUpdate() {
        val config = getSchemaRegistryConfig()
        val selectedViewType = viewType.selectedItem ?: ViewType.STRUCTURE
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
