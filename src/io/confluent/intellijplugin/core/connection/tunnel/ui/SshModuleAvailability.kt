package io.confluent.intellijplugin.core.connection.tunnel.ui

import com.intellij.openapi.diagnostic.Logger

/**
 * Probes whether the IntelliJ SSH/Remote platform module (`com.intellij.ssh.*`) is present
 * in the running IDE.
 *
 * The plugin references SSH classes (e.g. [com.intellij.ssh.config.unified.SshConfig],
 * [com.intellij.ssh.ui.unified.SshConfigVisibility]) to build the SSH tunnel UI, but the
 * SSH/Remote module is NOT bundled in every IDE flavor (e.g. WebStorm) and is not declared as a
 * hard plugin dependency. Constructing the tunnel UI in such an IDE throws
 * [NoClassDefFoundError]. Callers should consult [isAvailable] before constructing
 * SSH-dependent components.
 */
object SshModuleAvailability {
    private val logger = Logger.getInstance(SshModuleAvailability::class.java)

    private const val PROBE_CLASS = "com.intellij.ssh.config.unified.SshConfig"

    /**
     * `true` when the SSH/Remote module classes can be loaded by this plugin's classloader.
     *
     * Uses [Class.forName] with `initialize = false` against the classloader that loaded this
     * (plugin) class. Catches both [ClassNotFoundException] and the broader [LinkageError]
     * family (which includes [NoClassDefFoundError]) so a partially-present module also reports
     * unavailable instead of throwing at a later use site.
     */
    val isAvailable: Boolean by lazy {
        try {
            Class.forName(PROBE_CLASS, false, SshModuleAvailability::class.java.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            // Expected and common (e.g. WebStorm) — log without the stack trace; details at DEBUG.
            logger.info("SSH/Remote module not available; SSH tunnel UI will be disabled.")
            logger.debug("SSH/Remote module probe failed", e)
            false
        } catch (e: LinkageError) {
            // NoClassDefFoundError and friends: the class resolved partially but its
            // dependencies are missing. Treat as unavailable.
            logger.info("SSH/Remote module only partially available; SSH tunnel UI will be disabled.")
            logger.debug("SSH/Remote module probe failed", e)
            false
        }
    }
}
