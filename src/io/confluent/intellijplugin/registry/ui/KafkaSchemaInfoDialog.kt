package io.confluent.intellijplugin.registry.ui

import com.intellij.CommonBundle
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.CacheDiffRequestChainProcessor
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.LightColors
import com.intellij.ui.ScreenUtil
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.util.invokeAndWaitSwing
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.Promise
import java.awt.Dimension
import javax.swing.JEditorPane
import java.util.concurrent.CancellationException
import kotlin.math.min

object KafkaSchemaInfoDialog {

    fun show(
        schemaType: String,
        schemaDefinition: String,
        project: Project,
        schemaName: String
    ) {
        val schema = KafkaRegistryUtil.getPrettySchema(schemaType = schemaType, schema = schemaDefinition)

        invokeLater {
            val dialogBuilder = DialogBuilder(project)
            dialogBuilder.title(KafkaMessagesBundle.message("registry.info.dialog.title", schemaName))
            dialogBuilder.centerPanel(
                KafkaRegistrySchemaEditor(
                    project,
                    parentDisposable = dialogBuilder,
                    isEditable = false
                ).apply {
                    setText(
                        schema,
                        if (KafkaRegistryFormat.valueOf(schemaType) != KafkaRegistryFormat.PROTOBUF) JsonLanguage.INSTANCE
                        else KafkaRegistryUtil.protobufLanguage
                    )
                }.component.apply {
                    preferredSize = Dimension(
                        min(preferredSize.width, ScreenUtil.getMainScreenBounds().width / 4),
                        min(preferredSize.height, ScreenUtil.getMainScreenBounds().height / 4)
                    )
                }).addOkAction()
            dialogBuilder.setDimensionServiceKey("kafka.schema.info.dialog")
            dialogBuilder.show()
        }
    }

    // Suitable for both "Diff between schema versions" and "Update schema".
    private fun showDiff(
        @NlsContexts.DialogTitle title: String,
        project: Project,
        registryInfoFirst: SchemaVersionInfo,
        registryInfoSecond: SchemaVersionInfo, onApply: ((String) -> Promise<Unit>)? = null
    ) {
        val schemaName = registryInfoFirst.schemaName
        val schemaType = registryInfoFirst.type.name
        val schemaDefinition1 = registryInfoFirst.schema
        val schemaDefinition2 = registryInfoSecond.schema

        showDiff(project, title, schemaName, schemaType, schemaDefinition1, schemaDefinition2, onApply)
    }

    private fun showDiff(
        project: Project,
        @Nls title: String,
        schemaName: String,
        schemaType: String,
        schemaDefinition1: String,
        schemaDefinition2: String,
        onApply: ((String) -> Promise<Unit>)?
    ) {
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

        val diffData = SimpleDiffRequest(
            KafkaMessagesBundle.message("show.edit.schema.diff.title", schemaName),
            prev,
            new,
            KafkaMessagesBundle.message("show.edit.schema.diff.prev.name"),
            KafkaMessagesBundle.message("show.edit.schema.diff.new.name")
        )

        // DiffWindowBase
        val requests = SimpleDiffRequestChain(diffData)
        val processor = CacheDiffRequestChainProcessor(project, requests)

        val busyIcon = AsyncProcessIcon("schema-update")
        var errorLabel: JEditorPane? = null
        var busyRow: Row? = null
        var pendingWrite: Promise<Unit>? = null

        val panel = panel {
            row { cell(processor.component).align(Align.FILL).resizableColumn() }.resizableRow()
            row {
                errorLabel = comment("").component.apply {
                    isVisible = false
                }
            }
            busyRow = row {
                cell(busyIcon)
                label(KafkaMessagesBundle.message("diff.dialog.updating"))
            }.visible(false)
        }

        val dialogWrapper = DialogBuilder(project)
        Disposer.register(dialogWrapper, processor)
        Disposer.register(dialogWrapper, busyIcon)
        dialogWrapper.setTitle(title)

        dialogWrapper.setCenterPanel(panel)
        fun setBusy(busy: Boolean) = invokeAndWaitSwing {
            dialogWrapper.dialogWrapper.isOKActionEnabled = !busy
            busyRow?.visible(busy)
        }

        dialogWrapper.addOkAction().setText(
            if (onApply == null) CommonBundle.getOkButtonText() else KafkaMessagesBundle.message("diff.dialog.button.update")
        )
        if (onApply != null) {
            dialogWrapper.addCancelAction()
            // Closing the dialog while a write is pending aborts it; the registry may still have applied it.
            dialogWrapper.setCancelOperation {
                (pendingWrite as? CancellablePromise<*>)?.cancel()
                dialogWrapper.dialogWrapper.close(DialogWrapper.CANCEL_EXIT_CODE)
            }

            dialogWrapper.setOkOperation {
                errorLabel?.isVisible = false
                val newText = new.document.text
                if (prev.document.text != newText) {
                    // The write can take minutes against a slow registry; keep the dialog from being submitted twice.
                    setBusy(true)
                    val write = try {
                        onApply.invoke(newText)
                    } catch (t: Throwable) {
                        setBusy(false)
                        throw t
                    }
                    pendingWrite = write
                    write.onError {
                        setBusy(false)
                        if (it is CancellationException) return@onError
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
                } else {
                    dialogWrapper.dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
                }
            }
        }
        processor.updateRequest()
        dialogWrapper.show()
    }

    fun showDiff(
        @NlsContexts.DialogTitle title: String,
        project: Project,
        registryInfo: SchemaVersionInfo,
        onApply: ((String) -> Promise<Unit>)? = null
    ) {
        showDiff(title, project, registryInfo, registryInfo, onApply)
    }
}