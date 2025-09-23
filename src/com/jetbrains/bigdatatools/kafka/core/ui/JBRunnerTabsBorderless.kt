package io.confluent.kafka.core.ui

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.JBTabsBorder
import java.awt.*

/** Descendant of JBRunnerTabs that prevents drawing of vertical line on a left border. */
open class JBRunnerTabsBorderless(project: Project?, parent: Disposable) : JBRunnerTabs(project, parent) {
  override fun createTabBorder(): JBTabsBorder = JBRunnerTabsBorder(this)
}

private class JBRunnerTabsBorder(tabs: JBRunnerTabs) : JBTabsBorder(tabs) {
  override val effectiveBorder: Insets
    get() = Insets(tabs.borderThickness, 0, 0, 0)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (tabs.isEmptyVisible) return
    tabs.tabPainter.paintBorderLine(g as Graphics2D, tabs.borderThickness,
                                    Point(x, y + tabs.headerFitSize!!.height),
                                    Point(x + width, y + tabs.headerFitSize!!.height))
  }
}