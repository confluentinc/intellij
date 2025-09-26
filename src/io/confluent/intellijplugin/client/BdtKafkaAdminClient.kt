package io.confluent.intellijplugin.client

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClient
import java.time.Duration

class BdtKafkaAdminClient(private val adminClient: AdminClient) : Admin by adminClient, Disposable {
  override fun dispose() = executeOnPooledThread {
    try {
      adminClient.close(Duration.ofSeconds(10))
    }
    catch (t: Throwable) {
      logger.warn("Cannot close kafka client", t)
    }
  }

  companion object {
    private val logger = Logger.getInstance(this::class.java)
  }
}