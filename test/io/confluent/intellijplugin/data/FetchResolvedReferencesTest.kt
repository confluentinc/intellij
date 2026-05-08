package io.confluent.intellijplugin.data

import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.SchemaReferenceResponse
import io.confluent.intellijplugin.ccloud.model.response.SchemaVersionResponse
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class FetchResolvedReferencesTest {

    @Test
    fun `should return empty map for empty references`() = runBlocking {
        val fetcher = mock<DataPlaneFetcher>()

        val result = fetchResolvedReferences(fetcher, emptyList())

        assertTrue(result.isEmpty())
        verifyNoInteractions(fetcher)
    }

    @Test
    fun `should fetch single reference`() = runBlocking {
        val fetcher = mock<DataPlaneFetcher> {
            onBlocking { getSchemaVersionInfo("Address", 1L) } doReturn schemaResponse(
                subject = "Address",
                version = 1,
                schema = "address-schema-text"
            )
        }
        val refs = listOf(SchemaReference("com.example.Address", "Address", 1))

        val result = fetchResolvedReferences(fetcher, refs)

        assertEquals(mapOf("com.example.Address" to "address-schema-text"), result)
    }

    @Test
    fun `should resolve transitive references`() = runBlocking {
        val fetcher = mock<DataPlaneFetcher> {
            onBlocking { getSchemaVersionInfo("Address", 1L) } doReturn schemaResponse(
                subject = "Address",
                version = 1,
                schema = "address-schema",
                references = listOf(SchemaReferenceResponse("com.example.Country", "Country", 1))
            )
            onBlocking { getSchemaVersionInfo("Country", 1L) } doReturn schemaResponse(
                subject = "Country",
                version = 1,
                schema = "country-schema"
            )
        }
        val refs = listOf(SchemaReference("com.example.Address", "Address", 1))

        val result = fetchResolvedReferences(fetcher, refs)

        assertEquals(
            mapOf(
                "com.example.Address" to "address-schema",
                "com.example.Country" to "country-schema"
            ),
            result
        )
    }

    @Test
    fun `should fetch each subject and version only once`() = runBlocking {
        val fetcher = mock<DataPlaneFetcher> {
            onBlocking { getSchemaVersionInfo("Address", 1L) } doReturn schemaResponse(
                subject = "Address",
                version = 1,
                schema = "address-schema"
            )
        }
        val refs = listOf(
            SchemaReference("com.example.Address", "Address", 1),
            SchemaReference("com.example.Address", "Address", 1)
        )

        fetchResolvedReferences(fetcher, refs)

        verify(fetcher, times(1)).getSchemaVersionInfo("Address", 1L)
    }

    @Test
    fun `should terminate on a cycle`() = runBlocking {
        val fetcher = mock<DataPlaneFetcher> {
            onBlocking { getSchemaVersionInfo("A", 1L) } doReturn schemaResponse(
                subject = "A",
                version = 1,
                schema = "schema-a",
                references = listOf(SchemaReferenceResponse("com.example.B", "B", 1))
            )
            onBlocking { getSchemaVersionInfo("B", 1L) } doReturn schemaResponse(
                subject = "B",
                version = 1,
                schema = "schema-b",
                references = listOf(SchemaReferenceResponse("com.example.A", "A", 1))
            )
        }
        val refs = listOf(SchemaReference("com.example.A", "A", 1))

        val result = fetchResolvedReferences(fetcher, refs)

        assertEquals(
            mapOf(
                "com.example.A" to "schema-a",
                "com.example.B" to "schema-b"
            ),
            result
        )
        verify(fetcher, times(1)).getSchemaVersionInfo("A", 1L)
        verify(fetcher, times(1)).getSchemaVersionInfo("B", 1L)
    }

    @Test
    fun `should swallow per-ref exceptions and continue with remaining refs`() = runBlocking {
        val fetcher = mock<DataPlaneFetcher> {
            onBlocking { getSchemaVersionInfo("Bad", 1L) } doThrow RuntimeException("boom")
            onBlocking { getSchemaVersionInfo("Good", 1L) } doReturn schemaResponse(
                subject = "Good",
                version = 1,
                schema = "good-schema"
            )
        }
        val refs = listOf(
            SchemaReference("com.example.Bad", "Bad", 1),
            SchemaReference("com.example.Good", "Good", 1)
        )

        val result = fetchResolvedReferences(fetcher, refs)

        assertEquals(mapOf("com.example.Good" to "good-schema"), result)
    }

    @Test
    fun `should propagate cancellation`() {
        val fetcher = mock<DataPlaneFetcher> {
            onBlocking { getSchemaVersionInfo(any(), any()) } doThrow CancellationException("cancelled")
        }
        val refs = listOf(SchemaReference("com.example.X", "X", 1))

        assertThrows<CancellationException> {
            runBlocking { fetchResolvedReferences(fetcher, refs) }
        }
    }

    private fun schemaResponse(
        subject: String,
        version: Int,
        schema: String,
        references: List<SchemaReferenceResponse>? = null
    ) = SchemaVersionResponse(
        subject = subject,
        version = version,
        id = 1,
        schema = schema,
        schemaType = "AVRO",
        references = references
    )
}
