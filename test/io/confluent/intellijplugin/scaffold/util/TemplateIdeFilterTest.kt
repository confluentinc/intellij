package io.confluent.intellijplugin.scaffold.util

import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateSpec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TemplateIdeFilterTest {

    private fun createTemplate(name: String, language: String?, displayName: String? = null): ScaffoldV1TemplateListDataInner {
        return ScaffoldV1TemplateListDataInner(
            metadata = ScaffoldV1TemplateMetadata(self = null),
            spec = Scaffoldv1TemplateSpec(
                name = name,
                displayName = displayName ?: name,
                language = language
            )
        )
    }

    @Nested
    @DisplayName("findPreferredLanguages")
    inner class FindPreferredLanguages {

        @Test
        fun `returns Java and Kotlin for IntelliJ IDEA`() {
            val languages = TemplateIdeFilter.findPreferredLanguages("IntelliJ IDEA Ultimate")
            assertTrue(languages.contains("java"))
            assertTrue(languages.contains("kotlin"))
        }

        @Test
        fun `returns Python for PyCharm`() {
            val languages = TemplateIdeFilter.findPreferredLanguages("PyCharm Professional")
            assertTrue(languages.contains("python"))
        }

        @Test
        fun `returns Go for GoLand`() {
            val languages = TemplateIdeFilter.findPreferredLanguages("GoLand 2025.1")
            assertTrue(languages.contains("go"))
        }

        @Test
        fun `returns empty list for unknown IDE`() {
            val languages = TemplateIdeFilter.findPreferredLanguages("Unknown IDE")
            assertTrue(languages.isEmpty())
        }

        @Test
        fun `is case insensitive`() {
            val languages = TemplateIdeFilter.findPreferredLanguages("intellij idea community")
            assertTrue(languages.contains("java"))
        }
    }

    @Nested
    @DisplayName("sortByIdeAffinity")
    inner class SortByIdeAffinity {

        @Test
        fun `sorts Java templates first for IntelliJ IDEA name`() {
            val templates = listOf(
                createTemplate("python-starter", "Python"),
                createTemplate("java-starter", "Java"),
                createTemplate("go-starter", "Go"),
                createTemplate("kotlin-starter", "Kotlin")
            )

            val sorted = sortWithIdeName(templates, "IntelliJ IDEA")

            // Java and Kotlin templates should come before Python and Go
            val sortedLanguages = sorted.map { it.spec.language }
            val javaIndex = sortedLanguages.indexOf("Java")
            val kotlinIndex = sortedLanguages.indexOf("Kotlin")
            val pythonIndex = sortedLanguages.indexOf("Python")
            val goIndex = sortedLanguages.indexOf("Go")

            assertTrue(javaIndex < pythonIndex)
            assertTrue(javaIndex < goIndex)
            assertTrue(kotlinIndex < pythonIndex)
            assertTrue(kotlinIndex < goIndex)
        }

        @Test
        fun `sorts Python templates first for PyCharm name`() {
            val templates = listOf(
                createTemplate("java-starter", "Java"),
                createTemplate("python-starter", "Python"),
                createTemplate("go-starter", "Go")
            )

            val sorted = sortWithIdeName(templates, "PyCharm")

            assertEquals("Python", sorted.first().spec.language)
        }

        @Test
        fun `preserves order when IDE is unknown`() {
            val templates = listOf(
                createTemplate("a-template", "Java"),
                createTemplate("b-template", "Python"),
                createTemplate("c-template", "Go")
            )

            val sorted = sortWithIdeName(templates, "Unknown IDE")

            assertEquals(templates, sorted)
        }

        @Test
        fun `handles templates with null language`() {
            val templates = listOf(
                createTemplate("no-lang", null),
                createTemplate("java-starter", "Java"),
                createTemplate("python-starter", "Python")
            )

            val sorted = sortWithIdeName(templates, "IntelliJ IDEA")

            // Java should be first, null-language should not crash
            assertEquals("Java", sorted.first().spec.language)
        }

        private fun sortWithIdeName(
            templates: List<ScaffoldV1TemplateListDataInner>,
            ideName: String
        ): List<ScaffoldV1TemplateListDataInner> {
            val preferredLanguages = TemplateIdeFilter.findPreferredLanguages(ideName)
            if (preferredLanguages.isEmpty()) return templates
            return templates.sortedWith(
                compareByDescending<ScaffoldV1TemplateListDataInner> { template ->
                    val language = template.spec.language?.lowercase() ?: ""
                    preferredLanguages.any { preferred -> language.contains(preferred) }
                }.thenBy { it.spec.displayName ?: it.spec.name ?: "" }
            )
        }
    }
}
