package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.JBTabs
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.ComponentController
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.MigPanel
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import net.miginfocom.layout.LC
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.*

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
class ClusterPageController(private val project: Project, private val connectionData: KafkaConnectionData) : ComponentController {
  private val dataManager = KafkaDataManager.getInstance(connectionData.innerId, project) ?: error("Data Manager is not initialized")

  private val topicsController = TopicsController(project, dataManager)
  private val consumerGroupsController = ConsumerGroupsController(dataManager)

  private val schemaRegistryController = if (dataManager.isKafkaRegistryEnabled)
    KafkaSchemaRegistryController(project, dataManager)
  else
    null

  private val detailsLayout = CardLayout()
  private val details = JPanel(detailsLayout)
  private val panel = createPanel()

  init {
    Disposer.register(this, topicsController)
    Disposer.register(this, consumerGroupsController)
    schemaRegistryController?.let { Disposer.register(this, it) }
  }

  override fun dispose() {}

  override fun getComponent() = panel

  private fun showDetails(selectedValue: ClusterControllerType) {
    detailsLayout.show(details, selectedValue.name)
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

  private fun createPanel(): JPanel {
    val model = DefaultListModel<ClusterControllerType>().also { model ->
      ClusterControllerType.values().forEach {
        if (it == ClusterControllerType.SCHEMA_REGISTRY_GROUP && schemaRegistryController == null) {
          return@forEach
        }
        model.addElement(it)
      }
    }

    val list = JBList(model).apply {
      val renderer = CustomListCellRenderer<ClusterControllerType> { it.value }
      renderer.border = BorderFactory.createEmptyBorder(5, 10, 5, 0)
      cellRenderer = renderer

      selectionMode = DefaultListSelectionModel.SINGLE_SELECTION
      selectedIndex = 0

      addListSelectionListener { e ->
        if (e.valueIsAdjusting)
          return@addListSelectionListener
        showDetails(selectedValue)
      }
    }

    details.add(topicsController.getComponent(), ClusterControllerType.TOPIC.name)
    details.add(consumerGroupsController.getComponent(), ClusterControllerType.CONSUMER_GROUP.name)

    schemaRegistryController?.let {
      details.add(schemaRegistryController.getComponent(), ClusterControllerType.SCHEMA_REGISTRY_GROUP.name)
    }
    showDetails(ClusterControllerType.TOPIC)

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

    val scroll = JBScrollPane(list).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val leftPanel = JPanel(BorderLayout()).apply {
      add(scroll, BorderLayout.CENTER)
      add(MigPanel(LC().insets("0").gridGapY("0").fillX().hideMode(3)).apply {
        row(createProducer)
        row(createConsumer)
        row(createProducerAndConsumer)
      }, BorderLayout.SOUTH)
    }

    return OnePixelSplitter().apply {
      proportion = 0.01f
      dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
      firstComponent = leftPanel
      secondComponent = details
    }
  }

  private enum class ClusterControllerType(val value: String) {
    TOPIC("Topics"),
    CONSUMER_GROUP("Consumers"),
    SCHEMA_REGISTRY_GROUP(KafkaMessagesBundle.message("settings.registry.tab"))
  }
}