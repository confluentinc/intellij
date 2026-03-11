package io.confluent.intellijplugin.core.settings.connections

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IconUtil
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class BrokerConnectionGroup : ConnectionGroup(
    id = GROUP_ID,
    name = KafkaMessagesBundle.message("connection.group.name.broker"),
    icon = AllIcons.Toolwindows.ToolWindowMessages
) {
    var onCreateConnection: (() -> Unit)? = null

    companion object {
        const val GROUP_ID: String = "BrokerConnectionGroup"
        private const val ICON_SCALE = 2.5f
    }

    override fun createOptionsPanel(): JComponent {
        val createButton = JButton(KafkaMessagesBundle.message("connection.group.broker.panel.cta")).apply {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            addActionListener { onCreateConnection?.invoke() }
        }

        val content = panel {
            row {
                icon(IconUtil.scale(BigdatatoolsKafkaIcons.Kafka, null, ICON_SCALE))
                    .align(AlignX.CENTER)
            }.bottomGap(BottomGap.SMALL)
            row {
                label(KafkaMessagesBundle.message("connection.group.broker.panel.title"))
                    .align(AlignX.CENTER)
                    .bold()
            }.bottomGap(BottomGap.NONE)
            row {
                comment(KafkaMessagesBundle.message("connection.group.broker.panel.label"))
                    .align(AlignX.CENTER)
            }.bottomGap(BottomGap.SMALL)
            row {
                cell(createButton).align(AlignX.CENTER)
            }
        }

        return JPanel(GridBagLayout()).apply { add(content) }
    }
}