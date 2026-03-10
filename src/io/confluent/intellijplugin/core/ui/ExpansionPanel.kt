package io.confluent.intellijplugin.core.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.layout.migLayout.createLayoutConstraints
import com.intellij.ui.layout.migLayout.createUnitValue
import com.intellij.ui.layout.migLayout.patched.MigLayout
import io.confluent.intellijplugin.core.rfs.icons.RfsIcons
import io.confluent.intellijplugin.core.util.ToolbarUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import net.miginfocom.layout.ConstraintParser
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * In expanded state shows title on top and collapse button in top right.
 * In collapsed state shows only vertical button on left with expand functionality.
 */
class ExpansionPanel(
    @NlsContexts.BorderTitle private val title: String,
    private val supplier: () -> JComponent,
    expanded: Boolean = true,
    private val actions: List<AnAction>? = null
) : JPanel(BorderLayout()) {

    constructor(
        @NlsContexts.BorderTitle title: String,
        supplier: () -> JComponent,
        expandedKey: String,
        expandedDefault: Boolean,
        actions: List<AnAction>? = null
    )
            : this(
        title,
        supplier,
        PropertiesComponent.getInstance().getBoolean(expandedKey, expandedDefault),
        actions
    ) {
        this.expandedServiceKey = expandedKey
    }

    private val changeListeners = mutableListOf<ChangeListener>()

    var expandedServiceKey: String? = null

    var expanded = expanded
        set(value) {
            if (field == value || !isDisplayable) {
                return
            }
            field = value
            update()
            changeListeners.forEach { it.stateChanged(ChangeEvent(this)) }

            expandedServiceKey?.let {
                PropertiesComponent.getInstance().setValue(it, field)
            }
        }

    init {
        update()
    }

    override fun getMaximumSize(): Dimension {
        return if (expanded) super.getMaximumSize() else Dimension(preferredSize.width, preferredSize.height)
    }

    private fun update() {
        if (expanded) {
            setExpanded()
        } else {
            setCollapsed()
        }
    }

    fun addChangeListener(listener: ChangeListener) = changeListeners.add(listener)

    fun removeChangeListener(listener: ChangeListener) = changeListeners.remove(listener)

    private fun setExpanded() {
        val left = createUnitValue(10, isHorizontal = true)
        val right = createUnitValue(0, isHorizontal = true)
        val topBottom = createUnitValue(0, isHorizontal = false)
        val panelInsets = arrayOf(topBottom, left, topBottom, right)
        val titlePanel = JPanel(
            MigLayout(
                createLayoutConstraints(0, 0).noVisualPadding().fill().apply { insets = panelInsets },
                ConstraintParser.parseColumnConstraints("[grow][pref!]")
            )
        ).apply {
            border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
            add(JLabel(title), BorderLayout.LINE_START)
            val expandAction =
                DumbAwareAction.create(KafkaMessagesBundle.message("expansionpanel.collapse"), RfsIcons.COLLAPSE) {
                    expanded = false
                }
            val actionGroup = DefaultActionGroup()
            if (!actions.isNullOrEmpty()) {
                actionGroup.addAll(actions)
                actionGroup.addSeparator()
            }
            actionGroup.add(expandAction)
            add(ToolbarUtils.createActionToolbar(this@ExpansionPanel, "BDTExpansionPanel", actionGroup, true).apply {
                layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY // For removing empty space on the right.
            }.component, BorderLayout.LINE_END)
        }

        removeLineStartComponent()
        add(supplier(), BorderLayout.CENTER)
        add(titlePanel, BorderLayout.NORTH)

        revalidate()
        repaint()
    }

    private fun setCollapsed() {
        removeCenterComponent()
        removeNorthComponent()

        add(VerticalButton.createToolbar(title, RfsIcons.EXPAND).apply {
            addActionListener {
                expanded = true
            }
        }, BorderLayout.LINE_START)

        revalidate()
        repaint()
    }
}