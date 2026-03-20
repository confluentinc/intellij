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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.layout.ConstraintParser
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.function.BiFunction
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * CCloud schema detail view with tree/raw toggle, version selector, and compare mode.
 */
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
    private val isEditModeAvailable = AtomicBooleanProperty(false)

    @NlsSafe
    private var schemaName: String? = null

    private var version1Schema: SchemaVersionInfo? = null
    private var version2Schema: SchemaVersionInfo? = null

    private val version1Controller = SchemaVersionsComboboxController(this, dataManager) { versions ->
        isEditModeAvailable.set(versions.size > 1)
        if (versions.isNotEmpty()) {
            version1.component.item = versions.first()
            updateVersion1Info()
        } else {
            isLoading.set(false)
            hasContent.set(false)
        }
    }.also {
        Disposer.register(this, it)
    }

    private val version2Controller = SchemaVersionsComboboxController(this, dataManager) {}.also {
        Disposer.register(this, it)
    }

    private lateinit var version1: Cell<ComboBox<Long>>
    private lateinit var version2: Cell<ComboBox<Long>>
    private lateinit var viewType: SegmentedButton<ViewType>

    private val component = JPanel(BorderLayout())

    // UI components reused from Kafka tab
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
        version1Controller.setSchema(id)
        version2Controller.setSchema(id)
        schemaName = id
    }

    private fun updateVersion1Info() {
        val schemaName = schemaName ?: return
        val version = version1.component.item ?: return

        isLoading.set(true)
        hasContent.set(false)

        dataManager.getSchemaVersionInfo(schemaName, version).onSuccess { versionInfo ->
            if (version1Schema == versionInfo) {
                invokeLater {
                    if (Disposer.isDisposed(this@ConfluentSchemaDetailController)) return@invokeLater
                    isLoading.set(false)
                    hasContent.set(true)
                }
                return@onSuccess
            }

            version1Schema = versionInfo
            val prettySchema = KafkaRegistryUtil.getPrettySchema(schemaType = versionInfo.type.name, schema = versionInfo.schema)

            invokeLater {
                if (Disposer.isDisposed(this@ConfluentSchemaDetailController)) return@invokeLater

                schemaView.setText(
                    prettySchema,
                    if (versionInfo.type != KafkaRegistryFormat.PROTOBUF) JsonLanguage.INSTANCE
                    else KafkaRegistryUtil.protobufLanguage
                )

                diffViewController.updateVersion1(versionInfo)

                isLoading.set(false)
                hasContent.set(true)
            }

            dataManager.driver.coroutineScope.launch(Dispatchers.Default) {
                val parseResult = dataManager.parseSchemaForDisplay(versionInfo)
                val parsedSchema = parseResult.getOrNull()

                if (parsedSchema == null) {
                    val error = parseResult.exceptionOrNull()
                    thisLogger().warn("Failed to parse schema for '$schemaName' version $version: ${error?.message}", error)
                    return@launch
                }

                invokeLater {
                    if (Disposer.isDisposed(this@ConfluentSchemaDetailController)) return@invokeLater
                    if (version1Schema != versionInfo) return@invokeLater

                    try {
                        structureView.update(parsedSchema)
                    } catch (e: IllegalArgumentException) {
                        // Empty schema with no type definitions, skip tree view
                    }
                }
            }
        }.onError { error ->
            thisLogger().warn("Failed to load schema version info for '$schemaName' version $version", error)
            invokeLater {
                if (Disposer.isDisposed(this@ConfluentSchemaDetailController)) return@invokeLater

                isLoading.set(false)
                hasContent.set(false)
            }
        }
    }

    private fun updateVersion2Info() {
        val schemaName = schemaName ?: return
        val version = version2.component.item ?: return

        dataManager.getSchemaVersionInfo(schemaName, version).onSuccess {
            if (version2Schema == it) {
                return@onSuccess
            }

            version2Schema = it
            invokeLater {
                if (!Disposer.isDisposed(this@ConfluentSchemaDetailController)) {
                    diffViewController.updateVersion2(it)
                }
            }
        }.onError { error ->
            thisLogger().warn("Failed to load version 2 info for '$schemaName' version $version", error)
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

                version1 = cell(version1Controller.getComponent()).onChanged {
                    updateVersion1Info()
                }.customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 0))
                    .visibleIf(version1Controller.isVisible)

                link(KafkaMessagesBundle.message("link.label.compare")) {
                    isNotEditMode.set(false)
                    isEditMode.set(true)
                }.visibleIf(isEditModeAvailable.and(isNotEditMode))
                    .customize(UnscaledGaps(top = 0, left = 10, bottom = 0, right = 0))

                icon(AllIcons.General.ArrowRight).visibleIf(isEditMode).gap(RightGap.SMALL)
                    .customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 0))

                version2 = cell(version2Controller.getComponent()).visibleIf(isEditMode).onChanged {
                    updateVersion2Info()
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
                val versionInfo = version1Schema ?: return

                KafkaSchemaInfoDialog.showDiff(
                    KafkaMessagesBundle.message("update.dialog.title"),
                    project,
                    versionInfo
                ) { newText ->
                    val versionModel = dataManager.getSchemaVersionsModel(versionInfo.schemaName)
                    versionModel.addListener(object : DataModelListener {
                        override fun onChanged() {
                            invokeLater { version1.component.item = versionModel.originObject?.firstOrNull() }
                            versionModel.removeListener(this)
                        }
                    })
                    dataManager.updateSchema(versionInfo, newText)
                }
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = version1Schema != null
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        val moreAction = MoreActionGroup()
        moreAction.add(object : DumbAwareAction(KafkaMessagesBundle.message("action.delete.version.text")) {
            override fun actionPerformed(e: AnActionEvent) {
                val versionInfo = version1Schema ?: return

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
                    version1Schema != null && version1.component.itemCount > 1
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
