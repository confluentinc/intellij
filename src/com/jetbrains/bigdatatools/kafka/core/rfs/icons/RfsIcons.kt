package com.jetbrains.bigdatatools.kafka.core.rfs.icons

import com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.scale.ScaleType
import javax.swing.Icon
import kotlin.math.ceil

object RfsIcons {
  private val LOCK: Icon
    get() = AllIcons.Nodes.Locked

  val ERROR_ICON: Icon
    get() = AllIcons.General.Error

  val BDT_ICON : Icon
    get() = BigdatatoolsKafkaIcons.BigData

  // Connection types
  val LOCAL_ICON : Icon
    get() = BigdatatoolsKafkaIcons.RfsIcons.Local
  val DIRECTORY_ICON : Icon
    get() = AllIcons.Nodes.Package
  val DIRECTORY_LOCKED_ICON : Icon
    get() = createLockInscribed(AllIcons.Nodes.Package)
  val FILE_ICON : Icon
    get() = AllIcons.FileTypes.Any_type
  val FILE_LOCKED_ICON : Icon
    get() = createLockInscribed(AllIcons.FileTypes.Any_type, AllIcons.FileTypes.Any_type.iconWidth - LOCK.iconWidth)

  val REMOTE_DIRECTORY_ICON : Icon
    get() = BigdatatoolsKafkaIcons.RfsIcons.RemoteFS
  val REMOTE_DIRECTORY_LOCKED_ICON : Icon
    get() = createLockInscribed(REMOTE_DIRECTORY_ICON)

  val LOADING_ICON : Icon
    get() = AnimatedIcon.Default()

  val COLLAPSE : Icon
    get() = BigdatatoolsKafkaIcons.CollapseallVertical
  val EXPAND : Icon
    get() = BigdatatoolsKafkaIcons.ExpandallVertical

  fun createLockInscribed(base: Icon): ScalableIcon {
    return createLockInscribed(base, base.iconWidth - LOCK.iconWidth + 3)
  }

  fun createLockInscribed(base: Icon, hShift: Int): ScalableIcon {
    return createInscribedIcon(base, Inscription(LOCK, hShift, 1))
  }

  private fun createInscribedIcon(base: Icon, vararg inscriptions: Inscription): ScalableIcon {
    val layeredIcon = FixedSizeLayeredIcon(inscriptions.size + 1)
    layeredIcon.setIcon(base, 0)

    inscriptions.forEachIndexed { idx: Int, (icon: Icon, hShift: Int, vShift: Int) ->
      layeredIcon.setIcon(icon, idx + 1, hShift, vShift)
    }
    return layeredIcon
  }

  data class Inscription(val icon: Icon, val hShift: Int, val vShift: Int)

  class FixedSizeLayeredIcon : LayeredIcon {

    constructor(layerCount: Int) : super(layerCount)
    private constructor(another: LayeredIcon) : super(another)

    override fun getIconWidth(): Int {
      super.getIconWidth()
      return ceil(scaleVal(getIcon(0)!!.iconWidth.toDouble(), ScaleType.OBJ_SCALE)).toInt()
    }

    override fun getIconHeight(): Int {
      super.getIconHeight()
      return ceil(scaleVal(getIcon(0)!!.iconHeight.toDouble(), ScaleType.OBJ_SCALE)).toInt()
    }

    override fun replaceBy(replacer: IconReplacer): FixedSizeLayeredIcon {
      return FixedSizeLayeredIcon(super.replaceBy(replacer))
    }
  }
}