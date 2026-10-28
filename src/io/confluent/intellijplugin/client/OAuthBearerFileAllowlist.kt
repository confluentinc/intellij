package io.confluent.intellijplugin.client

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.core.connection.exception.BdtConfigurationException
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Kafka rejects local files (JWT assertion, private key, assertion template) that are not listed in the JVM
 * property `org.apache.kafka.sasl.oauthbearer.allowed.files` (empty by default). Unlike endpoint URLs these are
 * NOT added automatically: a config pasted or imported from elsewhere could otherwise point Kafka at a sensitive
 * file (for example an SSH key) and a token endpoint of the attacker's choosing, which would then receive it.
 *
 * Instead, the user must confirm each exact (file, token endpoint) pair once on this machine. Approvals are kept
 * in [OAuthBearerFileApprovals], never in the connection settings. Only exact, absolute, normalized file paths
 * for the keys in [FILE_KEYS] are ever added; no directories or wildcards. Entries are only added, so values set
 * with `-D` are preserved.
 */
object OAuthBearerFileAllowlist {
    private const val ALLOWED_FILES_PROPERTY = BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_FILES_CONFIG
    private const val SEPARATOR = ","

    val FILE_KEYS = listOf(
        SaslConfigs.SASL_OAUTHBEARER_ASSERTION_FILE,
        SaslConfigs.SASL_OAUTHBEARER_ASSERTION_PRIVATE_KEY_FILE,
        SaslConfigs.SASL_OAUTHBEARER_ASSERTION_TEMPLATE_FILE,
    )

    private val lock = Any()

    fun interface Approver {
        /** Returns true if the user agrees to let Kafka read [files] and authenticate to [endpoint] with them. */
        fun confirm(project: Project?, files: List<String>, endpoint: String?): Boolean
    }

    /** Asks the user. Callable from a background thread; blocks only that thread, never the EDT. */
    val dialogApprover = Approver { project, files, endpoint ->
        var result = false
        val ask = {
            result = Messages.showYesNoDialog(
                project,
                KafkaMessagesBundle.message(
                    "oauth.files.confirm.message",
                    files.joinToString("<br>"),
                    endpoint ?: KafkaMessagesBundle.message("oauth.files.confirm.no.endpoint")
                ),
                KafkaMessagesBundle.message("oauth.files.confirm.title"),
                KafkaMessagesBundle.message("oauth.files.confirm.allow"),
                KafkaMessagesBundle.message("oauth.files.confirm.deny"),
                Messages.getWarningIcon()
            ) == Messages.YES
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) ask() else app.invokeAndWait(ask, ModalityState.any())
        result
    }

    /**
     * Allows the files named in [properties] once the user has approved them.
     * @throws BdtConfigurationException if the user declines.
     */
    fun allowFiles(
        properties: Map<*, *>,
        project: Project?,
        approvals: OAuthBearerFileApprovals = OAuthBearerFileApprovals.getInstance(),
        approver: Approver = dialogApprover,
    ) {
        val files = FILE_KEYS.mapNotNull { (properties[it] as? String)?.trim() }
            .filter(::isAllowable).distinct()
        if (files.isEmpty()) return

        val endpoint = (properties[SaslConfigs.SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL] as? String)?.trim()
            ?.ifEmpty { null }

        val alreadyAllowed = currentlyAllowed()
        val pending = files.filter { it !in alreadyAllowed && !approvals.isApproved(it, endpoint) }
        if (pending.isNotEmpty()) {
            if (!approver.confirm(project, pending, endpoint)) {
                throw BdtConfigurationException(
                    KafkaMessagesBundle.message("oauth.files.declined", pending.joinToString(", "))
                )
            }
            pending.forEach { approvals.approve(it, endpoint) }
        }

        synchronized(lock) {
            val current = currentlyAllowed()
            val added = files.filter { it !in current }
            if (added.isEmpty()) return

            System.setProperty(ALLOWED_FILES_PROPERTY, (current + added).joinToString(SEPARATOR))
            thisLogger().debug("Allowed OAuth files: $added")
        }
    }

    private fun currentlyAllowed(): List<String> =
        System.getProperty(ALLOWED_FILES_PROPERTY, "").split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    // Kafka compares File(path).absolutePath, so only absolute paths that are already normalized are exact.
    // A comma cannot be represented in the comma-separated list.
    private fun isAllowable(path: String): Boolean {
        if (path.isEmpty() || SEPARATOR in path) return false
        val parsed: Path = try {
            Paths.get(path)
        } catch (_: InvalidPathException) {
            return false
        }
        return parsed.isAbsolute && parsed.normalize().toString() == path
    }
}
