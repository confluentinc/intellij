package io.confluent.intellijplugin.core.rfs.editorviewer

import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.ui.OnePixelSplitter
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JTable
import javax.swing.table.JTableHeader
import kotlin.math.max
import kotlin.math.roundToInt

class RfsSplitter(val table: JTable, val header: JTableHeader) : OnePixelSplitter() {

  private var firstLayout = true

  override fun createDivider(): Divider {
    return object : OnePixelDivider(isVertical, this) {
      override fun paint(g: Graphics) {
        val bounds = g.clipBounds ?: return
        g.color = if (table.showVerticalLines) table.gridColor else table.background
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
        val rect = header.getHeaderRect(0)
        g.clipRect(rect.x, rect.y, 2, rect.height)
        g.translate(-rect.width + 1, 0)
        header.paint(g)
      }
    }
  }

  private fun getDimension(size: Dimension): Double {
    return if (isVertical) size.getHeight() else size.getWidth()
  }

  private fun computeFirstComponentSize(total: Int): Double {

    val pSize2 = getDimension(secondComponent.preferredSize)
    if (total > pSize2) {
      myProportion = ((total - pSize2) / total).toFloat()
      return total - pSize2
    }

    var size1 = (myProportion * total).toDouble()
    var size2 = total - size1
    if (!isHonorMinimumSize) {
      return size1
    }
    var mSize1 = getDimension(firstComponent.minimumSize)
    var mSize2 = getDimension(secondComponent.minimumSize)
    val pSize1 = getDimension(firstComponent.preferredSize)

    if (isHonorPreferredSize && size1 > mSize1 && size2 > mSize2) {
      mSize1 = pSize1
      mSize2 = pSize2
    }
    if (total < mSize1 + mSize2) {
      size1 = when (lackOfSpaceStrategy) {
        LackOfSpaceStrategy.SIMPLE_RATIO -> {
          val proportion = mSize1 / (mSize1 + mSize2)
          proportion * total
        }
        LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE -> mSize1
        LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE -> total - mSize2
      }
    }
    else {
      if (size1 < mSize1) {
        size1 = mSize1
      }
      else if (size2 < mSize2) {
        size2 = mSize2
        size1 = total - size2
      }
    }
    return size1
  }

  override fun doLayout() {

    if (!firstLayout) {
      return super.doLayout()
    }

    firstLayout = false

    val width = width
    val height = height
    val total = if (isVertical) height else width
    if (total <= 0) return
    if (firstComponent != null && firstComponent.isVisible && secondComponent != null && secondComponent.isVisible) {
      // both first and second components are visible
      val firstRect = Rectangle()
      val dividerRect = Rectangle()
      val secondRect = Rectangle()
      var d = dividerWidth
      val size1: Double
      if (total <= d) {
        size1 = 0.0
        d = total
      }
      else {
        size1 = computeFirstComponentSize(total - d)
      }
      val iSize1 = max(0, size1.roundToInt())
      val iSize2 = max(0, total - iSize1 - d)
      if (isVertical) {
        firstRect.setBounds(0, 0, width, iSize1)
        dividerRect.setBounds(0, iSize1, width, d)
        secondRect.setBounds(0, iSize1 + d, width, iSize2)
      }
      else {
        firstRect.setBounds(0, 0, iSize1, height)
        dividerRect.setBounds(iSize1, 0, d, height)
        secondRect.setBounds(iSize1 + d, 0, iSize2, height)
      }
      myDivider.isVisible = true
      firstComponent.bounds = firstRect
      myDivider.bounds = dividerRect
      secondComponent.bounds = secondRect
    }
    else if (firstComponent != null && firstComponent.isVisible) { // only first component is visible
      myDivider.isVisible = false
      firstComponent.setBounds(0, 0, width, height)
    }
    else if (secondComponent != null && secondComponent.isVisible) { // only second component is visible
      myDivider.isVisible = false
      secondComponent.setBounds(0, 0, width, height)
    }
    else { // both components are null or invisible
      myDivider.isVisible = false
      if (firstComponent != null) {
        firstComponent.setBounds(0, 0, 0, 0)
      }

      if (secondComponent != null) {
        secondComponent.setBounds(0, 0, 0, 0)
      }
    }
  }
}