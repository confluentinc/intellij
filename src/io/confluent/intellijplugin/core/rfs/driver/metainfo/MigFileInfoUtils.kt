package io.confluent.intellijplugin.core.rfs.driver.metainfo

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.BrowserLink
import io.confluent.intellijplugin.core.rfs.driver.metainfo.components.SelectableLabel
//import io.confluent.intellijplugin.core.ui.MigBlock
import io.confluent.intellijplugin.core.ui.MigPanel
import java.awt.Dimension

fun MigPanel.rowIfNotBlank(@NlsContexts.Label label: String, text: String?) {
  if (!text.isNullOrBlank())
    row(label, SelectableLabel(text))
}

fun MigPanel.linkIfNotBlank(@NlsContexts.Label label: String, text: String?) {
  if (!text.isNullOrBlank()) {
    val link = BrowserLink(text).apply {
      minimumSize = Dimension(minimumSize.height * 2, minimumSize.height)
      preferredSize = minimumSize
    }
    row(label, link)
  }
}

//fun MigBlock.rowIfNotBlank(@NlsContexts.Label label: String, text: String?) {
//  if (!text.isNullOrBlank())
//    row(label, SelectableLabel(text))
//}