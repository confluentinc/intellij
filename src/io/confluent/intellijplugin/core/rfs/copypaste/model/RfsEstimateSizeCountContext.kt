package io.confluent.intellijplugin.core.rfs.copypaste.model

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import io.confluent.intellijplugin.core.delegate.Delegate
import io.confluent.intellijplugin.core.delegate.Delegate2
import io.confluent.intellijplugin.core.util.SizeUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle


data class RfsEstimateSizeCountContext(val progressIndicator: ProgressIndicator) {
    private var curFilePath: String = ""
    var totalCount: Int = 0
        private set
    private var totalSize: Long = 0
    val isCanceled: Boolean
        get() = progressIndicator.isCanceled

    val finishNotifier = Delegate<Throwable?, Unit>()
    val progressNotifier = Delegate2<Int, Long, Unit>()

    fun cancel() {
        progressIndicator.cancel()
        updateIndicator()
    }

    fun updateCalculatingPath(path: String) {
        curFilePath = path
        updateIndicator()

    }

    fun updateSizeCount(addCount: Int, addSize: Long) {
        totalCount += addCount

        if (totalSize != -1L && addSize > -1L)
            totalSize += addSize
        else
            totalSize = -1

        updateIndicator()
        progressNotifier.notify(totalCount, totalSize)
    }

    fun notifyFinish(throwable: Throwable?) {
        finishNotifier.notify(throwable)
    }

    private fun updateIndicator() {
        progressIndicator.isIndeterminate = true

        progressIndicator.text = KafkaMessagesBundle.message(
            "rfs.directory.task.estimate.contents.progress.text",
            curFilePath
        )
        if (totalSize != -1L) {
            progressIndicator.text2 = KafkaMessagesBundle.message(
                "rfs.directory.task.estimate.contents.info", totalCount,
                SizeUtils.toString(totalSize)
            )

        } else {
            progressIndicator.text2 =
                KafkaMessagesBundle.message("rfs.directory.task.estimate.contents.info.without.size", totalCount)
        }

        if (progressIndicator.isCanceled) {
            progressIndicator.text2 = KafkaMessagesBundle.message("rfs.copy.process.text2.canceling")

            return
        }
    }

    companion object {
        val KEY = Key<RfsEstimateSizeCountContext>("ESTIMATE_SIZE_CONTEXT")
    }
}