package io.confluent.intellijplugin.settings

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.icons.AllIcons
import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class KafkaConnectionGroup : ConnectionFactory<KafkaConnectionData>(
    id = BdtConnectionType.KAFKA.id,
    name = KafkaMessagesBundle.message("connection.group.name.broker"),
    icon = AllIcons.Toolwindows.ToolWindowMessages
) {
    var onCreateConnection: (() -> Unit)? = null

    override fun newData() = KafkaConnectionData(version = 5).apply {
        name = BdtConnectionType.KAFKA.connName
        uri = "127.0.0.1:9092"
    }

    override fun createOptionsPanel(): JComponent {
        val createButton = JButton(KafkaMessagesBundle.message("connection.group.broker.panel.cta")).apply {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            addActionListener { onCreateConnection?.invoke() }
        }

        val content = panel {
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
