package io.confluent.intellijplugin.core.rfs.driver.metainfo.details

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.TimerUtil
import io.confluent.intellijplugin.core.ui.MigPanel
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import net.miginfocom.layout.CC
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

interface FileInfoDetails : Disposable {
  fun getBlocks(): List<FileInfoBlock>
}

// In FileInfo some blocks are displayed as tabbed control. To preserve last selected tab, we will store it name.
private var lastOpenedTab: String? = null

/** Preparation of blocks for displaying in FileInfo details panel in EditorFileViewer. */
fun FileInfoDetails.getComponent(): JComponent {
  val blocks = getBlocks()

  val panel = MigPanel()

  blocks.filter { it.title.isEmpty() }.forEach {
    panel.block(it.component)
  }

  val tabs = JBTabbedPane()
  blocks.filter { it.title.isNotEmpty() }.forEach {
    tabs.addTab(it.title.ifEmpty { KafkaMessagesBundle.message("file.info.group.basic") }, it.component)
  }

  if (tabs.tabCount != 0) {

    for (i in 0 until tabs.tabCount) {
      if (tabs.getTitleAt(i) == lastOpenedTab) {
        tabs.selectedIndex = i
        break
      }
    }

    tabs.addChangeListener {
      lastOpenedTab = tabs.getTitleAt(tabs.selectedIndex)
    }

    panel.row(JSeparator())
    panel.block(tabs)
  }

  val hasModifiableBlock = blocks.any { it.isModified != null && it.apply != null }

  if (hasModifiableBlock) {
    val applyButton = JButton(CommonBundle.getApplyButtonText()).apply {
      isVisible = false
      isEnabled = false
      addActionListener {
        blocks.forEach { if (it.isModified?.invoke() == true) it.apply?.invoke() }
      }
    }

    val revertButton = JButton(KafkaMessagesBundle.message("file.info.button.revert")).apply {
      isVisible = false
      isEnabled = false

      addActionListener {
        blocks.forEach { if (it.isModified?.invoke() == true) it.revert?.invoke() }
      }
    }

    val buttons = listOf(revertButton, applyButton)

    val periodicUpdater = TimerUtil.createNamedTimer("FileInfoDetailsApplyUpdater", 1000).apply {
      isRepeats = true
      addActionListener {
        if (blocks.any { it.isModified?.invoke() == true }) {
          if (!applyButton.isVisible) {
            buttons.forEach {
              it.isVisible = true
              it.isEnabled = true
            }
          }
        }
        else {
          if (applyButton.isVisible) {
            buttons.forEach {
              it.isVisible = false
              it.isEnabled = false
            }
          }
        }
      }
      start()
    }

    Disposer.register(this) {
      periodicUpdater.stop()
    }

    panel.add(JPanel().apply {
      add(revertButton)
      add(applyButton)
    }, CC().spanX().alignX("right").wrap())
  }

  return panel
}

