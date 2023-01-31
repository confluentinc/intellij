package com.jetbrains.bigdatatools.kafka.registry.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.json.JsonFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.protobuf.lang.PbFileType
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.bigdatatools.common.util.invokeAndWaitSwing
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryInfo
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.concurrency.Promise
import javax.swing.JEditorPane

object KafkaRegistrySchemaInfoDialog {
  fun show(project: Project, registryInfo: SchemaRegistryInfo) {
    val schema = KafkaRegistryUtil.getPrettySchema(registryInfo) ?: return

    val isJson = KafkaRegistryFormat.valueOf(registryInfo.type) != KafkaRegistryFormat.PROTOBUF

    val dialogWrapper = DialogBuilder(project)
    dialogWrapper.title(KafkaMessagesBundle.message("registry.info.dialog.title", registryInfo.name))
    dialogWrapper.centerPanel(KafkaRegistrySchemaEditor(project).apply {
      setText(schema, isJson)
    }.component).addOkAction()
    dialogWrapper.show()
  }

  // Suitable for both "Diff between schema versions" and "Update schema".
  fun showDiff(@NlsContexts.DialogTitle title: String, project: Project, registryInfoFirst: SchemaRegistryInfo,
               registryInfoSecond: SchemaRegistryInfo, onApply: ((String) -> Promise<Unit>)? = null) {

    val isJson = KafkaRegistryFormat.valueOf(registryInfoFirst.type) != KafkaRegistryFormat.PROTOBUF
    val fileType = if (isJson) JsonFileType.INSTANCE else PbFileType.INSTANCE

    val schemaFirst = KafkaRegistryUtil.getPrettySchema(registryInfoFirst) ?: return
    val schemaSecond = KafkaRegistryUtil.getPrettySchema(registryInfoSecond) ?: return

    val prev = DiffContentFactory.getInstance().create(schemaFirst, fileType)
    prev.document.setReadOnly(true)

    val new = DiffContentFactory.getInstance().create(schemaSecond, fileType)
    new.document.setReadOnly(false)

    val diffData = SimpleDiffRequest(KafkaMessagesBundle.message("show.edit.schema.diff.title", registryInfoFirst.name),
                                     prev,
                                     new,
                                     KafkaMessagesBundle.message("show.edit.schema.diff.prev.name"),
                                     KafkaMessagesBundle.message("show.edit.schema.diff.new.name"))

    // DiffWindowBase
    val requests = SimpleDiffRequestChain(diffData)
    val processor = CacheDiffRequestChainProcessor(project, requests)

    var errorLabel: JEditorPane? = null

    val dialogWrapper = DialogBuilder(project)
    Disposer.register(dialogWrapper, processor)
    dialogWrapper.setTitle(title)
    dialogWrapper.setCenterPanel(panel {
      row { cell(processor.component) }.resizableRow()
      row { errorLabel = comment("").component }
    })
    dialogWrapper.addOkAction().setText(KafkaMessagesBundle.message("diff.dialog.button.update"))
    if (onApply != null) {
      dialogWrapper.addCancelAction()

      dialogWrapper.setOkOperation {
        errorLabel?.isVisible = false
        val newText = new.document.text
        if (prev.document.text != newText) {
          onApply.invoke(newText).onError {
            errorLabel?.text = it.message
            errorLabel?.isVisible = true
          }.onSuccess {
            invokeAndWaitSwing {
              dialogWrapper.dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
            }
          }
        }
        else {
          dialogWrapper.dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
        }
      }
    }
    processor.updateRequest()
    dialogWrapper.show()
  }

  fun showDiff(@NlsContexts.DialogTitle title: String,
               project: Project,
               registryInfo: SchemaRegistryInfo,
               onApply: ((String) -> Promise<Unit>)? = null) {
    showDiff(title, project, registryInfo, registryInfo, onApply)
  }
}