package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.json.JsonLanguage
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
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
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.layout.migLayout.createLayoutConstraints
import com.intellij.ui.layout.migLayout.patched.MigLayout
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.ComponentController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsMonitoringController
import com.jetbrains.bigdatatools.common.ui.CustomComponentActionImpl
import com.jetbrains.bigdatatools.common.util.ToolbarUtils
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.common.editor.SchemaVersionDiffController
import com.jetbrains.bigdatatools.kafka.common.editor.SchemaVersionsComboboxController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.SchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.registry.schema.SchemaTreePanel
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaRegistrySchemaEditor
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaClusterConfig
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import net.miginfocom.layout.ConstraintParser
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.function.BiFunction
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class KafkaSchemaController(private val project: Project,
                            private val dataManager: KafkaDataManager) : ComponentController,
                                                                         DetailsMonitoringController<String> {
  private val config: KafkaClusterConfig
    get() = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)

  private val isStructure = AtomicBooleanProperty(false)
  private val isSchema = AtomicBooleanProperty(false)
  private val isEditMode = AtomicBooleanProperty(false)
  private val isNotEditMode = AtomicBooleanProperty(false)
  private val isEditModeAvailable = AtomicBooleanProperty(false)

  @NlsSafe
  private var schemaName: String? = null
  private val version1Controller = SchemaVersionsComboboxController(this, dataManager) {
    isEditModeAvailable.set(it.size > 1)
  }.also {
    Disposer.register(this, it)
  }

  private val version2Controller = SchemaVersionsComboboxController(this, dataManager) {}.also {
    Disposer.register(this, it)
  }
  private lateinit var version1: Cell<ComboBox<Long>>
  private lateinit var version2: Cell<ComboBox<Long>>
  private lateinit var viewType: SegmentedButton<ViewType>

  private var version1Schema: SchemaVersionInfo? = null
  private var version2Schema: SchemaVersionInfo? = null

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
      cell(structureView.getComponent()).align(Align.FILL)
    }.resizableRow().visibleIf(isStructure)

    row {
      cell(schemaView.component).align(Align.FILL)
    }.resizableRow().visibleIf(isNotEditMode)

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
  }

  override fun getComponent(): JComponent = component

  override fun setDetailsId(@NlsSafe id: String) {
    version1Controller.setSchema(id)
    version2Controller.setSchema(id)
    schemaName = id
    updateVersion1Info()
    updateVersion2Info()
  }

  private fun updateVersion1Info() {
    val schemaName = schemaName
    if (schemaName == null)
      return
    val version = version1.component.item ?: return
    dataManager.getSchemaVersionInfo(schemaName, version).onSuccess {
      if (version1Schema == it)
        return@onSuccess

      version1Schema = it
      val prettySchema = KafkaRegistryUtil.getPrettySchema(schemaType = it.type.name, schema = it.schema)
      val parsedSchema = KafkaRegistryUtil.parseSchema(schemaType = it.type, newText = it.schema, references = it.references,
                                                       dataManager = dataManager).getOrNull()
                         ?: return@onSuccess
      invokeLater {
        schemaView.setText(prettySchema, if (it.type != KafkaRegistryFormat.PROTOBUF) JsonLanguage.INSTANCE
        else KafkaRegistryUtil.protobufLanguage)
        structureView.update(parsedSchema)
        diffViewController.updateVersion1(it)
      }
    }
  }

  private fun updateVersion2Info() {
    val schemaName = schemaName
    if (schemaName == null)
      return
    val version = version2.component.item ?: return
    dataManager.getSchemaVersionInfo(schemaName, version).onSuccess {
      if (version2Schema == it)
        return@onSuccess

      version2Schema = it
      invokeLater {
        diffViewController.updateVersion2(it)
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
      layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY // For removing empty space on the right.
      this.targetComponent = targetComponent
    }

    val toolbarPanel = JPanel(MigLayout(createLayoutConstraints(0, 0).noVisualPadding().fill(),
                                        ConstraintParser.parseColumnConstraints("[grow][pref!]"))).apply {
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

        version1 = cell(version1Controller.getComponent()).onChanged {
          updateVersion1Info()
        }.customize(UnscaledGaps(top = 0, left = 0, bottom = 0, right = 0)).visibleIf(version1Controller.isVisible)

        link(KafkaMessagesBundle.message("link.label.compare")) {
          isNotEditMode.set(false)
          isEditMode.set(true)
        }.visibleIf(isEditModeAvailable.and(isNotEditMode)).customize(UnscaledGaps(top = 0, left = 10, bottom = 0, right = 0))

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
    val updateSchemaAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.create.new.version"),
                                                      null,
                                                      AllIcons.Actions.EditScheme) {
      override fun actionPerformed(e: AnActionEvent) {
        val versionInfo = version1Schema ?: return

        KafkaSchemaInfoDialog.showDiff(KafkaMessagesBundle.message("update.dialog.title"), project, versionInfo) { newText ->
          dataManager.updateSchema(versionInfo, newText)
        }
      }

      override fun displayTextInToolbar(): Boolean = true
    }

    val moreAction = MoreActionGroup()
    moreAction.add(object : DumbAwareAction(KafkaMessagesBundle.message("action.delete.version.text")) {
      override fun actionPerformed(e: AnActionEvent) {
        val versionSchema = version1Schema ?: return
        if (dataManager.registryType == KafkaRegistryType.AWS_GLUE) {
          val askRes = Messages.showOkCancelDialog(
            project,
            KafkaMessagesBundle.message("action.remove.version.confirm.dialog.msg", versionSchema.version,
                                        versionSchema.schemaName),
            KafkaMessagesBundle.message("action.delete.version.text"),
            Messages.getOkButton(),
            Messages.getCancelButton(), Messages.getQuestionIcon())
          if (askRes == Messages.OK) {
            dataManager.deleteRegistrySchemaVersion(versionSchema)
          }
        }
        else {
          Messages.showCheckboxMessageDialog(
            KafkaMessagesBundle.message("action.remove.version.confirm.dialog.msg", versionSchema.version,
                                        versionSchema.schemaName),
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
        e.presentation.isEnabledAndVisible = version1Schema != null && version1.component.itemCount > 1
        if (e.presentation.isEnabledAndVisible && dataManager.connectionData.registryType == KafkaRegistryType.AWS_GLUE) {
          val versions = version1.component.getUserData(SchemaVersionsComboboxController.VERSIONS_LIST_KEY)
          val selectedItem = version1.component.item
          e.presentation.isEnabledAndVisible = selectedItem != versions?.lastOrNull()
        }
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    })
    return DefaultActionGroup(updateSchemaAction, moreAction)
  }

  private fun onViewTypeUpdate() {
    val selectedViewType = viewType.selectedItem
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

