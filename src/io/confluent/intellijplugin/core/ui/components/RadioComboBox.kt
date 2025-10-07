package io.confluent.intellijplugin.core.ui.components

import com.intellij.ui.components.RadioButton
import com.intellij.ui.scale.JBUIScale
import io.confluent.intellijplugin.core.settings.components.RenderableEntity
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.JPanel

/**
 * Radio button group of one or more element, that looks as RadioGroup, but actually displays a state of boolean field.
 */
class RadioComboBox<E : RenderableEntity>(private val items: Array<E>, selectedItem: E) {
    private val component = JPanel()
    private val group = ButtonGroup()
    private val listeners = mutableListOf<(newValue: E) -> Unit>()

    private var selectedItemField = selectedItem

    var selectedItem: E
        get() = selectedItemField
        set(value) {
            selectedItemField = value
            group.elements.toList()[items.indexOf(value)]?.isSelected = true
            valueChanged(value)
        }

    init {
        var first = true
        items.forEach {
            val radioButton = RadioButton(it.title).apply {
                actionCommand = it.id
                if (selectedItemField == it) {
                    isSelected = true
                }
                addItemListener { e ->
                    if (e.stateChange == ItemEvent.SELECTED) {
                        selectedItemField = it
                        valueChanged(it)
                    }
                }
            }

            if (first) {
                first = false
            } else {
                component.add(Box.createRigidArea(Dimension(JBUIScale.scale(5), 0)))
            }

            component.add(radioButton)
            group.add(radioButton)
        }
    }

    fun getComponent() = component

    private fun valueChanged(newValue: E) {
        listeners.forEach { it.invoke(newValue) }
    }

    fun addItemListener(listener: (newValue: E) -> Unit) {
        listeners += listener
    }

    @Suppress("unused")
    fun removeItemListener(listener: (newValue: E) -> Unit) {
        listeners -= listener
    }
}