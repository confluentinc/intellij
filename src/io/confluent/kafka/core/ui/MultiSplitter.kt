package io.confluent.kafka.core.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.ui.util.minimumWidth
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

class MultiSplitter : JPanel(null) {

  private inner class MultiSplittable : Splittable {
    lateinit var divider: OnePixelDivider
    override fun getMinProportion(first: Boolean) = 0f
    override fun setProportion(proportion: Float) = this@MultiSplitter.dividerProportionsChanged(divider, proportion)
    override fun getOrientation() = false
    override fun setOrientation(verticalSplit: Boolean) = Unit
    override fun asComponent(): Component = this@MultiSplitter
    override fun setDragging(dragging: Boolean) = Unit
  }

  /** If this component set, then the other will tend to have preferred size, while this one will be expanded and collapsed freely. */
  var centralComponent: JComponent? = null

  private val proportions = mutableMapOf<OnePixelDivider, Float>()

  private val proportionsSaveScheduled = AtomicBoolean(false)

  var proportionsKey: String? = null

  private fun dividerProportionsChanged(divider: OnePixelDivider, proportion: Float) {
    proportions[divider] = proportion
    revalidate()
    repaint()

    val proportionsKey = proportionsKey
    if (proportionsKey != null && !proportionsSaveScheduled.getAndSet(true)) {
      // Save proportions.
      AppExecutorUtil.getAppScheduledExecutorService()
        .schedule({
                    val dividers = components.filterIsInstance<OnePixelDivider>()
                    val proportionsList = dividers.map { proportions[it].toString() }
                    PropertiesComponent.getInstance().setList(proportionsKey, proportionsList)
                    proportionsSaveScheduled.set(false)
                  }, 200, TimeUnit.MILLISECONDS)
    }
  }

  // Called on new component addition.
  private fun recalculateProportions() {
    proportions.clear()
    val dividers = components.filterIsInstance<OnePixelDivider>()

    val proportionsKey = proportionsKey
    if (proportionsKey != null) {
      val savedProportions = PropertiesComponent.getInstance().getList(proportionsKey)?.map { it.toFloat() }
      if (savedProportions != null && savedProportions.size == dividers.size) {
        dividers.forEachIndexed { index, divider ->
          proportions[divider] = savedProportions[index]
        }
        return
      }
    }

    dividers.forEachIndexed { index, divider ->
      proportions[divider] = (index + 1).toFloat() / (dividers.size + 1)
    }
  }

  override fun addImpl(comp: Component, constraints: Any?, index: Int) {
    if (components.lastOrNull() != null && components.lastOrNull() !is OnePixelDivider) {
      val splittable = MultiSplittable()
      val divider = OnePixelDivider(false, splittable)
      splittable.divider = divider
      super.addImpl(divider, constraints, -1)

      recalculateProportions()
    }

    super.addImpl(comp, constraints, -1)
  }

  override fun remove(index: Int) {
    super.remove(index)

    if (components.isEmpty()) return
    if (components[componentCount - 1] is OnePixelDivider) {
      remove(components[componentCount - 1])
    }
  }

  override fun doLayout() {
    val width = width
    val height = height

    val expansionPanels = components.filterIsInstance<ExpansionPanel>()

    // If all components collapsed, we will expand first or centerComponent.
    if (expansionPanels.isNotEmpty() && expansionPanels.all { !it.expanded }) {
      if (centralComponent == null || centralComponent !is ExpansionPanel) {
        expansionPanels.first().expanded = true
      }
      else {
        (centralComponent as? ExpansionPanel)?.expanded = true
      }
    }

    data class ComponentAndWidth(val component: Container, var width: Int)

    val widths = mutableListOf<ComponentAndWidth>()

    var pos = 0
    for (i in 0 until components.size) {
      val component = components[i]

      if (component !is Container) {
        return
      }

      val componentWidth = if (component is OnePixelDivider) {
        // divider have a fixed width and could not be sized
        DIVIDER_WIDTH
      }
      else if (component.maximumSize.width != Int.MAX_VALUE) {
        component.maximumSize.width
      }
      else if (component is ExpansionPanel && !component.expanded) {
        // Collapsed panel could not be sized.
        component.minimumWidth
      }
      else if (i + 1 == components.size) {
        width - pos
      }
      else {
        val divider = components[i + 1] as OnePixelDivider
        val proportion = proportions[divider]!!
        max(30,  (width * proportion).toInt() - pos)
      }

      widths += ComponentAndWidth(component, componentWidth)
      pos += componentWidth
    }

    // Applying compensations (total width is less than split width)
    val totalWidth = widths.sumOf { it.width }
    if (totalWidth < width) {
      // Expanding central component (or applicable component)
      val centralComponent = centralComponent
      val expansionTarget = if (centralComponent is ExpansionPanel && centralComponent.expanded) {
        centralComponent
      }
      else {
        components.last { it !is OnePixelDivider && it.maximumSize.width == Integer.MAX_VALUE }
      }

      val widthRecord = widths.find { it.component == expansionTarget }!!
      widthRecord.width += width - totalWidth
    }

    //Settings bounds
    pos = 0
    widths.forEach {
      it.component.setBounds(pos, 0, it.width, height)
      pos += it.width
    }
  }

  companion object {
    private const val DIVIDER_WIDTH = 1
  }
}