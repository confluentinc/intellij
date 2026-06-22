package io.confluent.intellijplugin.consumer.editor

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.isNull
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.enteredTextSatisfies
import com.intellij.unscramble.AnalyzeStacktraceUtil
import io.confluent.intellijplugin.common.editor.PropertiesTable
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.core.rfs.driver.metainfo.components.SelectableLabel
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.ui.ComponentColoredBorder
import io.confluent.intellijplugin.core.ui.DarculaTextAreaBorder
import io.confluent.intellijplugin.core.util.SizeUtils
import io.confluent.intellijplugin.core.util.TimeUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Toolkit
import javax.swing.*
import kotlin.math.max

internal class KafkaRecordDetails(project: Project, parentDisposable: Disposable) {

    private val topicField = SelectableLabel("")

    private lateinit var keyLoadFileLinkRow: Row
    private lateinit var valueLoadFileLinkRow: Row

    private val keySection = RecordFieldSection(project, parentDisposable, "key", KafkaFieldType.STRING)
    private val valueSection = RecordFieldSection(project, parentDisposable, "value", KafkaFieldType.JSON)

    private val headers = PropertiesTable(emptyList(), isEditable = false)
    private val partition = SelectableLabel("")
    private val offset = SelectableLabel("")

    private val timestamp = SelectableLabel("")

    private val keySize = SelectableLabel("")
    private val valueSize = SelectableLabel("")

    private val keyTypeLabel = SelectableLabel("")
    private val valueTypeLabel = SelectableLabel("")

    private val error = AtomicProperty<String?>(null)

    private val errorConsole = ConsoleViewUtil.setupConsoleEditor(project, false, false).apply {

        this.document.setText(error.get() ?: "")
        error.afterChange {
            this.document.setText(it ?: "")
        }
    }
    private val errorPanel = errorConsole.scrollPane.apply {
        border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
        preferredSize = Dimension(
            preferredSize.width,
            max(300, Toolkit.getDefaultToolkit().screenSize.height / 5)
        )
    }

