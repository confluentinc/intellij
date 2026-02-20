package io.confluent.intellijplugin.common.editor

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ListTableModelTest {

    private fun createModel(
        data: MutableList<String> = mutableListOf(),
        columnNames: List<String> = listOf("Value"),
        columnMapper: (String, Int) -> Any? = { item, _ -> item }
    ) = ListTableModel(data, columnNames, columnMapper)

    @Nested
    @DisplayName("getValueAt")
    inner class GetValueAt {

        @Test
        fun `should return value for valid index`() {
            val model = createModel(mutableListOf("a", "b", "c"))

            assertEquals("a", model.getValueAt(0, 0))
            assertEquals("b", model.getValueAt(1, 0))
            assertEquals("c", model.getValueAt(2, 0))
        }

        @Test
        fun `should return null for out-of-bounds row index`() {
            val model = createModel(mutableListOf("only"))

            assertNull(model.getValueAt(5, 0))
            assertNull(model.getValueAt(-1, 0))
        }

        @Test
        fun `single-index overload should return null for out-of-bounds`() {
            val model = createModel(mutableListOf("only"))

            assertEquals("only", model.getValueAt(0))
            assertNull(model.getValueAt(1))
            assertNull(model.getValueAt(-1))
        }
    }

    @Nested
    @DisplayName("addElement")
    inner class AddElement {

        @Test
        fun `should append elements`() {
            val model = createModel()

            model.addElement("first")
            assertEquals(1, model.rowCount)
            assertEquals("first", model.getValueAt(0, 0))

            model.addElement("second")
            assertEquals(2, model.rowCount)
            assertEquals("second", model.getValueAt(1, 0))
        }

        @Test
        fun `should evict oldest when maxElementsCount is exceeded`() {
            val model = createModel()
            model.maxElementsCount = 2

            model.addElement("a")
            model.addElement("b")
            assertEquals(2, model.rowCount)

            model.addElement("c")
            assertEquals(2, model.rowCount)
            assertEquals("b", model.getValueAt(0, 0))
            assertEquals("c", model.getValueAt(1, 0))
        }
    }

    @Nested
    @DisplayName("Concurrent access")
    inner class RaceCondition {

        @RepeatedTest(10)
        fun `should not throw when reading during concurrent modification`() {
            val model = createModel(mutableListOf("a", "b", "c"))
            val failure = AtomicReference<Throwable>()
            val barrier = CyclicBarrier(2)
            val iterations = 1000

            val reader = thread(start = false) {
                try {
                    barrier.await()
                    repeat(iterations) {
                        val lastIndex = model.rowCount - 1
                        if (lastIndex >= 0) {
                            model.getValueAt(lastIndex, 0)
                        }
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }

            val writer = thread(start = false) {
                try {
                    barrier.await()
                    repeat(iterations) {
                        model.addElement("item-$it")
                        model.clear()
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }

            reader.start()
            writer.start()
            reader.join(5000)
            writer.join(5000)

            failure.get()?.let { throw AssertionError("Race condition caused exception", it) }
        }
    }

    @Nested
    @DisplayName("clear")
    inner class ClearAndElements {

        @Test
        fun `clear should empty the model`() {
            val model = createModel(mutableListOf("a", "b"))

            model.clear()

            assertEquals(0, model.rowCount)
            assertEquals(emptyList<String>(), model.elements())
        }

    }
}