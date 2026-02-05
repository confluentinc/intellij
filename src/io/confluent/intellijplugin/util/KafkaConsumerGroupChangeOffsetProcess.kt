package io.confluent.intellijplugin.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.CalendarView
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.consumer.models.ConsumerStartWith
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.data.BaseClusterDataManager
import kotlinx.coroutines.*
import java.awt.Component
import java.awt.Container
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.util.*

internal class KafkaConsumerGroupChangeOffsetProcess(
    val project: Project,
    val dataManager: BaseClusterDataManager,
    val consumerGroup: String
) {
    private val coroutineScope = dataManager.driver.coroutineScope
    private val startSpecificDate = CalendarView()
    private val startType = AtomicProperty(ConsumerStartType.NOW)
    private val topic = AtomicProperty(KafkaMessagesBundle.message("all.topics"))
    private val startOffset = AtomicProperty(0)

    fun showAndUpdate() {
        coroutineScope.launch {
            withBackgroundProgress(project, KafkaMessagesBundle.message("task.change.offset"), cancellable = true) {
                try {
                    internalChangeOffsetWithDialog()
                } catch (t: Throwable) {
                    withContext(Dispatchers.EDT) {
                        RfsNotificationUtils.showExceptionMessage(project, t)
                    }
                }
            }
        }
    }

    private suspend fun internalChangeOffsetWithDialog() {
        val consumerGroupOffset = withContext(Dispatchers.IO) {
            dataManager.loadConsumerGroupOffset(consumerGroup)
        }

        val topics: List<String> = consumerGroupOffset.map { it.topic }.distinct()
        val allTopicName = KafkaMessagesBundle.message("all.topics")
        val allSelectableTopics = if (topics.size <= 1)
            topics
        else
            listOf(allTopicName) + topics

        val res = createAndShowDialog(allSelectableTopics)
        if (res)
            return

        val selectedNames = if (topic.get() == allTopicName) {
            topics
        } else {
            listOf(topic.get())
        }

        val topicInfos = selectedNames.map {
            coroutineScope.async {
                dataManager.loadTopicInfo(it)
            }
        }.awaitAll()
        val partitions = topicInfos.flatMap { it.partitionList }
        val startWith = ConsumerStartWith(
            type = startType.get(),
            time = startSpecificDate.date?.time,
            offset = startOffset.get().toLong(),
            consumerGroup = null
        )

        val partitionsWithNewOffsets = KafkaOffsetUtils.calculateOffsets(partitions, startWith, dataManager)
        dataManager.resetOffsets(consumerGroup, partitionsWithNewOffsets)
    }

    private fun doOnChildVisibilityChange(container: Component, runnable: () -> Unit) {
        if (container !is Container) {
            return
        }

        val componentListener = object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) = Unit
            override fun componentMoved(e: ComponentEvent?) = Unit
            override fun componentShown(e: ComponentEvent?) = runnable()
            override fun componentHidden(e: ComponentEvent?) = runnable()
        }

        val stack = Stack<Component>()
        container.components.forEach { stack.push(it) }
        while (!stack.isEmpty()) {
            val component = stack.pop()
            component.addComponentListener(componentListener)
            if (component is Container) {
                component.components.forEach { stack.push(it) }
            }
        }
    }

    private suspend fun createAndShowDialog(allSelectableTopics: List<String>) = withContext(Dispatchers.EDT) {
        val builder = DialogBuilder().apply {
            addOkAction()
            addCancelAction()
            title(KafkaMessagesBundle.message("action.kafka.ResetOffsetsAction.text"))
            centerPanel(createPanel(allSelectableTopics))
        }
        doOnChildVisibilityChange(builder.centerPanel) {
            invokeLater {
                builder.dialogWrapper.pack()
            }
        }
        !builder.showAndGet()
    }

    private fun createPanel(allSelectableTopics: List<String>): DialogPanel {
        return panel {
            row(KafkaMessagesBundle.message("consumer.record.topic")) {
                comboBox(allSelectableTopics).align(AlignX.FILL).resizableColumn().bindItem(topic)
            }
            row(KafkaMessagesBundle.message("consumer.group.dialog.change.offset.strategy.label")) {
                comboBox(
                    ConsumerStartType.entries - ConsumerStartType.CONSUMER_GROUP,
                    CustomListCellRenderer<ConsumerStartType> { it.title })
                    .bindItem(startType).align(AlignX.FILL).resizableColumn()
            }
            row(KafkaMessagesBundle.message("consumer.group.dialog.change.offset.datetime.label")) {
                cell(startSpecificDate).align(AlignX.FILL).resizableColumn()
            }.visibleIf(startType.equalsTo(ConsumerStartType.SPECIFIC_DATE))
            row(KafkaMessagesBundle.message("consumer.group.dialog.change.offset.offset.label")) {
                textField().bindIntText(startOffset).align(AlignX.FILL).resizableColumn().validationOnInput {
                    val range = LongRange(0, Long.MAX_VALUE - 1)
                    val value = it.text.toLongOrNull()
                    when (value) {
                        null -> error(UIBundle.message("please.enter.a.number"))
                        !in range -> error(
                            UIBundle.message("please.enter.a.number.from.0.to.1", range.first, range.last)
                        )

                        else -> null
                    }
                }
            }.visibleIf(
                startType.equalsTo(ConsumerStartType.OFFSET)
                    .or(startType.equalsTo(ConsumerStartType.LATEST_OFFSET_MINUS_X))
            )
        }
    }
}

