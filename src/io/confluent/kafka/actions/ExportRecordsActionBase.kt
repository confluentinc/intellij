package io.confluent.kafka.actions

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.confluent.kafka.consumer.editor.ConsumerEditorUtils.getTableContent
import io.confluent.kafka.consumer.editor.KafkaConsumerEditor
import io.confluent.kafka.core.rfs.util.RfsNotificationUtils
import io.confluent.kafka.core.util.executeOnPooledThread
import io.confluent.kafka.core.util.invokeLater
import io.confluent.kafka.producer.editor.KafkaProducerEditor
import io.confluent.kafka.util.KafkaMessagesBundle
import java.nio.file.Path
import java.nio.file.Paths

internal abstract class ExportRecordsActionBase : DumbAwareAction() {

  abstract val type: ExportType

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = CommonDataKeys.VIRTUAL_FILE.getData(e.dataContext)

    e.presentation.isEnabledAndVisible = if (project != null && file != null) {
      val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)
      editor is KafkaConsumerEditor || editor is KafkaProducerEditor
    } else {
      false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(e.dataContext) ?: return

    val editor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile)
    val records = when (editor) {
      is KafkaConsumerEditor -> editor.customizable.getRecords()
      is KafkaProducerEditor -> editor.getRecords()
      else -> null
    } ?: return

    val fileWrapper = getSavedFile(project) ?: return
    val file = fileWrapper.file

    executeOnPooledThread {
      try {
        val text = getTableContent(records, type.extension)
        file.bufferedWriter().use { out -> out.write(text) }
        invokeLater {
          RfsNotificationUtils.notifySuccess(
            KafkaMessagesBundle.message("rfs.dump.to.file.action.saved.notification.message", file.name),
            KafkaMessagesBundle.message("rfs.dump.to.file.action.saved.notification.title"),
            create(RevealFileAction.getActionName()) { RevealFileAction.openFile(file) },
            project
          )
        }
      }
      catch (e: Exception) {
        invokeLater {
          RfsNotificationUtils.notifyException(e, KafkaMessagesBundle.message("rfs.dump.to.file.action.saving.error.title"))
        }
      }
    }
  }

  @RequiresEdt
  private fun getSavedFile(project: Project): VirtualFileWrapper? {
    val fileDescriptor = FileSaverDescriptor(
      KafkaMessagesBundle.message("group.Kafka.ExportRecords.Actions.text"),
      KafkaMessagesBundle.message("group.Kafka.ExportRecords.Actions.description")
    ).apply {
      withExtensionFilter(KafkaMessagesBundle.message("group.Kafka.ExportRecords.Actions.label"), *ExportType.entries.map { it.extension }.toTypedArray())
    }
    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(fileDescriptor, project)

    val projectPath: String? = project.basePath
    val baseDir: Path? = if (projectPath != null) Paths.get(projectPath) else null
    return dialog.save(baseDir, "$FILE_NAME.${type.extension}")
  }
}

internal class CsvExportAction : ExportRecordsActionBase() {
  override val type = ExportType.CSV
}

internal class TsvExportAction : ExportRecordsActionBase() {
  override val type = ExportType.TSV
}

internal class JsonExportAction : ExportRecordsActionBase() {
  override val type = ExportType.JSON
}

internal enum class ExportType(val extension: String) {
  CSV("csv"), TSV("tsv"), JSON("json");
}

private const val FILE_NAME = "output"
