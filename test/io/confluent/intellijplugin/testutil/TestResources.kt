package io.confluent.intellijplugin.testutil

import java.net.ServerSocket

/** Reads the test resource `/fixtures/<dir>/<name>` as text. */
internal fun fixture(dir: String, name: String): String {
    val path = "/fixtures/${dir.trim('/')}/$name"
    val stream = requireNotNull(object {}.javaClass.getResourceAsStream(path)) { "Missing test fixture $path" }
    return stream.bufferedReader().use { it.readText() }
}

/** A localhost port with nothing listening on it. */
internal fun closedPort(): Int = ServerSocket(0).use { it.localPort }
