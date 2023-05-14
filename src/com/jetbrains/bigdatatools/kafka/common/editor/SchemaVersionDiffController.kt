package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanelWithEmptyText
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.SchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.BorderLayout

class SchemaVersionDiffController(val project: Project) : Disposable {
  private var schema1: SchemaVersionInfo? = null
  private var schema2: SchemaVersionInfo? = null
  private var disposable = Disposer.newDisposable(this)

  val component = JBPanelWithEmptyText(BorderLayout())

  fun updateVersion1(schema: SchemaVersionInfo) {
    schema1 = schema
    update()
  }

  fun updateVersion2(schema: SchemaVersionInfo) {
    schema2 = schema
    update()
  }


  private fun update() {
    val schema1 = schema1 ?: return
    val schema2 = schema2 ?: return

    Disposer.dispose(disposable)

    disposable = Disposer.newDisposable(this)
    val isJson = schema1.type != KafkaRegistryFormat.PROTOBUF
    val fileType = if (isJson) JsonFileType.INSTANCE
    else
      FileTypeManager.getInstance().findFileTypeByName("protobuf")


    val prev = DiffContentFactory.getInstance().create(schema1.getPretty(), fileType)
    prev.document.setReadOnly(true)
    val new = DiffContentFactory.getInstance().create(schema2.getPretty(), fileType)
    new.document.setReadOnly(false)


    val diffData = SimpleDiffRequest("",
                                     prev,
                                     new,
                                     KafkaMessagesBundle.message("show.edit.schema.diff.version", schema1.version),
                                     KafkaMessagesBundle.message("show.edit.schema.diff.version", schema2.version))

    // DiffWindowBase
    val requests = SimpleDiffRequestChain(diffData)

    val processor = CacheDiffRequestChainProcessor(project, requests).also {
      Disposer.register(disposable, it)
    }
    processor.updateRequest()
    component.removeAll()
    component.add(processor.component)
  }

  override fun dispose() {

  }
}