package io.confluent.intellijplugin.core.util

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.client.CCloudApiException
import io.confluent.intellijplugin.ccloud.client.CCloudConnectException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ConnectException

@TestApplication
class PresentableTextTest {

    @Test
    fun `should return CCloudApiException message as-is`() {
        assertEquals("Not found (HTTP 404)", CCloudApiException("Not found (HTTP 404)", 404).toPresentableText())
    }

    @Test
    fun `should return CCloudApiException subclass message as-is`() {
        val exception = CCloudConnectException("Request failed: Connection refused", ConnectException("Connection refused"))
        assertEquals("Request failed: Connection refused", exception.toPresentableText())
    }

    @Test
    fun `should prefix other exceptions with their class name`() {
        assertEquals("IllegalStateException: boom", IllegalStateException("boom").toPresentableText())
    }
}
