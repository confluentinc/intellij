package io.confluent.intellijplugin.ccloud.client

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class CCloudUserAgentTest {

    @Test
    fun `headerValue matches expected format`() {
        val userAgent = CCloudUserAgent.headerValue()

        assertTrue(
            userAgent.matches(Regex("confluent-for-intellij/v.+ \\(https://confluent\\.io; support@confluent\\.io\\)")),
            "User-Agent '$userAgent' does not match expected format"
        )
    }
}
