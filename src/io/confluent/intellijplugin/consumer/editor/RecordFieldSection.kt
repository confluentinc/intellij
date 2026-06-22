package io.confluent.intellijplugin.consumer.editor

import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.writeBytes
import io.confluent.intellijplugin.common.editor.FieldViewerResolver
import io.confluent.intellijplugin.common.editor.FieldViewerType
import io.confluent.intellijplugin.common.editor.KafkaEditorUtils
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.core.ui.ComponentColoredBorder
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.core.ui.DarculaTextAreaBorder
import io.confluent.intellijplugin.core.ui.ResizableHeightPanel
import io.confluent.intellijplugin.core.ui.chooser.FileChooserUtil
import io.confluent.intellijplugin.registry.ui.KafkaRegistrySchemaEditor
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JComponent

/**
 * A single Key/Value field shown in the Message Details panel: a viewer-type combo box, a read-only editor, and
 * the logic that decides how the field text is rendered (JSON vs. plain text vs. Base64-decode).
 *
 * Layout-agnostic on purpose: it exposes plain Swing components ([viewerTypeCombo], [editorComponent]) so the
 * surrounding panel decides how to place them. The display decisions are delegated to the pure
 * [FieldViewerResolver] so they can be unit tested without instantiating this component.
 */
internal class RecordFieldSection(
    private val project: Project,
    parentDisposable: Disposable,
    /** Default file name suggested when decoding a Base64 value to a file (e.g. "key" / "value"). */
    private val fieldName: String,
    /** Field type assumed when no record is shown. */
    private val defaultFieldType: KafkaFieldType
) {

    companion object {
        /** Minimum editor height so a single-line key stays visible above its horizontal scrollbar. */
        const val MIN_EDITOR_HEIGHT = 50

        /** Editors auto-fit content up to this height before the user resizes them or scrolling kicks in. */
        const val MAX_AUTO_EDITOR_HEIGHT = 350
    }

    /** Consumer field type of the currently displayed value; drives auto-detection. */
    var fieldType: KafkaFieldType = defaultFieldType
        private set

    val viewerTypeCombo = ComboBox(FieldViewerType.entries.toTypedArray()).apply {
        renderer = CustomListCellRenderer<FieldViewerType> { it.title }
    }

    val editor = KafkaRegistrySchemaEditor(project, parentDisposable, isEditable = false).apply {
        component.border =
            BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
    }

    /**
     * The editor wrapped so its height auto-fits the content and can be resized independently via a bottom grip.
     * The chosen height is remembered per field across sessions.
     */
    val editorComponent: JComponent = ResizableHeightPanel(
        content = editor.component,
        minHeight = MIN_EDITOR_HEIGHT,
        maxAutoHeight = MAX_AUTO_EDITOR_HEIGHT,
        persistKey = "ConfluentKafka.RecordDetails.${fieldName}EditorHeight"
    )

    /** Effective viewer type, resolving [FieldViewerType.AUTO] against the current field type and text. */
    fun resolvedViewerType(): FieldViewerType =
        FieldViewerResolver.resolve(viewerTypeCombo.item, fieldType, editor.text)

    /** Whether the "load file" link should be visible for the current resolution. */
    fun isLoadFileLinkVisible(): Boolean = FieldViewerResolver.isLoadFileLinkVisible(resolvedViewerType())

    /** Sets the editor text and language according to the resolved viewer type. */
    fun setText(text: String, type: KafkaFieldType) {
        fieldType = type
        if (FieldViewerResolver.isJsonView(resolvedViewerType())) {
            editor.setText(KafkaEditorUtils.tryFormatJson(text), JsonLanguage.INSTANCE)
        } else {
            editor.setText(text, PlainTextLanguage.INSTANCE)
        }
    }

    /** Resets the field type to its default (used when no record is selected). Leaves editor text untouched. */
    fun resetType() {
        fieldType = defaultFieldType
    }

    /** Re-applies the editor language for the current resolution (used when the viewer-type selection changes). */
    fun updateEditorLanguage() {
        editor.setLanguage(
            if (FieldViewerResolver.isJsonView(resolvedViewerType())) JsonLanguage.INSTANCE
            else PlainTextLanguage.INSTANCE
        )
    }

    /** Decodes the current Base64 editor text and writes it to a user-selected file. */
    fun loadBinaryFile() {
        val virtualFile = FileChooserUtil.selectFolderAndCreateFile(project, fieldName) ?: return
        runWriteAction {
            virtualFile.writeBytes(Base64.getDecoder().decode(editor.text))
        }
    }
}
