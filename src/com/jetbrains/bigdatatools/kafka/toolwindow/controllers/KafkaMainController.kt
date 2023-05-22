package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.ide.DataManager
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tree.AsyncTreeModel
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.ComponentController
import com.jetbrains.bigdatatools.common.rfs.driver.DriverConnectionStatus
import com.jetbrains.bigdatatools.common.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.rfs.editorviewer.RfsEditorErrorPanel
import com.jetbrains.bigdatatools.common.rfs.editorviewer.RfsNodeAnimator
import com.jetbrains.bigdatatools.common.rfs.fileInfo.DriverRfsListener
import com.jetbrains.bigdatatools.common.rfs.projectview.actions.RfsActionPlaces
import com.jetbrains.bigdatatools.common.rfs.tree.DriverRfsTreeModel
import com.jetbrains.bigdatatools.common.rfs.util.RfsUtil
import com.jetbrains.bigdatatools.common.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.confluent.controller.KafkaRegistryController
import com.jetbrains.bigdatatools.kafka.registry.confluent.controller.KafkaSchemaController
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isConsumers
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isSchemas
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
class KafkaMainController(private val project: Project, private val connectionData: KafkaConnectionData) : ComponentController {
  private val driver = DriverManager.getDriverById(project, connectionData.innerId) as? KafkaDriver ?: error(
    "Data Manager is not initialized")
  private val dataManager = driver.dataManager

  private val topicsController = TopicsController(project, dataManager, this).also { Disposer.register(this, it) }
  private val consumerGroupsController = ConsumerGroupsController(dataManager).also { Disposer.register(this, it) }

  private val registryController = if (dataManager.registryType != KafkaRegistryType.NONE)
    KafkaRegistryController(project, dataManager, this).also { Disposer.register(this, it) }
  else
    null

  private val topicInfoController = TopicDetailsController(project, dataManager).also { Disposer.register(this, it) }
  private val schemaInfoController = if (dataManager.registryType != KafkaRegistryType.NONE)
    KafkaSchemaController(project, dataManager).also { Disposer.register(this, it) }
  else
    null

  private val detailsLayout = CardLayout()
  private val details = JPanel(detailsLayout)

  private val isNormalView = AtomicBooleanProperty(true)
  private val isErrorView = AtomicBooleanProperty(false)

  private lateinit var myTree: ProjectViewTree
  private val normalPanel = createNormalPanel()
  private var prevError: Throwable? = null
  private val errorPanel = JPanel(BorderLayout())
  private val panel = panel {
    row {
      cell(normalPanel).align(Align.FILL)
    }.resizableRow().visibleIf(isNormalView)
    row {
      cell(errorPanel).align(Align.FILL)
    }.resizableRow().visibleIf(isErrorView)
  }

  private val driverListener = object : DriverRfsListener {
    override fun driverRefreshFinished(status: DriverConnectionStatus) {
      invokeLater {
        updateMainPanel(status.getException())
      }
    }
  }

  init {
    driver.addListener(driverListener)
    Disposer.register(this, Disposable { driver.removeListener(driverListener) })
    updateMainPanel(driver.dataManager.client.connectionError)

    DataManager.registerDataProvider(panel) { dataId ->
      when {
        DATA_MANAGER.`is`(dataId) -> dataManager
        RFS_PATH.`is`(dataId) -> myTree.selectionPath?.lastDriverNode?.rfsPath
        else -> null
      }
    }
  }

  override fun dispose() {}

  override fun getComponent() = panel

  fun open(rfsPath: RfsPath) {
    RfsUtil.select(driver.getExternalId(), rfsPath, myTree)
  }

  private fun showDetailsComponent(rfsPath: RfsPath) {
    val label = dataManager.activeComponentLabel
    when {
      rfsPath.isRoot -> {
        showDetailsComponent(null)
        label.text = ""
      }
      rfsPath.isTopicFolder -> {
        showDetailsComponent(KafkaGroupType.TOPIC)
        label.text = KafkaGroupType.TOPIC.title
      }
      rfsPath.isConsumers || rfsPath.parent?.isConsumers == true -> {
        showDetailsComponent(KafkaGroupType.CONSUMER_GROUP)
        label.text = KafkaGroupType.CONSUMER_GROUP.title
      }
      rfsPath.isSchemas -> {
        showDetailsComponent(KafkaGroupType.SCHEMA_REGISTRY_GROUP)
        label.text = KafkaGroupType.SCHEMA_REGISTRY_GROUP.title
      }
      rfsPath.parent?.isTopicFolder == true -> {
        showDetailsComponent(KafkaGroupType.TOPIC_DETAIL)
        topicInfoController.setDetailsId(rfsPath.name)
        label.text = KafkaMessagesBundle.message("active.component.topic.detail", rfsPath.name)
      }
      rfsPath.parent?.isSchemas == true -> {
        showDetailsComponent(KafkaGroupType.SCHEMA_DETAIL)
        schemaInfoController?.setDetailsId(rfsPath.name)
        label.text = KafkaMessagesBundle.message("active.component.schema.detail", rfsPath.name)
      }
    }
  }

  private fun showDetailsComponent(selectedValue: KafkaGroupType?) {
    detailsLayout.show(details, selectedValue?.name)
  }

