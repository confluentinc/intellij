package io.confluent.intellijplugin.core.rfs.driver.task

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.NlsContexts.ProgressTitle

abstract class RemoteFsTask(@ProgressTitle title: String) : Task.Backgroundable(null, title, true) {
  open fun safeExecute(parentProgressIndicator: ProgressIndicator): Throwable? = try {
    run(parentProgressIndicator)
    null
  }
  catch (t: Throwable) {
    t
  }
}