package io.confluent.intellijplugin.ccloud.auth

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit.Stubbing
import io.confluent.intellijplugin.ccloud.util.ResourceLoader

/*
 * WireMock stubs for the three token endpoints (oauth/token, api/sessions, api/access_tokens).
 * They extend [Stubbing], so they work on both a WireMockServer and a WireMockExtension.
 */

/** Control plane token the success stub sets in the `auth_token` cookie. */
internal const val TEST_AUTH_CONTROL_PLANE_TOKEN = "test_cp_token"

/** Reads `fixtures/ccloud-auth/<name>` as text. */
internal fun ccloudAuthFixture(name: String): String = ResourceLoader.loadResource("fixtures/ccloud-auth/$name")

/** Stub a successful sign-in or refresh: every token endpoint answers 200 from the fixtures. */
internal fun Stubbing.stubTokenEndpointsSuccess() {
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
                .withHeader("Set-Cookie", "auth_token=$TEST_AUTH_CONTROL_PLANE_TOKEN; Path=/; HttpOnly")
                .withBody(ccloudAuthFixture("control-plane-session-success.json"))
        )
    )
    stubFor(
        WireMock.post("/api/access_tokens").willReturn(
            WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(ccloudAuthFixture("data-plane-token-success.json"))
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
