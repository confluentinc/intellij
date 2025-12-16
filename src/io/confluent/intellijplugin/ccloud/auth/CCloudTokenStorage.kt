package io.confluent.intellijplugin.ccloud.auth

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Secure token persistence using IntelliJ's PasswordSafe.
 * Stores only what's needed for session restoration.
 */
object CCloudTokenStorage {
    private val logger = thisLogger()

    private const val SERVICE_NAME = "ConfluentCloud"
    private const val SESSION_KEY = "OAuthSession"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class StoredSession(
        // Required for token refresh
        val refreshToken: String,
        val refreshTokenExpiresAt: Long,
        val endOfLifetime: Long,
        val organizationId: String?,
        // Display only
        val userEmail: String? = null,
        val organizationName: String? = null
    )

    fun saveSession(context: CCloudOAuthContext) {
        val refreshToken = context.getRefreshToken() ?: run {
            logger.warn("Cannot save session: no refresh token")
            return
        }

        val session = StoredSession(
            refreshToken = refreshToken.token,
            refreshTokenExpiresAt = refreshToken.expiresAt.toEpochMilli(),
            endOfLifetime = context.getEndOfLifetime()?.toEpochMilli() ?: 0,
            organizationId = context.getCurrentOrganization()?.id,
            userEmail = context.getUserEmail(),
            organizationName = context.getCurrentOrganization()?.name
        )

        try {
            val credentials = Credentials(SESSION_KEY, json.encodeToString(session))
            PasswordSafe.instance[credentialAttributes()] = credentials
            logger.info("Session saved for user: ${session.userEmail}")
        } catch (e: Exception) {
            logger.error("Failed to save session", e)
        }
    }

    fun loadSession(): StoredSession? {
        return try {
            val credentials = PasswordSafe.instance[credentialAttributes()]
            val sessionJson = credentials?.getPasswordAsString() ?: return null
            val session = json.decodeFromString<StoredSession>(sessionJson)

            // Check expiration
            val now = System.currentTimeMillis()
            when {
                session.endOfLifetime > 0 && session.endOfLifetime < now -> {
                    logger.info("Stored session expired (end of lifetime)")
                    clearSession()
                    null
                }
                session.refreshTokenExpiresAt < now -> {
                    logger.info("Stored refresh token expired")
                    clearSession()
                    null
                }
                else -> {
                    logger.info("Session loaded for user: ${session.userEmail}")
                    session
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load session", e)
            null
        }
    }

    fun clearSession() {
        try {
            PasswordSafe.instance[credentialAttributes()] = null
            logger.info("Session cleared")
        } catch (e: Exception) {
            logger.error("Failed to clear session", e)
        }
    }

    private fun credentialAttributes() = CredentialAttributes(generateServiceName(SERVICE_NAME, SESSION_KEY))
}
