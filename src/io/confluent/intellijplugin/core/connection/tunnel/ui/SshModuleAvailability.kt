package io.confluent.intellijplugin.core.connection.tunnel.ui

import com.intellij.openapi.diagnostic.Logger

/**
 * Whether the IntelliJ SSH/Remote module (`com.intellij.ssh.*`) is reachable from the plugin's
 * classloader. It is absent in some IDE flavors (e.g. WebStorm) and not a declared dependency, so
 * building SSH-dependent UI without checking [isAvailable] first throws [NoClassDefFoundError].
 */
object SshModuleAvailability {
    private val logger = Logger.getInstance(SshModuleAvailability::class.java)

    private const val PROBE_CLASS = "com.intellij.ssh.config.unified.SshConfig"

    /** Probed once via [Class.forName] (no init). A missing or partially-present module reports unavailable. */
    val isAvailable: Boolean by lazy {
        try {
            Class.forName(PROBE_CLASS, false, SshModuleAvailability::class.java.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            // Expected and common (e.g. WebStorm).
            logger.info("SSH/Remote module not available; SSH tunnel UI will be disabled.")
            logger.debug("SSH/Remote module probe failed", e)
            false
        } catch (e: LinkageError) {
            // Partially-present module (NoClassDefFoundError etc.) — treat as unavailable.
            logger.info("SSH/Remote module only partially available; SSH tunnel UI will be disabled.")
            logger.debug("SSH/Remote module probe failed", e)
            false
        }
    }
}
