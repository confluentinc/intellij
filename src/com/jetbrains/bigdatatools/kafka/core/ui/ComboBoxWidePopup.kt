package com.jetbrains.bigdatatools.kafka.core.ui

import com.intellij.openapi.ui.ComboBox
import javax.swing.ComboBoxModel

/**
 * The original preferred size of `JComboBox` is calculated as a max of combobox items preferred sizes.
 * But we need to have comboBox which is smaller than the
 * Platform [com.intellij.openapi.ui.ComboBoxWithWidePopup] only provides a possibility to make wide popup.
 * This class via enableWidePopup(), has an implementation.
 */
class ComboBoxWidePopup<E> : ComboBox<E> {

  private var widePopup = false

  private val nullElement: E?

  init {
    isSwingPopup = false
  }

  constructor(model: ComboBoxModel<E>, referenceElement: E?) : super(model) {
    this.nullElement = referenceElement
  }

  constructor(values: Array<E>, referenceElement: E?) : super(values) {
    this.nullElement = referenceElement
  }

  override fun getMinimumPopupWidth(): Int {
    if (widePopup) {
      prototypeDisplayValue = null
      val preferredWidth = preferredSize.width
      prototypeDisplayValue = nullElement
      return preferredWidth
    }
    return super.getMinimumPopupWidth()
  }
}