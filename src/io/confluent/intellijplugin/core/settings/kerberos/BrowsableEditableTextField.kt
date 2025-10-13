package io.confluent.intellijplugin.core.settings.kerberos

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.UIBundle
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import io.confluent.intellijplugin.core.ui.chooser.FileChooserUtil
import io.confluent.intellijplugin.core.ui.doOnChange
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.nio.file.Path
import javax.swing.Icon

class BrowsableEditableTextField(project: Project) : ExtendableTextField() {
    init {
        val clearExtension = ExtendableTextComponent.Extension.create(
            CLOSE_ICON,
            CLOSE_HOVERED_ICON,
            KafkaMessagesBundle.message("text.field.browse.clear")
        ) {
            this.text = ""
        }

        doOnChange {
            if (text.isEmpty()) {
                if (extensions.contains(clearExtension)) {
                    removeExtension(clearExtension)
                }
            } else {
                if (!extensions.contains(clearExtension)) {
                    addExtension(clearExtension)
                }
            }
        }

        addExtension(
            ExtendableTextComponent.Extension.create(
                AllIcons.General.OpenDisk,
                AllIcons.General.OpenDisk,
                UIBundle.message("component.with.browse.button.accessible.name")
            ) {
                val file = FileChooserUtil.selectSingleFile(
                    project,
                    null,
                    UIBundle.message("component.with.browse.button.accessible.name")
                )
                    ?: return@create
                this.text = file.path
            })

        addExtension(
            ExtendableTextComponent.Extension.create(
                AllIcons.General.Inline_edit,
                AllIcons.General.Inline_edit,
                UIBundle.message("button.text.edit")
            ) {
                val vf =
                    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Path.of(this.text)) ?: return@create
                FileEditorManager.getInstance(project).openFile(vf, true)
            })
    }
}

private val CLOSE_ICON: Icon
    get() = AllIcons.Actions.Close

private val CLOSE_HOVERED_ICON: Icon
    get() = AllIcons.Actions.CloseHovered