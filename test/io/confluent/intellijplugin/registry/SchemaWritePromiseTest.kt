package io.confluent.intellijplugin.registry

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.model.DataModel
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
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class SchemaWritePromiseTest {

    @Nested
    inner class `asSchemaWritePromise` {

        @Test
        fun `should resolve when the write completes`() {
            val deferred = CompletableDeferred<Unit>().apply { complete(Unit) }

            deferred.asSchemaWritePromise().blockingGet(5, TimeUnit.SECONDS)
        }

        @Test
        fun `should report a timeout as SchemaWriteTimeoutException saying the write may have succeeded`() {
            @Suppress("OPT_IN_USAGE")
            val deferred = GlobalScope.async(start = CoroutineStart.DEFAULT) {
                withTimeout(50.milliseconds) { delay(10_000) }
            }

            val error = rejectionOf(deferred.asSchemaWritePromise())

            assertInstanceOf(SchemaWriteTimeoutException::class.java, error)
            assertTrue(error.message!!.contains("may have been registered"), error.message)
            assertTrue(error.message!!.contains("${KafkaRegistryUtil.CALL_TIMEOUT.inWholeSeconds} seconds"), error.message)
        }

        @Test
        fun `should pass other failures through unchanged`() {
            val failure = IllegalStateException("boom")
            val deferred = CompletableDeferred<Unit>().apply { completeExceptionally(failure) }

            assertSame(failure, rejectionOf(deferred.asSchemaWritePromise()))
        }
    }

    @Nested
    inner class `call timeout` {

        @Test
        fun `should default to 180 seconds`() {
            assertEquals(180, KafkaRegistryUtil.CALL_TIMEOUT.inWholeSeconds)
        }
    }

    @Nested
    inner class `updateSchemaAwaitingNewVersion` {

        private val model = mock<DataModel<List<Long>>>()

        private fun attachedListener(): DataModelListener {
            val captor = argumentCaptor<DataModelListener>()
            verify(model).addListener(captor.capture())
            return captor.firstValue
        }

        @Test
        fun `should call back and detach the listener once the versions change`() {
            var changes = 0
            val update = AsyncPromise<Unit>()

            KafkaRegistryUtil.updateSchemaAwaitingNewVersion(model, { changes++ }) { update }
            val listener = attachedListener()
            listener.onChanged()

            assertEquals(1, changes)
            verify(model).removeListener(listener)
        }

        @Test
        fun `should keep the listener while the update is pending or succeeds`() {
            val update = AsyncPromise<Unit>()

            KafkaRegistryUtil.updateSchemaAwaitingNewVersion(model, {}) { update }
            update.setResult(Unit)

            val listener = attachedListener()
            verify(model, never()).removeListener(listener)
        }

        @Test
        fun `should detach the listener when the update fails`() {
            val update = AsyncPromise<Unit>()

            KafkaRegistryUtil.updateSchemaAwaitingNewVersion(model, {}) { update }
            update.setError(IllegalStateException("boom"))

            val listener = attachedListener()
            verify(model).removeListener(listener)
        }
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
