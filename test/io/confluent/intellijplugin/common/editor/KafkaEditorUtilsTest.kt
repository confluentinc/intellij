package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestApplication
class KafkaEditorUtilsTest {

    private fun <T> createComboBox(items: List<T>): ComboBox<T> {
        val comboBox = ComboBox<T>()
        items.forEach { comboBox.addItem(it) }
        return comboBox
    }

    private fun <T> callUpdateComboBoxAndWait(
        comboBox: ComboBox<T>,
        dataSupplier: () -> Pair<List<T>?, Int?>
    ) {
        val latch = CountDownLatch(1)
        KafkaEditorUtils.updateComboBox(comboBox, { latch.countDown() }, dataSupplier)
        latch.await(5, TimeUnit.SECONDS)
    }

    @Nested
    inner class `updateComboBox` {

        @Test
        fun `should preserve selected item by identity when list is reordered`() {
            val comboBox = createComboBox(listOf("alpha", "beta", "gamma"))
            comboBox.selectedIndex = 1 // "beta"

            callUpdateComboBoxAndWait(comboBox) {
                listOf("gamma", "alpha", "beta") to null
            }

            assertEquals("beta", comboBox.selectedItem)
        }

        @Test
        fun `should preserve selected item when new items are added`() {
            val comboBox = createComboBox(listOf("alpha", "beta"))
            comboBox.selectedIndex = 1 // "beta"

            callUpdateComboBoxAndWait(comboBox) {
                listOf("alpha", "new-topic", "beta", "another-topic") to null
            }

            assertEquals("beta", comboBox.selectedItem)
        }

        @Test
        fun `should use selectedItemIndex when provided`() {
            val comboBox = createComboBox(listOf("alpha", "beta", "gamma"))
            comboBox.selectedIndex = 0 // "alpha"

            callUpdateComboBoxAndWait(comboBox) {
                listOf("gamma", "alpha", "beta") to 2
            }

            assertEquals("beta", comboBox.selectedItem)
        }

        @Test
        fun `should fall back to index 0 when previously selected item is removed`() {
            val comboBox = createComboBox(listOf("alpha", "beta", "gamma"))
            comboBox.selectedIndex = 1 // "beta"

            callUpdateComboBoxAndWait(comboBox) {
                listOf("alpha", "gamma", "delta") to null
            }

            // "beta" no longer exists, no explicit index — comboBox defaults to 0
            assertEquals("alpha", comboBox.selectedItem)
        }

        @Test
        fun `should not change selection when list is unchanged`() {
            val comboBox = createComboBox(listOf("alpha", "beta", "gamma"))
            comboBox.selectedIndex = 2 // "gamma"

            callUpdateComboBoxAndWait(comboBox) {
                listOf("alpha", "beta", "gamma") to null
            }

            assertEquals("gamma", comboBox.selectedItem)
        }
    }
}
