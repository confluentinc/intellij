package io.confluent.intellijplugin.ccloud.client

import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.testutil.fixture
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal const val TEST_CONTROL_PLANE_TOKEN = "test-control-plane-token"
internal const val TEST_DATA_PLANE_TOKEN = "test-data-plane-token"

/** Auth service that is signed in and returns [TEST_CONTROL_PLANE_TOKEN] / [TEST_DATA_PLANE_TOKEN]. */
internal fun signedInAuthService(): CCloudAuthService = mock {
    on { isSignedIn() } doReturn true
    on { getControlPlaneToken() } doReturn TEST_CONTROL_PLANE_TOKEN
    on { getDataPlaneToken() } doReturn TEST_DATA_PLANE_TOKEN
}

/** [CCloudRestClient] pointed at [baseUrl] with a signed-in mock auth service. */
internal fun testClient(
    baseUrl: String,
    authType: CCloudRestClient.AuthType = CCloudRestClient.AuthType.DATA_PLANE,
    authService: CCloudAuthService = signedInAuthService(),
    additionalHeaders: Map<String, String> = emptyMap(),
    retryPolicy: CCloudRetryPolicy = CCloudRetryPolicy.DEFAULT,
    readTimeoutMs: Int = CCloudRestClient.READ_TIMEOUT_MS
): CCloudRestClient = CCloudRestClient(
    baseUrl = baseUrl,
    authType = authType,
    additionalHeaders = additionalHeaders,
    authService = authService,
    retryPolicy = retryPolicy,
    readTimeoutMs = readTimeoutMs
)

/** Reads `/fixtures/ccloud/<name>` as text. */
internal fun ccloudFixture(name: String): String = fixture("ccloud", name)

/** Reads `/fixtures/ccloud-resources/<name>` (CCloud REST resource responses) as text. */
internal fun ccloudResourceFixture(name: String): String = fixture("ccloud-resources", name)
