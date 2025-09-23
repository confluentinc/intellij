// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.confluent.kafka.core.rfs.copypaste.dialog

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.refactoring.RefactoringBundle

enum class RfsSkipOverwriteChoice(private val myKey: String) {
  OVERWRITE("copy.overwrite.button"), SKIP("copy.skip.button"), OVERWRITE_ALL("copy.overwrite.for.all.button"), SKIP_ALL(
    "copy.skip.for.all.button");

  companion object {
    private fun getOptions(full: Boolean): List<RfsSkipOverwriteChoice> = if (full) {
      values().asList()
    }
    else {
      listOf(OVERWRITE, SKIP)
    }

    private fun getMessage(choice: RfsSkipOverwriteChoice) = RefactoringBundle.message(choice.myKey)

    /**
     * Shows dialog with overwrite/skip choices
     */
    fun askUser(project: Project?,
                path: String,
                fileName: @NlsSafe String,
                title: @NlsContexts.Command String?,
                includeAllCases: Boolean): RfsSkipOverwriteChoice = invokeAndWaitIfNeeded {
      val message = RefactoringBundle.message("dialog.message.file.already.exists.in.directory", fileName, path)
      val options = getOptions(includeAllCases)
      val selection = Messages.showDialog(project, message, title,
                                          options.map(::getMessage).toTypedArray(), 0, Messages.getQuestionIcon())
      if (selection < 0)
        SKIP
      else
        options[selection]
    }
  }
}