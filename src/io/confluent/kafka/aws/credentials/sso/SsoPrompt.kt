package io.confluent.kafka.aws.credentials.sso

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import io.confluent.kafka.core.rfs.util.RfsNotificationUtils
import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.SwingUtilities

object SsoPrompt : SsoLoginCallback {
  override fun tokenPending(authorization: Authorization) {
    SwingUtilities.invokeAndWait {
      val result = Messages.showOkCancelDialog(
        KafkaMessagesBundle.message("credentials.sso.login.message", authorization.verificationUri, authorization.userCode),
        KafkaMessagesBundle.message("credentials.sso.login.title"),
        KafkaMessagesBundle.message("credentials.sso.login.open_browser"),
        Messages.getCancelButton(),
        Messages.getQuestionIcon())

      if (result == Messages.OK) {
        BrowserUtil.browse(authorization.verificationUriComplete)
      }
      else {
        throw ProcessCanceledException(IllegalStateException(KafkaMessagesBundle.message("credentials.sso.login.cancelled")))
      }
    }
  }

  override fun tokenRetrieved() {}

  override fun tokenRetrievalFailure(e: Exception) {
    RfsNotificationUtils.notifyException(e, KafkaMessagesBundle.message("credentials.sso.login.failed"))
  }
}