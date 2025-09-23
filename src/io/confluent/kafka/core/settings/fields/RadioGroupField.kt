package io.confluent.kafka.core.settings.fields

import com.intellij.ui.components.RadioButton
import com.intellij.ui.scale.JBUIScale
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.components.RenderableEntity
import io.confluent.kafka.core.settings.connections.ConnectionData
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty1

fun interface ChangeListener {
  fun onChange()
}

class RadioGroupField<D : ConnectionData, E : RenderableEntity>(
  private val prop: KMutableProperty1<D, E>,
  key: ModificationKey,
  initSettings: D,
  private val items: Collection<E>,
) : WrappedNamedComponent<D>(key) {

  private val component = JPanel()
  private val group = ButtonGroup()
  private val listeners = mutableListOf<ChangeListener>()

  init {
    val targetItem = prop.get(initSettings)

    var first = true
    items.forEach {
      val radioButton = RadioButton(it.title).apply {
        actionCommand = it.id
        if (targetItem == it) {
          isSelected = true
        }
        addItemListener { e -> if (e.stateChange == ItemEvent.SELECTED) valueChanged() }
      }

      if (first) {
        first = false
      }
      else {
        component.add(Box.createRigidArea(Dimension(JBUIScale.scale(5), 0)))
      }

      component.add(radioButton)
      group.add(radioButton)
    }
  }

  private fun valueChanged() {
    listeners.forEach { it.onChange() }
  }

  fun addItemListener(listener: ChangeListener) {
    listeners += listener
  }

  override fun getValue() = items.first { group.selection.actionCommand == it.id }

  fun setValue(item: E) {
    group.selection.actionCommand = item.id
    val i = items.indexOfFirst { it.id == item.id }
    group.elements.toList()[i].isSelected = true
  }

  override fun getComponent() = component

  override fun apply(conn: D) = prop.set(conn, getValue())

  override fun isModified(conn: D) = prop.get(conn) != getValue()
}