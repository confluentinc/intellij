package io.confluent.kafka.core.ui.components

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.project.Project
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor
import com.intellij.util.textCompletion.ValuesCompletionProvider
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.text.DefaultCaret
import kotlin.math.max

class ConnectionPropertiesEditor(project: Project, private val variants: List<ConnectionProperty>) {
  internal lateinit var fieldWithCompletion: MultilineTextFieldWithCompletion
  private val separators = listOf('\n', ' ', '\t')

  private val descriptor = object : DefaultTextCompletionValueDescriptor<ConnectionProperty>() {
    override fun getLookupString(item: ConnectionProperty) = item.propertyName + "="
    override fun getTypeText(item: ConnectionProperty) = item.rightSideInfo
    override fun getTailText(item: ConnectionProperty) = item.default.ifBlank { "<empty>" }
    override fun createInsertHandler(item: ConnectionProperty) = InsertHandler<LookupElement> { context, _ ->
      context.setAddCompletionChar(false)
    }
  }

  private val textPane = JEditorPane().apply {
    editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
    border = BorderFactory.createCompoundBorder(
      IdeBorderFactory.createBorder(UIUtil.getTooltipSeparatorColor(), SideBorder.TOP),
      BorderFactory.createEmptyBorder(5, 5, 5, 5)
    )
    isEditable = false
    isOpaque = false
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    margin = JBInsets.emptyInsets()
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAATextInfoForSwingComponent())

    (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
  }

  private val completionProvider =
    object : ValuesCompletionProvider.ValuesCompletionProviderDumbAware<ConnectionProperty>(descriptor, separators, variants, true) {
      override fun getAdvertisement(): String? {

        // ToDo extra hack!!!
        //   LookupManager, LookupImpl and LookupUi is overprotected and i cannot find another way to change their look.
        val activeLookup = LookupManager.getActiveLookup(fieldWithCompletion.editor) as? LookupImpl ?: return null

        val advertiser = activeLookup.advertiser ?: return null
        val adComponent = advertiser.adComponent

        val bottomPanelHierarchyListener = object : HierarchyListener {
          override fun hierarchyChanged(e: HierarchyEvent) {
            val bottomPanel = e.component as Container
            e.component.parent ?: return
            bottomPanel.removeHierarchyListener(this)
            bottomPanel.layout = AdvertiserLayout(activeLookup, textPane)
            bottomPanel.removeAll()
            bottomPanel.add(textPane, BorderLayout.CENTER)
            bottomPanel.revalidate()
            bottomPanel.repaint()
          }
        }

        val adComponentHierarchyListener = object : HierarchyListener {
          override fun hierarchyChanged(e: HierarchyEvent?) {
            val bottomPanel = adComponent.parent ?: return
            adComponent.removeHierarchyListener(this)
            bottomPanel.addHierarchyListener(bottomPanelHierarchyListener)
          }
        }

        adComponent.addHierarchyListener(adComponentHierarchyListener)

        activeLookup.addLookupListener(object : LookupListener {
          override fun currentItemChanged(event: LookupEvent) {
            @Suppress("HardCodedStringLiteral")
            val str = (event.item?.`object` as? ConnectionProperty)?.meaning ?: ""
            textPane.text = "<html>${str}</html>"
          }
        })

        return null
      }
    }

  init {
    fieldWithCompletion = MultilineTextFieldWithCompletion(project, completionProvider)
  }

  fun getComponent() = fieldWithCompletion

  inner class AdvertiserLayout(private val advertiser: LookupImpl, private val component: Component) : LayoutManager {

    override fun addLayoutComponent(name: String, comp: Component) {}
    override fun removeLayoutComponent(comp: Component) {}

    override fun preferredLayoutSize(parent: Container): Dimension {
      val insets = parent.insets
      return Dimension(max(advertiser.list.preferredSize.width, 300), component.preferredSize.height + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(parent: Container) = preferredLayoutSize(parent)

    override fun layoutContainer(parent: Container) {
      val insets = parent.insets
      val size = parent.size
      component.setBounds(insets.left, insets.top, JBUIScale.scale(size.width) - insets.left - insets.right,
                          size.height - insets.top - insets.bottom)
    }
  }
}