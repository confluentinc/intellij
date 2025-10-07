package io.confluent.intellijplugin.core.rfs.copypaste.utils

import com.intellij.CommonBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import io.confluent.intellijplugin.core.rfs.copypaste.settings.RfsCopySettings
import org.jetbrains.annotations.Nls

object RfsUserConfirmatory {
    fun askCanContinue(project: Project, @Nls userInfoMessage: String?, key: String): Boolean {
        if (ApplicationManager.getApplication().isUnitTestMode)
            return true

        if (RfsCopySettings.getInstance().ignoreConfirmationCopyMove.contains(key))
            return true

        userInfoMessage ?: return true
        return confirmStartByUser(userInfoMessage, key, project)
    }

    private fun confirmStartByUser(
        @NlsContexts.DialogMessage msg: String,
        doNotAskKey: String,
        project: Project
    ) = invokeAndWaitIfNeeded {
        MessageDialogBuilder.yesNo(CommonBundle.message("title.confirmation"), msg)
            .yesText(CommonBundle.message("button.yes"))
            .noText(CommonBundle.getCancelButtonText())
            .doNotAsk(
                object : DoNotAskOption {
                    override fun isToBeShown() = true

                    override fun setToBeShown(value: Boolean, exitCode: Int) {
                        if (exitCode == 1)
                            return
                        if (value)
                            RfsCopySettings.getInstance().ignoreConfirmationCopyMove.remove(doNotAskKey)
                        else
                            RfsCopySettings.getInstance().ignoreConfirmationCopyMove.add(doNotAskKey)
                    }

                    override fun canBeHidden(): Boolean = true

                    override fun shouldSaveOptionsOnCancel(): Boolean = false

                    override fun getDoNotShowMessage() = DiffBundle.message("do.not.ask.me.again")
                })
            .ask(project)
    }
}