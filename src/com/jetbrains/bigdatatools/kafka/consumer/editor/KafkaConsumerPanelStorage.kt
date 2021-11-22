package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager

class KafkaConsumerPanelStorage(private val dataManager: KafkaDataManager) : Disposable {
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val storage = mutableMapOf<VirtualFile, KafkaConsumerPanel>()

  fun getOrCreate(file: VirtualFile): KafkaConsumerPanel {
    val cachedPanel = storage[file]
    if (cachedPanel != null)
      return cachedPanel

    val panel = KafkaConsumerPanel(dataManager, file)
    Disposer.register(this, panel)
    storage[file] = panel
    return panel
  }

  fun unsubscribe(file: VirtualFile) {
    if (Disposer.isDisposed(this)) {
      return
    }

    alarm.addRequest(
      Runnable {
        if (Disposer.isDisposed(this@KafkaConsumerPanelStorage))
          return@Runnable

        disposePanel(file)
      },
      1000)
  }

  private fun disposePanel(file: VirtualFile) {
    val panel = storage[file] ?: return
    if (Disposer.isDisposed(panel))
      return
    val editors = ProjectManager.getInstance().openProjects.flatMap { FileEditorManager.getInstance(it).allEditors.toList() }
    val isNoAttached = !editors.any { it.file == file }
    if (isNoAttached) {
      storage.remove(file)
      Disposer.dispose(panel)
    }
  }

  override fun dispose() {
    storage.clear()
  }
}