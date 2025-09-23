package io.confluent.kafka.core.ui

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container

fun Container.removeNorthComponent() = removeBorderLayoutComponent(BorderLayout.NORTH)

fun Container.removeCenterComponent() = removeBorderLayoutComponent(BorderLayout.CENTER)

fun Container.removeLineStartComponent() = removeBorderLayoutComponent(BorderLayout.LINE_START)

fun Container.removeBorderLayoutComponent(constraints: Any) {
  val layout = this.layout as? BorderLayout ?: return
  val currentComponent = layout.getLayoutComponent(constraints)
  currentComponent?.let { remove(it) }
}

fun Container.setLineStartComponent(component: Component?) = setBorderLayoutComponent(component, BorderLayout.LINE_START)

fun Container.setLineEndComponent(component: Component?) = setBorderLayoutComponent(component, BorderLayout.LINE_END)

fun Container.setSouthComponent(component: Component?) = setBorderLayoutComponent(component, BorderLayout.SOUTH)

fun Container.setNorthComponent(component: Component?) = setBorderLayoutComponent(component, BorderLayout.NORTH)

fun Container.setCenterComponent(component: Component?) = setBorderLayoutComponent(component, BorderLayout.CENTER)

fun Container.setBorderLayoutComponent(component: Component?, constraints: Any) {
  val layout = this.layout as? BorderLayout ?: return

  val currentComponent = layout.getLayoutComponent(constraints)
  currentComponent?.let { remove(it) }

  component?.let { add(it, constraints) }
}