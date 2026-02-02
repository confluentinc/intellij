package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import io.confluent.intellijplugin.data.ClusterScopedDataManager

/**
 * Storage for CCloud consumer panels to persist them across editor recreations.
 *
 * When a file editor window is moved or floated, IntelliJ may dispose and recreate
 * the FileEditor. This storage ensures the consumer panel (and its running consumer)
 * survives these operations.
 *
 * Mirrors [KafkaConsumerPanelStorage] for native Kafka connections.
 */
class CCloudConsumerPanelStorage(private val dataManager: ClusterScopedDataManager) : Disposable {
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val storage = mutableMapOf<VirtualFile, CCloudConsumerPanel>()

    fun getOrCreate(project: Project, file: VirtualFile): CCloudConsumerPanel {
        val cachedPanel = storage[file]
        if (cachedPanel != null)
            return cachedPanel

        val panel = CCloudConsumerPanel(project, dataManager, file)
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
                if (Disposer.isDisposed(this@CCloudConsumerPanelStorage))
                    return@Runnable

                disposePanel(file)
            },
            1000
        )
    }

    private fun disposePanel(file: VirtualFile) {
        val panel = storage[file] ?: return
        if (Disposer.isDisposed(panel))
            return
        val editors =
            ProjectManager.getInstance().openProjects.flatMap { FileEditorManager.getInstance(it).allEditors.toList() }
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
