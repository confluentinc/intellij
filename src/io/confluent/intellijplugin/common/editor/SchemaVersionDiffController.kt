package io.confluent.intellijplugin.common.editor

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
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.Dimension

class SchemaVersionDiffController(val project: Project) : Disposable {
    private var previousSchema: SchemaVersionInfo? = null
    private var newSchema: SchemaVersionInfo? = null
    private var disposable = Disposer.newDisposable(this)

    val component = JBPanelWithEmptyText(BorderLayout()).also {
        it.preferredSize = Dimension(800, 400)
        it.emptyText.clear()
    }

    fun updatePrevious(schema: SchemaVersionInfo) {
        previousSchema = schema
        update()
    }

    fun updateNew(schema: SchemaVersionInfo) {
        newSchema = schema
        update()
    }


    private fun update() {
        val previousSchema = previousSchema ?: return
        val newSchema = newSchema ?: return

        Disposer.dispose(disposable)

        disposable = Disposer.newDisposable(this)
        val isJson = previousSchema.type != KafkaRegistryFormat.PROTOBUF
        val fileType = if (isJson) JsonFileType.INSTANCE
        else
            FileTypeManager.getInstance().findFileTypeByName("protobuf")


        val prev = DiffContentFactory.getInstance().create(previousSchema.getPretty(), fileType)
        prev.document.setReadOnly(true)
        val new = DiffContentFactory.getInstance().create(newSchema.getPretty(), fileType)
        new.document.setReadOnly(true)


        val diffData = SimpleDiffRequest(
            "",
            prev,
            new,
            KafkaMessagesBundle.message("show.edit.schema.diff.version", previousSchema.version),
            KafkaMessagesBundle.message("show.edit.schema.diff.version", newSchema.version)
        )

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