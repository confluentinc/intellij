package io.confluent.intellijplugin.ccloud.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant

@TestApplication
class CCloudTokenRefreshBeanTest {

    private lateinit var parentDisposable: Disposable

    @BeforeEach
    fun setUp() {
        parentDisposable = Disposer.newDisposable("test")
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(parentDisposable)
    }

    @Nested
    @DisplayName("lifecycle")
    inner class Lifecycle {

        @Test
        fun `start begins scheduler`() {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn false
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)

            bean.start()

            assertTrue(bean.isRunning())
        }

        @Test
        fun `stop ends scheduler`() {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn false
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            bean.stop()

            assertFalse(bean.isRunning())
        }

        @Test
        fun `start is idempotent`() {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn false
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)

            bean.start()
            bean.start()

            bean.stop()

            assertFalse(bean.isRunning())
        }

        @Test
        fun `dispose stops scheduler`() {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn false
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            bean.dispose()

            assertFalse(bean.isRunning())
        }
    }

    @Nested
    @DisplayName("refreshIfNeeded")
    inner class RefreshIfNeeded {

        @Test
        fun `calls refresh when shouldAttemptTokenRefresh is true`() = runBlocking {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn false
                on { shouldAttemptTokenRefresh() } doReturn true
                on { expiresAt() } doReturn Instant.now().plusSeconds(60)
                on { getFailedTokenRefreshAttempts() } doReturn 0
                onBlocking { refresh(null) } doReturn Result.success(mock)
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            // Wait to ensure the scheduler has had time to run at least one check cycle
            delay(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL.inWholeMilliseconds + 500)

            verify(context, atLeastOnce()).refresh(null)
            bean.stop()
        }

        @Test
        fun `skips refresh when shouldAttemptTokenRefresh is false`() = runBlocking {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn false
                on { shouldAttemptTokenRefresh() } doReturn false
                on { expiresAt() } doReturn Instant.now().plusSeconds(3600)
                on { getFailedTokenRefreshAttempts() } doReturn 0
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            // Wait to ensure the scheduler has had time to run at least one check cycle
            delay(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL.inWholeMilliseconds + 500)

            verify(context, never()).refresh(null)
            bean.stop()
        }
    }

    @Nested
    @DisplayName("shouldContinueRefreshing")
    inner class ShouldContinueRefreshing {

        @Test
        fun `stops loop on non-transient error`() = runBlocking {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn true
                on { getFailedTokenRefreshAttempts() } doReturn CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            // Wait to ensure a gap between the start and the stop condition is checked
            delay(100)

            assertFalse(bean.isRunning())
        }

        @Test
        fun `stops loop on end of lifetime`() = runBlocking {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn true
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            // Wait to ensure a gap between the start and the stop condition is checked
            delay(100)

            assertFalse(bean.isRunning())
        }

        @Test
        fun `continues after transient failure`() = runBlocking {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn false
                on { hasReachedEndOfLifetime() } doReturn false
                on { shouldAttemptTokenRefresh() } doReturn true
                on { expiresAt() } doReturn Instant.now().plusSeconds(60)
                on { getFailedTokenRefreshAttempts() } doReturn 1
                onBlocking { refresh(null) } doReturn Result.failure(RuntimeException("Transient error"))
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            delay(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL.inWholeMilliseconds + 500)

            assertTrue(bean.isRunning())
            bean.stop()
        }

        @Test
        fun `stops after max retry attempts reached`() = runBlocking {
            val context = mock<CCloudOAuthContext> {
                on { hasNonTransientError() } doReturn true
                on { hasReachedEndOfLifetime() } doReturn false
                on { getFailedTokenRefreshAttempts() } doReturn CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS
            }
            val bean = CCloudTokenRefreshBean(context, parentDisposable)
            bean.start()

            // Wait to ensure a gap between the start and the stop condition is checked
            delay(100)

            assertFalse(bean.isRunning())
        }
    }
}
