package io.confluent.intellijplugin.ccloud.auth

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit.Stubbing
import io.confluent.intellijplugin.testutil.fixture

/*
 * WireMock stubs for the three token endpoints (oauth/token, api/sessions, api/access_tokens).
 * They extend [Stubbing], so they can be called on a WireMockExtension.
 */

/** Control plane token the success stub sets in the `auth_token` cookie. */
internal const val TEST_AUTH_CONTROL_PLANE_TOKEN = "test_cp_token"

/** Reads `fixtures/ccloud-auth/<name>` as text. */
internal fun ccloudAuthFixture(name: String): String = fixture("ccloud-auth", name)

/**
 * Stub a successful sign-in or refresh: every token endpoint answers 200 from the fixtures.
 * Calling it again replaces the earlier success stubs, since WireMock prefers the newest stub.
 * @param controlPlaneToken Value of the `auth_token` cookie, set with prod's cookie attributes
 * @param dataPlaneFixture Data plane token response under `fixtures/ccloud-auth/`
 */
internal fun Stubbing.stubTokenEndpointsSuccess(
    controlPlaneToken: String = TEST_AUTH_CONTROL_PLANE_TOKEN,
    dataPlaneFixture: String = "data-plane-token-success.json",
) {
    stubFor(
        WireMock.post("/oauth/token").willReturn(
            WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(ccloudAuthFixture("oauth-token-success.json"))
        )
    )
    stubFor(
        WireMock.post("/api/sessions").willReturn(
            WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withHeader(
                    "Set-Cookie",
                    "auth_token=$controlPlaneToken; Path=/; SameSite=strict; secure; Max-Age=86400; HttpOnly; Secure"
                )
                .withBody(ccloudAuthFixture("control-plane-session-success.json"))
        )
    )
    stubFor(
        WireMock.post("/api/access_tokens").willReturn(
            WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(ccloudAuthFixture(dataPlaneFixture))
        )
    )
}

/**
 * Make the POST to [path] fail with [status]. Takes priority over [stubTokenEndpointsSuccess].
 * @param fixture Body file under `fixtures/ccloud-auth/`, if any
 * @param retryAfter `Retry-After` header value, if any
 * @param delayMs Delay before the response is sent
 */
internal fun Stubbing.stubTokenEndpointFailure(
    path: String,
    status: Int,
    fixture: String? = null,
    retryAfter: String? = null,
    delayMs: Int = 0,
) {
    val response = WireMock.aResponse().withStatus(status).withHeader("Content-Type", "application/json")
    fixture?.let { response.withBody(ccloudAuthFixture(it)) }
    retryAfter?.let { response.withHeader("Retry-After", it) }
    if (delayMs > 0) response.withFixedDelay(delayMs)
    stubFor(WireMock.post(path).willReturn(response).atPriority(1))
}

private val TOKEN_ENDPOINT_PROPERTIES = mapOf(
    "ccloud.oauth.token-uri" to "/oauth/token",
    "ccloud.control-plane.token-exchange-uri" to "/api/sessions",
    "ccloud.data-plane.token-exchange-uri" to "/api/access_tokens",
)

/**
 * Point the three token endpoint system properties at [baseUrl]. Call it per test (from `@BeforeEach`),
 * since a WireMock extension can move to a new port for each `@Nested` class.
 */
internal fun pointTokenEndpointsAt(baseUrl: String) {
    TOKEN_ENDPOINT_PROPERTIES.forEach { (property, path) -> System.setProperty(property, "$baseUrl$path") }
}

/** Clear the properties set by [pointTokenEndpointsAt]. */
internal fun clearTokenEndpointProperties() {
    TOKEN_ENDPOINT_PROPERTIES.keys.forEach(System::clearProperty)
}

private const val FALLBACK_TOKEN_LIFETIME_PROPERTY = "ccloud.control-plane.token-lifetime-seconds"

/** Set the lifetime given to tokens that aren't JWTs, such as the default stubbed tokens. */
internal fun setFallbackTokenLifetime(seconds: Long) {
    System.setProperty(FALLBACK_TOKEN_LIFETIME_PROPERTY, seconds.toString())
}

/** Clear the property set by [setFallbackTokenLifetime]. */
internal fun clearFallbackTokenLifetime() {
    System.clearProperty(FALLBACK_TOKEN_LIFETIME_PROPERTY)
}
