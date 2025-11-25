package io.confluent.intellijplugin.ccloud.auth

/**
 * This file will be renamed or deleted later
 * Provider interface for OAuth authentication to Confluent Cloud.
 * This interface is assumed to be implemented as part of the OAuth integration (in progress).
 */
interface CloudOAuthProvider {
    /**
     * Get the access token for the given connection.
     */
    fun getAccessToken(connectionId: String): String?

    /**
     * Get the authorization headers for the given connection.
     */
    fun getHeaders(connectionId: String): Map<String, String>
}
