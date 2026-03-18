package io.confluent.intellijplugin.registry.confluent.controller

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.StatusText
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.toolwindow.DetailsMonitoringController
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryAddSchemaDialog
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.BorderLayout
import javax.swing.JComponent

class KafkaTopicSchemaController(
    private val project: Project,
    private val dataManager: BaseClusterDataManager,
    private val viewType: TopicSchemaViewType
) : DetailsMonitoringController<String> {
    private var topicName: String? = null

    private val schemaController = KafkaSchemaController(project, dataManager).also {
        Disposer.register(this, it)
    }
    private val curComponent = JBPanelWithEmptyText(BorderLayout())
    private val internalComponent = schemaController.getComponent()
    private val loadingComponent = panel {
        row {
            label(KafkaMessagesBundle.message("confluent.cloud.details.schema.loading")).align(Align.CENTER)
        }.resizableRow()
    }

    private val listener = object : DataModelListener {
        override fun onChanged() {
            setDetailsId(topicName ?: "")
        }
    }

    init {
        init()
        setDetailsId(topicName ?: "")
    }

    override fun dispose() {
        dataManager.schemaRegistryModel?.removeListener(listener)
    }

    fun init() {
        setEmptyText()
        dataManager.schemaRegistryModel?.addListener(listener)
        dataManager.initRefreshSchemasIfRequired()
    }

    override fun getComponent(): JComponent = curComponent

    override fun setDetailsId(id: String) {
        topicName = id
        val schemaName = id + viewType.suffix

        // Always show loading first, then determine final state via listener
        setLoadingState()

        val schemaModel = dataManager.schemaRegistryModel
        val isInitialized = schemaModel?.isInitedByFirstTime ?: false

        if (isInitialized) {
            // Schemas already loaded, update immediately
            if (dataManager.schemaExists(schemaName))
                setSchemaForTopic(schemaName)
            else
                setEmptySchemaForTopic()
        }
        // Otherwise stay in loading state until listener fires
        curComponent.revalidate()
        curComponent.repaint()
    }

    private fun setLoadingState() {
        curComponent.removeAll()
        curComponent.emptyText.clear()
        curComponent.add(loadingComponent, BorderLayout.CENTER)
    }

    private fun setSchemaForTopic(schemaName: String) {
        curComponent.removeAll()
        curComponent.emptyText.clear()
        curComponent.add(internalComponent, BorderLayout.CENTER)
        schemaController.setDetailsId(schemaName)
    }

    private fun setEmptyText() {
        curComponent.emptyText.clear()
        curComponent.emptyText.isShowAboveCenter = false
    }

    private fun setEmptySchemaForTopic() {
        curComponent.removeAll()
        curComponent.emptyText.clear()
        curComponent.emptyText.appendText(
            KafkaMessagesBundle.message("topic.schema.empty.text", viewType.title.lowercase()),
            StatusText.DEFAULT_ATTRIBUTES
        )
        curComponent.emptyText.appendSecondaryText(
            KafkaMessagesBundle.message("topic.schema.empty.text.create.link"),
            SimpleTextAttributes.LINK_ATTRIBUTES
        ) {
            val topicName = topicName ?: return@appendSecondaryText
            val dialog = KafkaRegistryAddSchemaDialog(project, dataManager)
            dialog.applySchemaName(topicName, viewType)
            dialog.topicField.isEnabled = false
            dialog.strategyCombobox.isEnabled = false
            dialog.keyValueCombobox.isEnabled = false
            dialog.subjectNameField.isEnabled = false
            dialog.show()
        }
        curComponent.emptyText.isShowAboveCenter = false
    }
}