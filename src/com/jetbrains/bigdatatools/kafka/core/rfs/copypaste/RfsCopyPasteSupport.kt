package com.jetbrains.bigdatatools.kafka.core.rfs.copypaste

import com.intellij.ide.CopyPasteSupport
import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.PasteProvider
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.providers.RfsCopyCutProvider
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.providers.RfsPasteProvider
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.actions.RfsPaneOwner

class RfsCopyPasteSupport(pane: RfsPaneOwner) : CopyPasteSupport {
  private val pasteProvider = RfsPasteProvider(pane)
  private val copyCutProvider = RfsCopyCutProvider(pane)

  override fun getCopyProvider(): CopyProvider = copyCutProvider
  override fun getCutProvider(): CutProvider = copyCutProvider
  override fun getPasteProvider(): PasteProvider = pasteProvider
}