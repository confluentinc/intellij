package io.confluent.intellijplugin.client

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.client.OAuthBearerFileAllowlist.Approver
import io.confluent.intellijplugin.core.connection.exception.BdtConfigurationException
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import org.apache.kafka.common.security.oauthbearer.internals.secured.ConfigurationUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OAuthBearerFileAllowlistTest {
    private val property = BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_FILES_CONFIG
    private var original: String? = null

    private class RecordingApprover(private val answer: Boolean) : Approver {
        val asked = mutableListOf<Pair<List<String>, String?>>()
        override fun confirm(project: Project?, files: List<String>, endpoint: String?): Boolean {
            asked += files to endpoint
            return answer
        }
    }

    private val approvals = OAuthBearerFileApprovals()

    @BeforeEach
    fun saveAndClear() {
        original = System.getProperty(property)
        System.clearProperty(property)
    }

    @AfterEach
    fun restore() {
        original?.let { System.setProperty(property, it) } ?: System.clearProperty(property)
    }

    private fun allowed(): String? = System.getProperty(property)

    private fun allow(props: Map<String, String>, approver: Approver) =
        OAuthBearerFileAllowlist.allowFiles(props, null, approvals, approver)

    @Nested
    inner class `with user approval` {
        @TempDir
        lateinit var dir: File

        @Test
        fun `should allow private key file after the user approves`() {
            val key = File(dir, "key.pem").absolutePath
            val approver = RecordingApprover(true)

            allow(
                mapOf(
                    "sasl.oauthbearer.assertion.private.key.file" to key,
                    "sasl.oauthbearer.token.endpoint.url" to "https://idp.example.com/token",
                ), approver
            )

            assertEquals(key, allowed())
            assertEquals(listOf(listOf(key) to "https://idp.example.com/token"), approver.asked)
        }

        @Test
        fun `should allow all three assertion file keys`() {
            val files = listOf("a.jwt", "b.pem", "c.json").map { File(dir, it).absolutePath }

            allow(
                mapOf(
                    "sasl.oauthbearer.assertion.file" to files[0],
                    "sasl.oauthbearer.assertion.private.key.file" to files[1],
                    "sasl.oauthbearer.assertion.template.file" to files[2],
                ), RecordingApprover(true)
            )

            assertEquals(files.joinToString(","), allowed())
        }

        @Test
        fun `should not ask again for an approved file and endpoint`() {
            val props = mapOf(
                "sasl.oauthbearer.assertion.file" to File(dir, "a.jwt").absolutePath,
                "sasl.oauthbearer.token.endpoint.url" to "https://idp.example.com/token",
            )
            allow(props, RecordingApprover(true))
            System.clearProperty(property)
            val second = RecordingApprover(false)

            allow(props, second)

            assertTrue(second.asked.isEmpty())
            assertEquals(props["sasl.oauthbearer.assertion.file"], allowed())
        }

        @Test
        fun `should ask again when the token endpoint changes`() {
            val file = File(dir, "a.jwt").absolutePath
            allow(
                mapOf(
                    "sasl.oauthbearer.assertion.file" to file,
                    "sasl.oauthbearer.token.endpoint.url" to "https://idp.example.com/token"
                ), RecordingApprover(true)
            )
            System.clearProperty(property)
            val second = RecordingApprover(true)

            allow(
                mapOf(
                    "sasl.oauthbearer.assertion.file" to file,
                    "sasl.oauthbearer.token.endpoint.url" to "https://evil.example.net/token"
                ), second
            )

            assertEquals(1, second.asked.size)
        }

        @Test
        fun `should preserve values already set with -D`() {
            System.setProperty(property, "/etc/existing.pem")
            val file = File(dir, "a.jwt").absolutePath

            allow(mapOf("sasl.oauthbearer.assertion.file" to file), RecordingApprover(true))

            assertEquals("/etc/existing.pem,$file", allowed())
        }

        @Test
        fun `should not ask for a file that is already allowed`() {
            val file = File(dir, "a.jwt").absolutePath
            System.setProperty(property, file)
            val approver = RecordingApprover(false)

            allow(mapOf("sasl.oauthbearer.assertion.file" to file), approver)

            assertTrue(approver.asked.isEmpty())
            assertEquals(file, allowed())
        }
    }

    @Nested
    inner class `without user approval` {
        @Test
        fun `should throw and allow nothing when the user declines`() {
            val approver = RecordingApprover(false)

            assertThrows(BdtConfigurationException::class.java) {
                allow(mapOf("sasl.oauthbearer.assertion.private.key.file" to File("/keys/k.pem").absolutePath), approver)
            }

            assertNull(allowed())
            assertFalse(approvals.isApproved(File("/keys/k.pem").absolutePath, null))
        }

        @Test
        fun `should do nothing and not ask when no file keys are configured`() {
            val approver = RecordingApprover(false)

            allow(
                mapOf(
                    "sasl.mechanism" to "OAUTHBEARER",
                    "sasl.oauthbearer.token.endpoint.url" to "https://idp.example.com/token"
                ), approver
            )

            assertTrue(approver.asked.isEmpty())
            assertNull(allowed())
        }

        @Test
        fun `should ignore keys that are not assertion file keys`() {
            val approver = RecordingApprover(true)

            allow(
                mapOf(
                    "ssl.truststore.location" to "/etc/ssl/trust.jks",
                    "sasl.oauthbearer.jwks.endpoint.url" to "file:///etc/passwd",
                ), approver
            )

            assertTrue(approver.asked.isEmpty())
            assertNull(allowed())
        }

        @Test
        fun `should reject paths that are not exact absolute normalized paths`() {
            val approver = RecordingApprover(true)
            val bad = listOf("relative/key.pem", "/keys/../etc/passwd", "/keys/./k.pem", "/keys/a,/etc/passwd", "", "  ")

            bad.forEach { allow(mapOf("sasl.oauthbearer.assertion.file" to it), approver) }

            assertTrue(approver.asked.isEmpty())
            assertNull(allowed())
        }
    }

    @Nested
    inner class `kafka validation` {
        @TempDir
        lateinit var dir: File

        @Test
        fun `should be rejected by kafka before and accepted after approval`() {
            val key = File(dir, "key.pem").also { it.writeText("x") }
            val props = mapOf("sasl.oauthbearer.assertion.private.key.file" to key.absolutePath)
            val utils = ConfigurationUtils(props)

            assertThrows(ConfigException::class.java) {
                utils.validateFile("sasl.oauthbearer.assertion.private.key.file")
            }

            allow(props, RecordingApprover(true))

            assertEquals(key.absoluteFile, utils.validateFile("sasl.oauthbearer.assertion.private.key.file"))
        }

        @Test
        fun `should still reject other files after approval of one`() {
            val approved = File(dir, "approved.pem").also { it.writeText("x") }
            val other = File(dir, "id_rsa").also { it.writeText("x") }
            allow(mapOf("sasl.oauthbearer.assertion.file" to approved.absolutePath), RecordingApprover(true))

            assertThrows(ConfigException::class.java) {
                ConfigurationUtils(mapOf("sasl.oauthbearer.assertion.file" to other.absolutePath))
                    .validateFile("sasl.oauthbearer.assertion.file")
            }
        }
    }
}
