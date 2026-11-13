package io.confluent.intellijplugin.testutil

import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension

/**
 * WireMock server on a dynamic port, reset before each test. Register it from a companion object with
 * `@JvmField @RegisterExtension`. The port can change between `@Nested` classes, so read
 * [WireMockExtension.baseUrl] per test rather than caching it.
 */
internal fun wireMockExtension(): WireMockExtension =
    WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build()
