package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.Optional
import javax.swing.JTable
import javax.swing.SwingUtilities

@TestApplication
class KafkaRecordsOutputTest {

    private lateinit var disposable: com.intellij.openapi.Disposable
    private lateinit var project: Project
    private lateinit var output: KafkaRecordsOutput

    @BeforeEach
    fun setUp() {
        SwingUtilities.invokeAndWait {
            disposable = Disposer.newDisposable("KafkaRecordsOutputTest")
            project = mock()
            output = KafkaRecordsOutput(project, isProducer = false)
            Disposer.register(disposable, output)
        }
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeAndWait { Disposer.dispose(disposable) }
    }

    private fun successRecord(
        topic: String = "t",
        partition: Int = 0,
        offset: Long = 0L,
        timestamp: Long = 0L,
        key: Any? = "k",
        value: Any? = "v",
    ): KafkaRecord {
        val rec = ConsumerRecord(
            topic, partition, offset, timestamp,
            TimestampType.CREATE_TIME, 1, 1,
            key, value, RecordHeaders(), Optional.empty<Int>(),
        )
        return KafkaRecord.createFor(
            KafkaFieldType.STRING, KafkaFieldType.STRING,
            KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
            Result.success(rec as ConsumerRecord<Any, Any>),
        )
    }

    private fun errorRecord(): KafkaRecord = KafkaRecord(
        keyType = KafkaFieldType.STRING,
        valueType = KafkaFieldType.STRING,
        error = RuntimeException("boom"),
        key = null, value = null,
        topic = "", partition = -1, offset = -1, duration = -1, timestamp = 0L,
        keySize = 0, valueSize = 0,
        headers = emptyList<Property>(),
        keyFormat = KafkaRegistryFormat.UNKNOWN,
        valueFormat = KafkaRegistryFormat.UNKNOWN,
    )

    @Nested
    inner class Construction {

        @Test
        fun `consumer construction exposes data and details panels`() {
            assertNotNull(output.dataPanel)
            assertNotNull(output.detailsPanel)
        }

        @Test
        fun `producer variant constructs without throwing`() {
            SwingUtilities.invokeAndWait {
                val producerScope = Disposer.newDisposable("producer")
                try {
                    val producer = KafkaRecordsOutput(project, isProducer = true)
                    Disposer.register(producerScope, producer)
                    assertNotNull(producer.dataPanel)
                } finally {
                    Disposer.dispose(producerScope)
                }
            }
        }
    }

    @Nested
    inner class TableModel {

        @Test
        fun `addBatchRows appends elements via outputModel`() {
            val records = listOf(successRecord(offset = 1L), successRecord(offset = 2L))
            SwingUtilities.invokeAndWait { output.addBatchRows(pollTime = 5L, elements = records) }
            // addBatch flushes via invokeLater — drain the queue.
            SwingUtilities.invokeAndWait { /* flush */ }

            val elements = output.getElements()
            assertEquals(2, elements.size)
            assertEquals(1L, elements[0].offset)
            assertEquals(2L, elements[1].offset)
        }

        @Test
        fun `addError appends a single error record`() {
            SwingUtilities.invokeAndWait { output.addError(errorRecord()) }
            SwingUtilities.invokeAndWait { /* flush */ }

            val elements = output.getElements()
            assertEquals(1, elements.size)
            assertNotNull(elements.single().error)
        }

        @Test
        fun `replace overwrites prior contents`() {
            SwingUtilities.invokeAndWait { output.addBatchRows(0L, listOf(successRecord(offset = 1L))) }
            SwingUtilities.invokeAndWait { /* flush */ }

            val replacement = listOf(successRecord(offset = 99L))
            SwingUtilities.invokeAndWait { output.replace(replacement) }

            val elements = output.getElements()
            assertEquals(1, elements.size)
            assertEquals(99L, elements.single().offset)
        }

        @Test
        fun `setMaxRows enforces eviction on subsequent batches`() {
            SwingUtilities.invokeAndWait {
                output.setMaxRows(2)
                output.addBatchRows(0L, listOf(successRecord(offset = 1L), successRecord(offset = 2L), successRecord(offset = 3L)))
            }
            SwingUtilities.invokeAndWait { /* flush */ }

            val elements = output.getElements()
            assertEquals(2, elements.size)
            // Last two records survive; the first is evicted.
            assertEquals(2L, elements[0].offset)
            assertEquals(3L, elements[1].offset)
        }

        @Test
        fun `getElements returns empty list on a fresh output`() {
            assertTrue(output.getElements().isEmpty())
        }
    }

    @Nested
    inner class ToolbarActions {

        private fun privateAction(name: String): AnAction {
            val field = KafkaRecordsOutput::class.java.getDeclaredField(name)
            field.isAccessible = true
            return field.get(output) as AnAction
        }

        @Test
        fun `searchExpandAction toggles state and updates icon presentation`() {
            val action = privateAction("searchExpandAction")
            val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)

            SwingUtilities.invokeAndWait { action.update(event) }
            val collapsedIcon = event.presentation.icon

            SwingUtilities.invokeAndWait { action.actionPerformed(event) }
            SwingUtilities.invokeAndWait { action.update(event) }
            val expandedIcon = event.presentation.icon

            // The two states render different icons.
            assertTrue(collapsedIcon !== expandedIcon)
        }

        @Test
        fun `searchAction createCustomComponent embeds the search field`() {
            val action = privateAction("searchAction")
            val customComponentAction = action as CustomComponentAction
            val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
            var comp: javax.swing.JComponent? = null
            SwingUtilities.invokeAndWait {
                comp = customComponentAction.createCustomComponent(event.presentation, "test")
            }
            // Default (collapsed) preferred size returns the embedded search field's preferred size.
            assertNotNull(comp!!.preferredSize)
            assertTrue(comp!!.preferredSize.width >= 0)
        }
    }

    @Nested
    inner class Selection {

        private fun outputJTable(): JTable =
            UIUtil.findComponentOfType(output.dataPanel, JTable::class.java)
                ?: error("output JTable not found in dataPanel")

        @Test
        fun `row selection invokes the selection listener without forcing details init`() {
            SwingUtilities.invokeAndWait {
                output.addBatchRows(0L, listOf(successRecord(offset = 1L), successRecord(offset = 2L)))
            }
            SwingUtilities.invokeAndWait { /* flush */ }
            SwingUtilities.invokeAndWait {
                outputJTable().selectionModel.setSelectionInterval(0, 0)
                outputJTable().selectionModel.clearSelection()
            }
            // No exception = listener handled both selected and -1 paths.
        }
    }

    @Nested
    inner class Lifecycle {

        @Test
        fun `stop without prior start does not throw`() {
            assertDoesNotThrow { SwingUtilities.invokeAndWait { output.stop() } }
        }

        @Test
        fun `start followed by stop disposes the loading decorator cleanly`() {
            SwingUtilities.invokeAndWait {
                output.start()
                output.stop()
            }
        }

        @Test
        fun `getElapsedTimeMs returns a non-negative value after start`() {
            SwingUtilities.invokeAndWait { output.start() }
            assertTrue(output.getElapsedTimeMs() >= 0)
        }

        @Test
        fun `dispose is idempotent`() {
            assertDoesNotThrow {
                SwingUtilities.invokeAndWait {
                    Disposer.dispose(output)
                    // Double-disposing through the parent scope in tearDown is a no-op.
                }
            }
        }
    }
}
