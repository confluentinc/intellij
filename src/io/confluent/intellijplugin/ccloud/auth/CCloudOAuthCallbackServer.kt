package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.diagnostic.thisLogger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLHandshakeException

/**
 * HTTP server to receive OAuth callback from Confluent Cloud.
 *
 * Flow:
 * 1. User clicks "Sign In" → browser opens Auth0 authorize URL
 * 2. User authenticates on Confluent Cloud
 * 3. Auth0 redirects to: http://localhost:26638/gateway/v1/callback-intellij-docs?code=XXX&state=YYY
 * 4. This server receives the callback, validates state, and triggers token exchange
 */
class CCloudOAuthCallbackServer(
    private val oauthContext: CCloudOAuthContext,
    private val onSuccess: (CCloudOAuthContext) -> Unit,
    private val onError: (String) -> Unit
) {
    private var server: HttpServer? = null

    companion object {
        private val logger = thisLogger()
        private const val CONFLUENT_CLOUD_HOMEPAGE = "https://confluent.cloud"

        private const val TLS_HANDSHAKE_ERROR_MESSAGE =
            "Failed to perform the SSL/TLS handshake."

        private val STYLES: String by lazy {
            loadResource("/templates/styles.html")
        }

        private val SUCCESS_TEMPLATE: String by lazy {
            loadTemplate("/templates/callback-success.html")
        }

        private val FAILURE_TEMPLATE: String by lazy {
            loadTemplate("/templates/callback-failure.html")
        }

        private fun loadResource(resourcePath: String): String {
            return CCloudOAuthCallbackServer::class.java.getResourceAsStream(resourcePath)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Failed to load resource: $resourcePath")
        }

        private fun loadTemplate(resourcePath: String): String {
            return loadResource(resourcePath)
                .replace("{#include styles/}", STYLES)
        }

        /** Helper function to parse query string into a map of key-value pairs, handling URL decoding */
        internal fun parseQueryString(query: String?): Map<String, String> {
            if (query.isNullOrEmpty()) return emptyMap()
            return query.split("&")
                .mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                    else null
                }
                .toMap()
        }
    }

    /**
     * Start the callback server on the configured port.
     * Server will automatically stop after handling one callback.
     */
    fun start() {
        if (server != null) {
            logger.warn("Callback server already running")
            return
        }

        try {
            server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), CCloudOAuthConfig.CALLBACK_PORT), 0).apply {
                createContext(CCloudOAuthConfig.CALLBACK_PATH) { exchange ->
                    handleCallback(exchange)
                }
                start()
            }
            logger.info("OAuth callback server started on port ${CCloudOAuthConfig.CALLBACK_PORT}")
        } catch (e: Exception) {
            logger.error("Failed to start OAuth callback server", e)
            onError("Failed to start callback server: ${e.message}")
        }
    }

    /**
     * Stop the callback server.
     */
    fun stop() {
        server?.stop(0)
        server = null
        logger.info("OAuth callback server stopped")
    }

    /**
     * Result of processing an OAuth callback. On success, the success context is passed to continue token exchange. On error, the OAuth error message is displayed to the user.
     */
    internal data class CallbackResult(
        val statusCode: Int,
        val html: String,
        val successContext: CCloudOAuthContext? = null,
        val errorMessage: String? = null
    )

    /**
     * Categorize an exception and return appropriate HTTP status code and user message.
     * Uses the actual HTTP status code from OAuthErrorException when available.
     */
    internal fun getErrorStatusAndMessage(exception: Throwable): Pair<Int, String> {
        return when (exception) {
            is SSLHandshakeException -> 500 to TLS_HANDSHAKE_ERROR_MESSAGE

            is OAuthErrorException -> { exception.httpStatusCode to "Retrieving ID token failed: ${exception.errorCode} ${exception.errorCode} - ${exception.errorDescription}." }

            // Assume network/server errors
            else -> 500 to (exception.message ?: "Token exchange failed")
        }
    }

    /**
     * Process callback parameters and return the result without side effects.
     * This pure function is to improve testing easability.
     * @param code The authorization code
     * @param state The state parameter
     * @param error The error parameter
     * @return The callback result
     */
    internal suspend fun processCallback(code: String?, state: String?, error: String?): CallbackResult {
        return when {
            error != null -> {
                logger.warn("OAuth callback error: $error")
                CallbackResult(400, errorHtml(error), errorMessage = error)
            }

            state != oauthContext.oauthState -> {
                val message = "Invalid state parameter"
                logger.warn("OAuth state mismatch")
                CallbackResult(400, errorHtml(message), errorMessage = message)
            }

            code == null -> {
                val message = "Missing authorization code"
                logger.warn("OAuth callback missing code parameter")
                CallbackResult(400, errorHtml(message), errorMessage = message)
            }

            else -> {
                logger.info("OAuth callback successful, exchanging code for tokens")
                val tokenResult = oauthContext.createTokensFromAuthorizationCode(code)

                tokenResult.fold(
                    onSuccess = { context ->
                        CallbackResult(200, successHtml(context.getUserEmail()), successContext = context)
                    },
                    onFailure = { exception ->
                        val (statusCode, message) = getErrorStatusAndMessage(exception)
                        CallbackResult(statusCode, errorHtml(message), errorMessage = message)
                    }
                )
            }
        }
    }

    private fun handleCallback(exchange: HttpExchange) {
        val params = parseQueryString(exchange.requestURI.query)
        logger.info("Received OAuth callback: code=${params["code"]?.take(10)}..., state=${params["state"]?.take(10)}..., error=${params["error"]}")

        // Process callback synchronously so we can show the correct result page to the user.
        // The browser is waiting for the HTTP response, so we must block until token exchange completes.
        val result = runBlocking {
            processCallback(params["code"], params["state"], params["error"])
        }

        sendResponse(exchange, result.statusCode, result.html)

        CoroutineScope(Dispatchers.IO).launch {
            when {
                result.successContext != null -> onSuccess(result.successContext)
                result.errorMessage != null -> onError(result.errorMessage)
            }
            stop()
        }
    }


    private fun sendResponse(exchange: HttpExchange, statusCode: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    internal fun successHtml(email: String): String {
        return SUCCESS_TEMPLATE
            .replace("{{email}}", email.escapeHtml())
            .replace("{{confluent_cloud_homepage}}", CONFLUENT_CLOUD_HOMEPAGE)
    }

    internal fun errorHtml(message: String): String {
        return FAILURE_TEMPLATE
            .replace("{{error}}", message.escapeHtml())
    }

    /** Helper function to escape HTML characters in a string for safe display in HTML */
    private fun String.escapeHtml(): String {
        return this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}