package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.settings.connections.ConnectionConfigurable
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.NotSerializableException
import java.io.ObjectOutputStream
import java.io.Serializable

/** Value whose writeObject throws an Error, standing in for a missing-module NoClassDefFoundError. */
class UnloadableValue : Serializable {
    @Suppress("UNUSED_PARAMETER", "unused")
    private fun writeObject(out: ObjectOutputStream) {
        throw NoClassDefFoundError("com/example/AbsentModuleClass")
    }
}

/** Value whose writeObject throws a checked exception, standing in for a non-serializable field. */
class UnserializableValue : Serializable {
    @Suppress("UNUSED_PARAMETER", "unused")
    private fun writeObject(out: ObjectOutputStream) {
        throw NotSerializableException("deliberately unserializable")
    }
}

class PackDataTestConnectionData : ConnectionData() {
    @Suppress("unused")
    var goodValue: String = "keep-me"

    @Suppress("unused")
    var unloadableValue: UnloadableValue? = null

    @Suppress("unused")
    var unserializableValue: UnserializableValue? = null

    override fun createDriver(project: Project?, isTest: Boolean): Driver =
        throw UnsupportedOperationException("not needed for this test")

    override fun createConfigurable(project: Project, parentGroup: ConnectionGroup): ConnectionConfigurable<*, *> =
        throw UnsupportedOperationException("not needed for this test")
}

/**
 * A single connection property that cannot be Java-serialized must not abort the whole
 * [ConnectionSettingsBase.packData] / `getState()`. The offending property is skipped and the rest
 * are still packed, mirroring the defensive read path in `unpackData`.
 */
@TestApplication
class ConnectionSettingsBasePackDataTest {

    @Test
    fun `should skip a property that throws a LinkageError and keep the rest`() {
        val conn = PackDataTestConnectionData().apply {
            name = "my-conn"
            uri = "localhost:9092"
            unloadableValue = UnloadableValue()
        }

        val ext = ConnectionSettingsBase.packData(conn)

        assertFalse(ext.extended.containsKey("unloadableValue"), "unloadable property should be skipped")
        assertTrue(ext.extended.containsKey("goodValue"), "serializable property should be retained")
        assertEquals("my-conn", ext.name)
        assertEquals("localhost:9092", ext.uri)
    }

    @Test
    fun `should skip a property that throws a serialization exception and keep the rest`() {
        val conn = PackDataTestConnectionData().apply {
            name = "my-conn"
            unserializableValue = UnserializableValue()
        }

        val ext = ConnectionSettingsBase.packData(conn)

        assertFalse(ext.extended.containsKey("unserializableValue"), "unserializable property should be skipped")
        assertTrue(ext.extended.containsKey("goodValue"), "serializable property should be retained")
    }

    @Test
    fun `should pack every property when all of them serialize`() {
        val conn = PackDataTestConnectionData().apply { name = "my-conn" }

        val ext = ConnectionSettingsBase.packData(conn)

        assertTrue(ext.extended.containsKey("goodValue"), "serializable property should be retained")
        assertFalse(ext.extended.containsKey("unloadableValue"), "null property should not be packed")
        assertFalse(ext.extended.containsKey("unserializableValue"), "null property should not be packed")
    }
}
