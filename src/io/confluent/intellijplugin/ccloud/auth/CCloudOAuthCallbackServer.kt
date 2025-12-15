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
import java.net.URI

/**
 * HTTP server to receive OAuth callback from Confluent Cloud.
 *
 * Flow:
 * 1. User clicks "Sign In" → browser opens Auth0 authorize URL
 * 2. User authenticates on Confluent Cloud
 * 3. Auth0 redirects to: http://localhost:26636/gateway/v1/callback-intellij-docs?code=XXX&state=YYY
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

    private fun handleCallback(exchange: HttpExchange) {
        val params = parseQueryParams(exchange.requestURI)
        val code = params["code"]
        val state = params["state"]
        val error = params["error"]

        logger.info("Received OAuth callback: code=${code?.take(10)}..., state=${state?.take(10)}..., error=$error")

        // Vaildate the callback parameters and handle the different response cases
        when {
            error != null -> {
                logger.warn("OAuth callback error: $error")
                sendResponse(exchange, 400, errorHtml(error))
                stopAndNotifyError(error)
            }

            state != oauthContext.oauthState -> {
                val message = "Invalid state parameter"
                logger.warn("OAuth state mismatch")
                sendResponse(exchange, 400, errorHtml(message))
                stopAndNotifyError(message)
            }

            code == null -> {
                val message = "Missing authorization code"
                logger.warn("OAuth callback missing code parameter")
                sendResponse(exchange, 400, errorHtml(message))
                stopAndNotifyError(message)
            }

            else -> {
                logger.info("OAuth callback successful, exchanging code for tokens")

                // Exchange code for tokens synchronously so we can show the correct result page
                val result = runBlocking {
                    oauthContext.createTokensFromAuthorizationCode(code)
                }

                result
                    .onSuccess { context ->
                        sendResponse(exchange, 200, successHtml(context.getUserEmail()))
                        CoroutineScope(Dispatchers.IO).launch {
                            onSuccess(context)
                            stop()
                        }
                    }
                    .onFailure { exception ->
                        val message = exception.message ?: "Token exchange failed"
                        sendResponse(exchange, 400, errorHtml(message))
                        CoroutineScope(Dispatchers.IO).launch {
                            onError(message)
                            stop()
                        }
                    }
            }
        }
    }

    /** Helper functions */

    /** Helper function to parse query params from the URI */
    private fun parseQueryParams(uri: URI): Map<String, String> {
        val query = uri.query ?: return emptyMap()
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                else null
            }
            .toMap()
    }

    /** Helper function to send the response to the client */
    private fun sendResponse(exchange: HttpExchange, statusCode: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /** Helper function to stop the server and notify the error */
    private fun stopAndNotifyError(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            onError(message)
            stop()
        }
    }

    private fun successHtml(email: String): String {
        return SUCCESS_TEMPLATE
            .replace("{{email}}", email.escapeHtml())
            .replace("{{confluent_cloud_homepage}}", CONFLUENT_CLOUD_HOMEPAGE)
    }

    private fun errorHtml(message: String): String {
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
