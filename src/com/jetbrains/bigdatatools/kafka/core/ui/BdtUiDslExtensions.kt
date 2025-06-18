package com.jetbrains.bigdatatools.kafka.core.ui

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.kafka.core.settings.fields.WrappedNamedComponent
import javax.swing.JComponent
import javax.swing.JLabel

/** Adds label and a component to panel row. Component will fill entire space horizontally. */
fun Panel.row(component: WrappedNamedComponent<*>): Row = row(component.labelComponent) {
  cell(component.getComponent()).align(AlignX.FILL)
}

fun Panel.row(@NlsContexts.Label label: String, component: JComponent): Row = row(label) {
  cell(component).align(AlignX.FILL)
}

fun Panel.row(label: JLabel, component: JComponent): Row = row(label) {
  cell(component).align(AlignX.FILL)
}

fun Panel.row(component: JComponent): Row = row {
  cell(component)
}

fun Panel.block(component: JComponent): Row = row {
  cell(component).align(Align.FILL).resizableColumn()
}

fun Panel.shortRow(component: WrappedNamedComponent<*>): Row = row(component.labelComponent) {
  cell(component.getComponent())
}


fun <S, T, C : ComboBox<T>> Cell<C>.bindUpdateCollection(prop: ObservableMutableProperty<S>, calcValues: (S) -> List<T>): Cell<C> {
  prop.afterChange {
    val selectedItem = component.item
    val newCollections = calcValues(it)
    component.removeAllItems()
    newCollections.forEach {
      component.addItem(it)
    }
    component.item = selectedItem
  }
  return this
}