    private val emptyStatePanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
        add(
            JLabel(
                "<html>${KafkaMessagesBundle.message("consumer.details.empty")}</html>",
                SwingConstants.CENTER
            ).apply {
                isEnabled = false
            })
    }

    @Suppress("DialogTitleCapitalization")
    private val detailsPanel = JBScrollPane(panel {
        error.get()?.let { errorString ->
            row(KafkaMessagesBundle.message("consumer.record.error")) {
                link(IdeBundle.message("unscramble.dialog.title")) {
                    AnalyzeStacktraceUtil.addConsole(
                        project,
                        null,
                        IdeBundle.message("tab.title.stacktrace"),
                        errorString
                    )
                }
            }
            row {
                cell(errorPanel).align(AlignX.FILL)
            }
        }

        val keyGroup = collapsibleGroup(title = KafkaMessagesBundle.message("consumer.record.key"), indent = false) {
            row {
                cell(keySection.viewerTypeCombo).align(AlignX.RIGHT)
            }
            keyLoadFileLinkRow = row {
                link(KafkaMessagesBundle.message("producer.config.link.load.file")) {
                    try {
                        keySection.loadBinaryFile()
                    } catch (t: Throwable) {
                        RfsNotificationUtils.showExceptionMessage(project, t)
                    }
                }
            }
            row {
                cell(keySection.editorComponent).resizableColumn().align(AlignX.FILL)
            }
        }
        keyGroup.expanded = true
        keyGroup.visibleIf(error.isNull())
        keyGroup.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)

        val valueGroup = collapsibleGroup(title = KafkaMessagesBundle.message("consumer.record.value"), indent = false) {
            row {
                cell(valueSection.viewerTypeCombo).align(AlignX.RIGHT)
            }
            valueLoadFileLinkRow = row {
                link(KafkaMessagesBundle.message("producer.config.link.load.file")) {
                    valueSection.loadBinaryFile()
                }
            }
            row {
                cell(valueSection.editorComponent).resizableColumn().align(AlignX.FILL)
            }
        }
        valueGroup.expanded = true
        valueGroup.visibleIf(error.isNull())
        valueGroup.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)

        val headerGroup = collapsibleGroup(title = KafkaMessagesBundle.message("record.info.headers"), indent = false) {
            row {
                cell(headers.getComponent()).align(AlignX.FILL).resizableColumn()
            }
        }
        headerGroup.expanded = false
        headerGroup.topGap(TopGap.SMALL).bottomGap(BottomGap.NONE)

        val metainfoGroup =
            collapsibleGroup(title = KafkaMessagesBundle.message("record.info.metadata"), indent = true) {
                row(KafkaMessagesBundle.message("consumer.record.topic")) { cell(topicField).align(AlignX.FILL) }
                row(KafkaMessagesBundle.message("consumer.record.partition")) { cell(partition).align(AlignX.FILL) }
                row(KafkaMessagesBundle.message("consumer.record.offset")) { cell(offset).align(AlignX.FILL) }
                    .visibleIf(offset.enteredTextSatisfies { it.isNotEmpty() })
                row(KafkaMessagesBundle.message("consumer.timestamp.label")) { cell(timestamp).align(AlignX.FILL) }
                row(KafkaMessagesBundle.message("consumer.record.keysize")) { cell(keySize).align(AlignX.FILL) }
                row(KafkaMessagesBundle.message("consumer.record.valuesize")) { cell(valueSize).align(AlignX.FILL) }
                row(KafkaMessagesBundle.message("label.key.type")) { cell(keyTypeLabel).align(AlignX.FILL) }
                row(KafkaMessagesBundle.message("label.value.type")) { cell(valueTypeLabel).align(AlignX.FILL) }
            }

        metainfoGroup.expanded = false
        metainfoGroup.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)

    }).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    val component = JPanel(CardLayout()).apply {
        add(emptyStatePanel, "emptyState")
        add(detailsPanel, "details")
    }

    init {
        keySection.viewerTypeCombo.addActionListener {
            keySection.updateEditorLanguage()
            keyLoadFileLinkRow.visible(keySection.isLoadFileLinkVisible())
            emptyStatePanel.revalidate()
        }

        valueSection.viewerTypeCombo.addActionListener {
            valueSection.updateEditorLanguage()
            valueLoadFileLinkRow.visible(valueSection.isLoadFileLinkVisible())
            emptyStatePanel.revalidate()
        }

        headers.getComponent().border =
            IdeBorderFactory.createBorder(SideBorder.RIGHT or SideBorder.BOTTOM or SideBorder.LEFT)

        update(null)

        keyLoadFileLinkRow.visible(keySection.isLoadFileLinkVisible())
        valueLoadFileLinkRow.visible(valueSection.isLoadFileLinkVisible())
    }

    fun update(row: KafkaRecord?) {
        error.set(row?.error?.stackTraceToString()?.replace("\r\n", "\n"))

        (component.layout as? CardLayout)?.show(component, if (row == null) "emptyState" else "details")

        if (row == null) {
            keySection.resetType()
            valueSection.resetType()
            return
        }

        keySection.setText(row.keyText ?: "", row.keyType)
        valueSection.setText(row.valueText ?: "", row.valueType)

        topicField.text = row.topic
        partition.text = if (row.partition >= 0) row.partition.toString() else ""
        offset.text = if (row.offset >= 0) row.offset.toString() else ""
        timestamp.text = TimeUtils.unixTimeToString(row.timestamp)
        keySize.text = SizeUtils.toString(maxOf(row.keySize, 0))
        valueSize.text = SizeUtils.toString(maxOf(row.valueSize, 0))
        keyTypeLabel.text = row.keyType.title
        valueTypeLabel.text = row.valueType.title

        headers.properties = row.headers.toMutableList()

        // Key and value Fields could contain multiline JSON
        detailsPanel.revalidate()
    }
}
