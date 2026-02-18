package io.confluent.intellijplugin.ccloud.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IconUtil
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shared Confluent Cloud sign-in panel.
 * Used by both the Settings dialog and the tool window tab.
 */
object CCloudSignInPanel {

    const val ICON_SCALE = 2.5f

    /**
     * Creates a centered sign-in panel with Confluent logo, title, description, and sign-in button.
     * @param onCreateConnection optional callback; when provided, a "Create a Kafka connection" link
     *                           is shown below the sign-in button (used in the tool window only).
     */
    fun create(onCreateConnection: (() -> Unit)? = null): JComponent {
        val signInButton = JButton(KafkaMessagesBundle.message("confluent.cloud.welcome.panel.cta")).apply {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            addActionListener { CCloudAuthService.getInstance().signIn() }
        }

        val content = panel {
            row {
                icon(IconUtil.scale(BigdatatoolsKafkaIcons.ConfluentTab, null, ICON_SCALE))
                    .align(AlignX.CENTER)
            }.bottomGap(BottomGap.SMALL)
            row {
                label(KafkaMessagesBundle.message("confluent.cloud.welcome.panel.title"))
                    .align(AlignX.CENTER)
                    .bold()
            }.bottomGap(BottomGap.NONE)
            row {
                comment(KafkaMessagesBundle.message("confluent.cloud.welcome.panel.label"))
                    .align(AlignX.CENTER)
            }.bottomGap(BottomGap.SMALL)
            row {
                cell(signInButton).align(AlignX.CENTER)
            }
            if (onCreateConnection != null) {
                row {
                    link(KafkaMessagesBundle.message("confluent.cloud.welcome.panel.create.connection")) {
                        onCreateConnection()
                    }.align(AlignX.CENTER)
                }
            }
        }

        return JPanel(GridBagLayout()).apply { add(content) }
    }
}
