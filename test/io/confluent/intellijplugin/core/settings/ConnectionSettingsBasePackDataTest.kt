package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.settings.connections.ConnectionConfigurable
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionGroup
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ObjectOutputStream
import java.io.Serializable

/** A value whose writeObject throws an Error, simulating NoClassDefFoundError/LinkageError. */
class ExplodingSerializableValue : Serializable {
    @Suppress("UNUSED_PARAMETER", "unused")
    private fun writeObject(out: ObjectOutputStream) {
        throw NoClassDefFoundError("com/intellij/ssh/config/unified/SshConfig")
    }
}

class PackDataTestConnectionData : ConnectionData() {
    @Suppress("unused")
    var goodValue: String = "keep-me"

    @Suppress("unused")
    var explodingValue: ExplodingSerializableValue? = ExplodingSerializableValue()

    override fun createDriver(project: Project?, isTest: Boolean): Driver =
        throw UnsupportedOperationException("not needed for test")

    override fun createConfigurable(project: Project, parentGroup: ConnectionGroup): ConnectionConfigurable<*, *> =
        throw UnsupportedOperationException("not needed for test")
}

/**
 * Regression test for INTELLIJ-PLUGIN-S4: a single connection property whose value cannot be
 * Java-serialized (e.g. its class graph references com.intellij.ssh.config.unified.SshConfig,
 * unloadable on the plugin classloader, causing NoClassDefFoundError during writeObject) must not
 * abort the entire getState()/packData. The bad property is skipped; the rest are still packed.
 */
@TestApplication
class ConnectionSettingsBasePackDataTest {

    @Test
    fun `packData skips unserializable property and keeps the rest`() {
        val conn = PackDataTestConnectionData().apply {
            name = "my-conn"
            uri = "localhost:9092"
        }

        val ext = ConnectionSettingsBase.packData(conn)

        // The unserializable property must be skipped rather than aborting the whole pack.
        assertFalse(ext.extended.containsKey("explodingValue"), "exploding property should be skipped")
        // A normal serializable property must still be present.
        assertTrue(ext.extended.containsKey("goodValue"), "serializable property should be retained")
    }
}
