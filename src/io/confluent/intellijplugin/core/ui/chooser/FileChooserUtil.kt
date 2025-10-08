package io.confluent.intellijplugin.core.ui.chooser

import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils

object FileChooserUtil {
    fun selectSingleFile(
        project: Project,
        prevSelectedPath: String? = null,
        fileChooserTitle: @DialogTitle String? = null
    ): VirtualFile? {
        val prevSelectedFile = if (prevSelectedPath.isNullOrBlank()) null
        else VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(prevSelectedPath))

        val fileDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
        if (fileChooserTitle != null) {
            fileDescriptor.title = fileChooserTitle
        }

        return FileChooser.chooseFile(fileDescriptor, project, prevSelectedFile ?: project.guessProjectDir())
    }

    fun selectFolderAndCreateFile(project: Project?, defaultFileName: String): VirtualFile? {
        try {
            val descriptor = FileSaverDescriptor(
                IdeBundle.message("title.new.file"),
                IdeBundle.message("prompt.enter.new.file.name")
            )
            val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper = dialog.save(defaultFileName) ?: return null
            return wrapper.getVirtualFile(true)
        } catch (t: Throwable) {
            RfsNotificationUtils.showExceptionMessage(project, t)
            return null
        }
    }
}
