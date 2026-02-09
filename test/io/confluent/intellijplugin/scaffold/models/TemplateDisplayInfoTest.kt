package io.confluent.intellijplugin.scaffold.models

import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

class TemplateDisplayInfoTest {

    @Nested
    @DisplayName("DisplayName")
    inner class DisplayNameTests {

        @Test
        fun `uses displayName when available`() {
            val spec = mapOf(
                "name" to "my-template",
                "display_name" to "My Template"
            )
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("My Template", info.displayName)
        }

        @Test
        fun `falls back to name when displayName is null`() {
            val spec = mapOf(
                "name" to "my-template"
            )
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("my-template", info.displayName)
        }

        @Test
        fun `returns Unknown Template when both null`() {
            val spec = emptyMap<String, Any?>()
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("Unknown Template", info.displayName)
        }

        @Test
        fun `returns Unknown Template when spec is not a Map`() {
            val template = ScaffoldV1TemplateListDataInner(
                metadata = createMetadata(),
                spec = "not a map"
            )

            val info = TemplateDisplayInfo.from(template)

            assertEquals("Unknown Template", info.displayName)
        }
    }

    @Nested
    @DisplayName("Metadata")
    inner class MetadataTests {

        @Test
        fun `extracts all metadata fields correctly`() {
            val spec = mapOf(
                "name" to "test-template",
                "display_name" to "Test Template",
                "description" to "A test template",
                "version" to "1.0.0",
                "language" to "Kotlin",
                "tags" to listOf("kafka", "streams", "test")
            )
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("Test Template", info.displayName)
            assertEquals("A test template", info.description)
            assertEquals("Kotlin", info.language)
            assertEquals("1.0.0", info.version)
            assertEquals("kafka, streams, test", info.tags)
        }

        @Test
        fun `handles null metadata gracefully`() {
            val spec = mapOf(
                "name" to "minimal-template"
            )
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("minimal-template", info.displayName)
            assertEquals("", info.description)
            assertEquals("", info.language)
            assertEquals("", info.version)
            assertEquals("", info.tags)
        }

        @Test
        fun `formats tags as comma-separated string`() {
            val spec = mapOf(
                "name" to "template",
                "tags" to listOf("tag1", "tag2", "tag3")
            )
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("tag1, tag2, tag3", info.tags)
        }

        @Test
        fun `handles empty tags list`() {
            val spec = mapOf(
                "name" to "template",
                "tags" to emptyList<String>()
            )
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("", info.tags)
        }

        @Test
        fun `handles single tag`() {
            val spec = mapOf(
                "name" to "template",
                "tags" to listOf("kafka")
            )
            val template = createTemplate(spec)

            val info = TemplateDisplayInfo.from(template)

            assertEquals("kafka", info.tags)
        }
    }

    // Helper functions

    private fun createTemplate(spec: Map<String, Any?>): ScaffoldV1TemplateListDataInner {
        return ScaffoldV1TemplateListDataInner(
            metadata = createMetadata(),
            spec = spec
        )
    }

    private fun createMetadata(): ScaffoldV1TemplateMetadata {
        return ScaffoldV1TemplateMetadata(
            self = "https://api.confluent.cloud/scaffold/v1/templates/test",
            resourceName = "crn://confluent.cloud/template=test"
        )
    }
}
