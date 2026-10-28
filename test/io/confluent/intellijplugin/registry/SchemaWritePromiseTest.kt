package io.confluent.intellijplugin.registry

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.registry.KafkaRegistryUtil.asSchemaWritePromise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.Promise
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class SchemaWritePromiseTest {

    @Nested
    inner class `asSchemaWritePromise` {

        @Test
        fun `should resolve when the write completes`() {
            val deferred = CompletableDeferred<Unit>().apply { complete(Unit) }

            deferred.asSchemaWritePromise(TIMEOUT).blockingGet(5, TimeUnit.SECONDS)
        }

        @Test
        fun `should report a timeout as SchemaWriteTimeoutException saying the write may have succeeded`() {
            @Suppress("OPT_IN_USAGE")
            val deferred = GlobalScope.async(start = CoroutineStart.DEFAULT) {
                withTimeout(50.milliseconds) { delay(10_000) }
            }

            val error = rejectionOf(deferred.asSchemaWritePromise(TIMEOUT))

            assertInstanceOf(SchemaWriteTimeoutException::class.java, error)
            assertTrue(error.message!!.contains("may have been registered"), error.message)
            assertTrue(error.message!!.contains("${TIMEOUT.inWholeSeconds} seconds"), error.message)
        }

        @Test
        fun `should cancel the write when the promise is cancelled`() {
            val deferred = CompletableDeferred<Unit>()

            (deferred.asSchemaWritePromise(TIMEOUT) as CancellablePromise<*>).cancel()

            assertTrue(deferred.isCancelled)
        }

        @Test
        fun `should cancel the write and run attached error handlers when the original promise is cancelled`() {
            val deferred = CompletableDeferred<Unit>()
            val promise = deferred.asSchemaWritePromise(TIMEOUT)
            var handled = false
            promise.also { it.onError { handled = true } }

            (promise as CancellablePromise<*>).cancel()

            assertTrue(deferred.isCancelled)
            assertTrue(handled)
        }

        @Test
        fun `should reject a cancelled promise with a CancellationException`() {
            val promise = CompletableDeferred<Unit>().asSchemaWritePromise(TIMEOUT)
            val error = CountDownLatch(1)
            var cause: Throwable? = null
            promise.onError {
                cause = it
                error.countDown()
            }

            (promise as CancellablePromise<*>).cancel()

            assertTrue(error.await(5, TimeUnit.SECONDS))
            assertInstanceOf(CancellationException::class.java, cause)
        }

        @Test
        fun `should run onAbandoned when the promise is cancelled`() {
            val deferred = CompletableDeferred<Unit>()
            var abandoned = 0

            (deferred.asSchemaWritePromise(TIMEOUT) { abandoned++ } as CancellablePromise<*>).cancel()

            assertEquals(1, abandoned)
        }

        @Test
        fun `should run onAbandoned when the write times out`() {
            @Suppress("OPT_IN_USAGE")
            val deferred = GlobalScope.async(start = CoroutineStart.DEFAULT) {
                withTimeout(50.milliseconds) { delay(10_000) }
            }
            val abandoned = CountDownLatch(1)

            val error = rejectionOf(deferred.asSchemaWritePromise(TIMEOUT) { abandoned.countDown() })

            assertInstanceOf(SchemaWriteTimeoutException::class.java, error)
            assertTrue(abandoned.await(5, TimeUnit.SECONDS))
        }

        @Test
        fun `should not run onAbandoned for other failures or success`() {
            var abandoned = 0
            CompletableDeferred<Unit>().apply { complete(Unit) }.asSchemaWritePromise(TIMEOUT) { abandoned++ }
            rejectionOf(
                CompletableDeferred<Unit>().apply { completeExceptionally(IllegalStateException("boom")) }
                    .asSchemaWritePromise(TIMEOUT) { abandoned++ }
            )

            assertEquals(0, abandoned)
        }

        @Test
        fun `should pass other failures through unchanged`() {
            val failure = IllegalStateException("boom")
            val deferred = CompletableDeferred<Unit>().apply { completeExceptionally(failure) }

            assertSame(failure, rejectionOf(deferred.asSchemaWritePromise(TIMEOUT)))
        }
    }

    @Nested
    inner class `call timeout` {

        @Test
        fun `should default to 180 seconds`() {
            assertEquals(180, KafkaRegistryUtil.CALL_TIMEOUT.inWholeSeconds)
        }
    }

    private companion object {
        val TIMEOUT = 180.seconds
    }

    private fun rejectionOf(promise: Promise<Unit>): Throwable {
        var error: Throwable? = null
        val latch = CountDownLatch(1)
        promise.onError {
            error = it
            latch.countDown()
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "promise was not rejected" }
        return error!!
    }
}