  private fun openProducer(): Array<FileEditor> {
    val file = LightVirtualFile("${connectionData.name} Producer", KafkaFileType(), "").apply {
      putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
      putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.PRODUCER)
    }
    return FileEditorManager.getInstance(project).openFile(file, true)
  }

  private fun createConsumerFile(): LightVirtualFile {
    return LightVirtualFile("${connectionData.name} Consumer", KafkaFileType(), "").apply {
      putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
      putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.CONSUMER)
    }
  }

  private fun createNormalPanel(): JPanel {
    val driver = DriverManager.getDriverById(project, connectionData.innerId) as KafkaDriver
    val treeModel = driver.createTreeModel(driver.root, project)
    val asyncTreeModel = AsyncTreeModel(treeModel, this)

    myTree = ProjectViewTree(asyncTreeModel)
    myTree.showsRootHandles = true
    myTree.isRootVisible = false
    DriverRfsTreeModel.fixInitFirstConnection(asyncTreeModel, myTree)

    val nodeAnimator = RfsNodeAnimator(treeModel).also {
      Disposer.register(this, it)
    }
    nodeAnimator.setRepainter { _, rfsPath ->
      val treePath = treeModel.getTreePath(rfsPath) ?: return@setRepainter
      val pathBounds = myTree.getPathBounds(treePath) ?: return@setRepainter
      myTree.repaint(pathBounds)
    }

    PopupHandler.installPopupMenu(myTree, "Kafka.Actions", RfsActionPlaces.RFS_PANE_POPUP)

    myTree.addTreeSelectionListener {
      val rfsPath = it.path.lastDriverNode?.rfsPath ?: return@addTreeSelectionListener
      showDetailsComponent(rfsPath)
    }

    details.add(topicsController.getComponent(), KafkaGroupType.TOPIC.name)
    details.add(topicInfoController.getComponent(), KafkaGroupType.TOPIC_DETAIL.name)
    details.add(consumerGroupsController.getComponent(), KafkaGroupType.CONSUMER_GROUP.name)
    registryController?.let {
      details.add(it.getComponent(), KafkaGroupType.SCHEMA_REGISTRY_GROUP.name)
    }
    schemaInfoController?.let {
      details.add(it.getComponent(), KafkaGroupType.SCHEMA_DETAIL.name)
    }

    showDetailsComponent(KafkaDriver.topicPath)

    val createProducer = JButton(KafkaMessagesBundle.message("create.producer.action.title")).apply {
      addActionListener {
        openProducer()
        KafkaUsagesCollector.openProducerEvent.log(project)
      }
    }

    val createConsumer = JButton(KafkaMessagesBundle.message("create.consumer.action.title")).apply {
      addActionListener {
        FileEditorManager.getInstance(project).openFile(createConsumerFile(), true)
        KafkaUsagesCollector.openConsumerEvent.log(project)
      }
    }

    val createProducerAndConsumer = JButton(KafkaMessagesBundle.message("create.producer.and.consumer.action.title")).apply {
      addActionListener {
        val producerEditor = openProducer()
        val consumerFile = createConsumerFile()
        val tabsDataProvider = if (producerEditor.size != 1) null
        else
          (SwingUtilities.getAncestorOfClass(JBTabs::class.java, producerEditor[0].component) as? JBTabs)?.dataProvider
        val window = if (tabsDataProvider == null) null else EditorWindow.DATA_KEY.getData(tabsDataProvider)
        if (window == null) {
          FileEditorManager.getInstance(project).openFile(consumerFile, true)
        }
        else {
          window.split(SwingConstants.VERTICAL, true, consumerFile, true)
          KafkaUsagesCollector.openProducerAndConsumerEvent.log(project)
        }
      }
    }

    val scroll = JBScrollPane(myTree).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val leftPanel = JPanel(BorderLayout()).apply {
      add(scroll, BorderLayout.CENTER)
      add(panel {
        row {
          cell(createProducer).align(Align.FILL).resizableColumn().customize(UnscaledGaps.EMPTY)
        }.bottomGap(BottomGap.NONE).topGap(TopGap.NONE)
        row {
          cell(createConsumer).align(Align.FILL).resizableColumn().customize(UnscaledGaps.EMPTY)
        }.bottomGap(BottomGap.NONE).topGap(TopGap.NONE)
        row {
          cell(createProducerAndConsumer).align(Align.FILL).resizableColumn().customize(UnscaledGaps.EMPTY)
        }.bottomGap(BottomGap.NONE).topGap(TopGap.NONE)
      }, BorderLayout.SOUTH)
    }

    return OnePixelSplitter().apply {
      proportion = 0.01f
      dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
      firstComponent = leftPanel
      secondComponent = details
    }
  }

  private fun setErrorPanel(exception: Throwable) {
    if (prevError == exception)
      return
    prevError = exception
    errorPanel.removeAll()
    errorPanel.add(RfsEditorErrorPanel(exception, this), BorderLayout.CENTER)
    errorPanel.revalidate()
    errorPanel.repaint()

    dataManager.activeComponentLabel.text = ""
  }

  private fun updateMainPanel(exception: Throwable?) {
    if (exception == null) {
      isNormalView.set(true)
      isErrorView.set(false)
    }
    else {
      isNormalView.set(false)
      isErrorView.set(true)
      setErrorPanel(exception)
    }
  }

  companion object {
    val DATA_MANAGER: DataKey<KafkaDataManager> = DataKey.create("kafka.data.manager")
    val RFS_PATH: DataKey<RfsPath> = DataKey.create("bdt.rfs.path")

    val AnActionEvent.dataManager
      get() = dataContext.getData(DATA_MANAGER)!!

    val AnActionEvent.rfsPath
      get() = dataContext.getData(RFS_PATH)
  }
}