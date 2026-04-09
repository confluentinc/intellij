package io.confluent.intellijplugin.ccloud.client

import io.confluent.intellijplugin.telemetry.TelemetryUtils

/**
 * Provides the User-Agent string for all Confluent Cloud API requests.
 *
 * Format: `confluent-for-intellij/v<version> (https://confluent.io; support@confluent.io)`
 */
object CCloudUserAgent {
    const val HEADER_NAME = "User-Agent"

    fun headerValue(): String {
        val version = TelemetryUtils.getPluginVersion()
        return "confluent-for-intellij/v$version (https://confluent.io; support@confluent.io)"
    }
}
