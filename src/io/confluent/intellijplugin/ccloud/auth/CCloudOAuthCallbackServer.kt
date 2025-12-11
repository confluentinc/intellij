package io.confluent.intellijplugin.ccloud.auth

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
) {
    // TODO
}
