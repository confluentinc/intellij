package io.confluent.intellijplugin.client

import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.DeleteConsumerGroupsOptions
import org.apache.kafka.clients.admin.DeleteConsumerGroupsResult
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.errors.GroupNotEmptyException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.internals.KafkaFutureImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class KafkaClientDeleteConsumerGroupTest {

    private fun adminReturning(future: KafkaFuture<Void>): Admin {
        val result = mock<DeleteConsumerGroupsResult> {
            on { all() } doReturn future
        }
        return mock<Admin> {
            on { deleteConsumerGroups(any<Collection<String>>(), any<DeleteConsumerGroupsOptions>()) } doReturn result
        }
    }

    private fun failedFuture(t: Throwable): KafkaFuture<Void> =
        KafkaFutureImpl<Void>().apply { completeExceptionally(t) }

    @Nested
    inner class Success {

        @Test
        fun `should complete when broker confirms delete`() = runBlocking {
            val admin = adminReturning(KafkaFuture.completedFuture(null))

            KafkaClient.deleteConsumerGroupAndAwait(admin, "group-1", 15_000)

            val options = argumentCaptor<DeleteConsumerGroupsOptions>()
            verify(admin).deleteConsumerGroups(eq(listOf("group-1")), options.capture())
            assertEquals(15_000, options.firstValue.timeoutMs())
        }
    }

    @Nested
    inner class Failure {

        @Test
        fun `should rethrow broker error unwrapped when delete fails`() {
            val cause = GroupNotEmptyException("The group is not empty.")
            val admin = adminReturning(failedFuture(cause))

            val thrown = assertThrows<GroupNotEmptyException> {
                runBlocking { KafkaClient.deleteConsumerGroupAndAwait(admin, "group-1", 15_000) }
            }
            assertSame(cause, thrown)
        }

        @Test
        fun `should rethrow timeout unwrapped when admin call times out`() {
            val cause = TimeoutException("Timed out waiting for a node assignment.")
            val admin = adminReturning(failedFuture(cause))

            val thrown = assertThrows<TimeoutException> {
                runBlocking { KafkaClient.deleteConsumerGroupAndAwait(admin, "group-1", 15_000) }
            }
            assertSame(cause, thrown)
        }
    }
}
