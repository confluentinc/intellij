package io.confluent.intellijplugin.core.ui

import com.intellij.openapi.util.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.settings.defaultui.UiUtil
import io.confluent.intellijplugin.core.settings.fields.WrappedNamedComponent
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

@Suppress("HardCodedStringLiteral")
class Comment(text: String) : JLabel("<html>$text</html>") {
    override fun getForeground() = UIUtil.getLabelDisabledForeground()
}

class EmptyCell : JComponent()

@Deprecated("MigLayout is deprecated, use Kotlin UI DSL instead (see com.intellij.ui.dsl.builder.BuilderKt.panel)")
open class MigPanel(layoutConstraints: LC = UiUtil.insets0FillXHidemode3) : JPanel(MigLayout(layoutConstraints)),
    UserDataHolder {

    companion object {
        private val TITLE_CONSTRAINTS = CC().growX().spanX().gapTop("20").wrap()

        fun createTitlePanel(@NlsContexts.Separator label: String) = JPanel(GridBagLayout()).apply {
            add(JLabel(label))
            add(JSeparator(), GridBagConstraints().apply {
                insets = JBUI.insetsLeft(10)
                fill = GridBagConstraints.HORIZONTAL
                gridwidth = GridBagConstraints.REMAINDER
                weightx = 1.0
            })
        }
    }

    private val userData = UserDataHolderBase()

    override fun <T> getUserData(key: Key<T>): T? = userData.getUserData(key)
    override fun <T> putUserData(key: Key<T>, value: T?) = userData.putUserData(key, value)

    // used externally
    @Suppress("MemberVisibilityCanBePrivate")
    var gapLeft: Boolean = false

    fun title(@NlsContexts.Separator label: String) {
        title(createTitlePanel(label))
    }

    fun title(component: JComponent) {
        add(component, TITLE_CONSTRAINTS)
    }

    fun row(@NlsContexts.Label label: String) {
        if (gapLeft) add(JLabel(label), UiUtil.gapLeftWrap) else add(JLabel(label), UiUtil.wrap)
    }

    fun row(@NlsContexts.Label label: String, component: JComponent) {
        row(JLabel(label), component)
    }

    fun row(component: WrappedNamedComponent<*>) {
        row(component.labelComponent, component.getComponent())
    }

    fun row(label: JComponent, component: JComponent) {
        if (gapLeft) add(label, UiUtil.gapLeft) else add(label)

        if (SystemInfo.isMac && component is JComboBox<*>) {
            add(component, UiUtil.macOsPushXGrowXSpanXWrap)
        } else {
            add(component, UiUtil.pushXGrowXSpanXWrap)
        }
    }

    fun row(component: JComponent) {
        val constraints = if (SystemInfo.isMac && component is JComboBox<*>) {
            if (gapLeft) {
                UiUtil.macOsGapLeftSpanXWrap
            } else {
                UiUtil.macOsPushXGrowXSpanXWrap
            }
        } else {
            if (gapLeft) {
                UiUtil.gapLeftSpanXWrap
            } else {
                UiUtil.pushXGrowXSpanXWrap
            }
        }

        add(component, constraints)
    }

    fun comment(emptyCell: EmptyCell, label: Comment) {
        if (gapLeft) add(emptyCell, UiUtil.gapLeft) else add(emptyCell)
        add(label, CC().grow().span().wrap())
    }

    fun block(component: Component) =
        add(component, if (gapLeft) UiUtil.gapLeftGrowXSpanXWrap else UiUtil.growXSpanXWrap)
}