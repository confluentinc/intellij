package com.jetbrains.bigdatatools.kafka.core.rfs.statistics.v2

import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.openapi.observable.util.whenFocusGained
import com.intellij.openapi.observable.util.whenFocusLost
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.ui.EditorTextField
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.Cell
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.settings.components.RenderableEntity
import com.jetbrains.bigdatatools.kafka.core.settings.fields.CredentialNamedField
import com.jetbrains.bigdatatools.kafka.core.settings.fields.WrappedComponent
import com.jetbrains.bigdatatools.kafka.core.ui.components.ConnectionPropertiesEditor
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.ItemEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JRadioButton
import javax.swing.text.JTextComponent

@Suppress("UNCHECKED_CAST")
object BdtStatisticUtils {
  fun attachActionCollector(field: Any, eventId: BaseEventId, index: AtomicInteger, type: BdtConnectionType) {
    when (field) {
      is StatisticInfoProvider -> {
        field.attachActionCollector(eventId, index, type)
      }
      is Cell<*> -> attachActionCollector(field.component, eventId, index, type)
      is JButton -> {
        val eventId2 = eventId as EventId2<Int, BdtConnectionType>
        field.addActionListener {
          eventId2.log(index.incrementAndGet(), type)
        }
      }
      is ComponentWithBrowseButton<*> -> {
        val eventId2 = eventId as EventId2<Int, BdtConnectionType>
        field.addActionListener {
          eventId2.log(index.incrementAndGet(), type)
        }
      }
      is List<*> -> {
        error("Cannot parse List")
      }
      else -> {
        error("unrecognized field type $field")
      }
    }
  }


  @Suppress("UNCHECKED_CAST")
  fun attachCollector(field: Any, eventId: BaseEventId, index: AtomicInteger, type: BdtConnectionType) {
    when (field) {
      is StatisticInfoProvider -> {
        field.attachCollector(eventId, index, type)
      }
      is CredentialNamedField<*> -> {
        attachCollector(field.getTextComponent(), eventId, index, type)
      }
      is WrappedComponent<*> -> {
        attachCollector(field.getComponent(), eventId, index, type)
      }
      is Cell<*> -> {
        attachCollector(field.component, eventId, index, type)
      }
      is ConnectionPropertiesEditor -> {
        attachCollector(field.fieldWithCompletion, eventId, index, type)
      }
      is EditorTextField -> {
        val eventId2 = eventId as EventId2<Int, BdtConnectionType>
        var text = ""
        field.addFocusListener(object : FocusListener {
          override fun focusGained(e: FocusEvent?) {
            text = field.text
          }

          override fun focusLost(e: FocusEvent?) {
            if (text != field.text) {
              (eventId2).log(index.incrementAndGet(), type)
            }
          }
        })
      }
      is ComponentWithBrowseButton<*> -> {
        attachCollector(field.childComponent, eventId, index, type)
      }
      is JTextComponent -> {
        val eventId2 = eventId as EventId2<Int, BdtConnectionType>
        var text = ""
        field.whenFocusGained {
          text = field.text
        }
        field.whenFocusLost {
          if (text != field.text) {
            (eventId2).log(index.incrementAndGet(), type)
          }
        }
      }
      is JComboBox<*> -> {
        when (eventId) {
          is EventId2<*, *> -> {
            val event = eventId as EventId2<Int, BdtConnectionType>
            field.addItemListener {
              if (it.stateChange == ItemEvent.SELECTED) {
                return@addItemListener
              }
              event.log(index.incrementAndGet(), type)
            }
          }
          is EventId3<*, *, *> -> {
            val event = eventId as EventId3<Int, BdtConnectionType, String>
            field.addItemListener {
              if (it.stateChange == ItemEvent.SELECTED) {
                return@addItemListener
              }

              val item = field.selectedItem
              val id = parseComboboxItem(item)
              id ?: return@addItemListener
              event.log(index.incrementAndGet(), type, id)
            }
          }
        }
      }
      is JCheckBox -> {
        when (eventId) {
          is EventId2<*, *> -> {
            val event = eventId as EventId2<Int, BdtConnectionType>
            field.addItemListener {
              event.log(index.incrementAndGet(), type)
            }
          }
          is EventId3<*, *, *> -> {
            val event = eventId as EventId3<Int, BdtConnectionType, Boolean>
            field.addItemListener {
              val item = field.isSelected
              event.log(index.incrementAndGet(), type, item)
            }
          }
        }
      }
      is RawCommandLineEditor -> {
        val event = eventId as EventId2<Int, BdtConnectionType>
        var text = ""
        field.textField.whenFocusGained {
          text = field.text
        }
        field.textField.whenFocusLost {
          if (text != field.text) {
            (event).log(index.incrementAndGet(), type)
          }
        }
      }
      is JRadioButton -> {
        val event = eventId as EventId2<Int, BdtConnectionType>
        field.addItemListener {
          if (field.isSelected) {
            (event).log(index.incrementAndGet(), type)
          }
        }
      }
      is List<*> -> {
        error("Cannot parse List")
      }
      else -> {
        error("unrecognized field type $field")
      }
    }
  }

  fun parseComboboxItem(item: Any?): String? {
    val id = when (item) {
      is String -> item
      is RenderableEntity -> item.id
      else -> item?.toString()
    }
    return id
  }
}