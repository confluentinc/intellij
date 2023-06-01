package com.jetbrains.bigdatatools.kafka.registry.ui

import com.intellij.CommonBundle
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.json.JsonFileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.LightColors
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.core.util.invokeAndWaitSwing
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.SchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import javax.swing.JEditorPane

object KafkaSchemaInfoDialog {

  fun show(schemaType: String,
           schemaDefinition: String,
           project: Project,
           schemaName: String) {
    val schema = KafkaRegistryUtil.getPrettySchema(schemaType = schemaType, schema = schemaDefinition)

    val isJson = KafkaRegistryFormat.valueOf(schemaType) != KafkaRegistryFormat.PROTOBUF

    val dialogWrapper = DialogBuilder(project)
    dialogWrapper.title(KafkaMessagesBundle.message("registry.info.dialog.title", schemaName))
    dialogWrapper.centerPanel(KafkaRegistrySchemaEditor(project).apply {
      setText(schema, isJson)
    }.component).addOkAction()
    dialogWrapper.show()
  }

  // Suitable for both "Diff between schema versions" and "Update schema".
  private fun showDiff(@NlsContexts.DialogTitle title: String,
                       project: Project,
                       registryInfoFirst: SchemaVersionInfo,
                       registryInfoSecond: SchemaVersionInfo, onApply: ((String) -> Promise<Unit>)? = null) {
    val schemaName = registryInfoFirst.schemaName
    val schemaType = registryInfoFirst.type.name
    val schemaDefinition1 = registryInfoFirst.schema
    val schemaDefinition2 = registryInfoSecond.schema

    showDiff(project, title, schemaName, schemaType, schemaDefinition1, schemaDefinition2, onApply)
  }

  private fun showDiff(project: Project,
                       @Nls title: String,
                       schemaName: String,
                       schemaType: String,
                       schemaDefinition1: String,
                       schemaDefinition2: String,
                       onApply: ((String) -> Promise<Unit>)?) {
    val isJson = KafkaRegistryFormat.valueOf(schemaType) != KafkaRegistryFormat.PROTOBUF
    val fileType = if (isJson) JsonFileType.INSTANCE
    else
      FileTypeManager.getInstance().findFileTypeByName("protobuf")


    val schemaFirst = KafkaRegistryUtil.getPrettySchema(schemaType = schemaType, schema = schemaDefinition1)
    val schemaSecond = KafkaRegistryUtil.getPrettySchema(schemaType = schemaType, schema = schemaDefinition2)

    val prev = DiffContentFactory.getInstance().create(schemaFirst, fileType)
    prev.document.setReadOnly(true)

    val new = DiffContentFactory.getInstance().create(schemaSecond, fileType)
    new.document.setReadOnly(false)


    val diffData = SimpleDiffRequest(KafkaMessagesBundle.message("show.edit.schema.diff.title", schemaName),
                                     prev,
                                     new,
                                     KafkaMessagesBundle.message("show.edit.schema.diff.prev.name"),
                                     KafkaMessagesBundle.message("show.edit.schema.diff.new.name"))

    // DiffWindowBase
    val requests = SimpleDiffRequestChain(diffData)
    val processor = CacheDiffRequestChainProcessor(project, requests)

    var errorLabel: JEditorPane? = null


    val panel = panel {
      row { cell(processor.component).align(Align.FILL).resizableColumn() }.resizableRow()
      row {
        errorLabel = comment("").component.apply {
          isVisible = false
        }
      }
    }


    val dialogWrapper = DialogBuilder(project)
    Disposer.register(dialogWrapper, processor)
    dialogWrapper.setTitle(title)

    dialogWrapper.setCenterPanel(panel)
    dialogWrapper.addOkAction().setText(
      if (onApply == null) CommonBundle.getOkButtonText() else KafkaMessagesBundle.message("diff.dialog.button.update"))
    if (onApply != null) {
      dialogWrapper.addCancelAction()

      dialogWrapper.setOkOperation {
        errorLabel?.isVisible = false
        val newText = new.document.text
        if (prev.document.text != newText) {
          onApply.invoke(newText).onError {
            errorLabel?.apply {
              isOpaque = true
              foreground = UIUtil.getLabelForeground()
              background = LightColors.RED
              border = JBUI.Borders.empty(10, 15, 15, 15)
              text = it.message
              isVisible = true
            }
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
               registryInfo: SchemaVersionInfo,
               onApply: ((String) -> Promise<Unit>)? = null) {
    showDiff(title, project, registryInfo, registryInfo, onApply)
  }
}