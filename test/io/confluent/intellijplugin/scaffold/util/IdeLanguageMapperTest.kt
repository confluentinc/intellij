package io.confluent.intellijplugin.scaffold.util

import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IdeLanguageMapperTest {

    private fun createTemplate(
        name: String = "test-template",
        language: String? = "Java"
    ): ScaffoldV1TemplateListDataInner {
        return ScaffoldV1TemplateListDataInner(
            metadata = ScaffoldV1TemplateMetadata(self = null),
            spec = Scaffoldv1TemplateSpec(
                name = name,
                displayName = name,
                language = language
            )
        )
    }

    @Nested
    @DisplayName("getPreferredLanguages")
    inner class GetPreferredLanguages {

        @Test
        fun `returns Java and Kotlin for IntelliJ IDEA Community`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "IC" }
            assertEquals(listOf("Java", "Kotlin"), languages)
        }

        @Test
        fun `returns Java and Kotlin for IntelliJ IDEA Ultimate`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "IU" }
            assertEquals(listOf("Java", "Kotlin"), languages)
        }

        @Test
        fun `returns Python for PyCharm`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "PC" }
            assertEquals(listOf("Python"), languages)
        }

        @Test
        fun `returns Go for GoLand`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "GO" }
            assertEquals(listOf("Go"), languages)
        }

        @Test
        fun `returns JavaScript and TypeScript for WebStorm`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "WS" }
            assertEquals(listOf("JavaScript", "TypeScript"), languages)
        }

        @Test
        fun `returns Python for PyCharm Professional`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "PY" }
            assertEquals(listOf("Python"), languages)
        }

        @Test
        fun `returns C and C++ for CLion`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "CL" }
            assertEquals(listOf("C/C++"), languages)
        }

        @Test
        fun `returns C# and dotNET for Rider`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "RD" }
            assertEquals(listOf("C#", ".NET"), languages)
        }

        @Test
        fun `returns Ruby for RubyMine`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "RM" }
            assertEquals(listOf("Ruby"), languages)
        }

        @Test
        fun `returns PHP for PhpStorm`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "PS" }
            assertEquals(listOf("PHP"), languages)
        }

        @Test
        fun `returns empty list for unknown IDE`() {
            val languages = IdeLanguageMapper.getPreferredLanguages { "XX" }
            assertTrue(languages.isEmpty())
        }
    }

    @Nested
    @DisplayName("sortByPreferredLanguage")
    inner class SortByPreferredLanguage {

        @Test
        fun `preferred language templates sort first`() {
            val templates = listOf(
                createTemplate(name = "go-template", language = "Go"),
                createTemplate(name = "java-template", language = "Java"),
                createTemplate(name = "python-template", language = "Python")
            )

            val sorted = IdeLanguageMapper.sortByPreferredLanguage(templates, listOf("Java", "Kotlin"))

            assertEquals("java-template", sorted[0].spec.name)
        }

        @Test
        fun `preserves original order for non-preferred templates`() {
            val templates = listOf(
                createTemplate(name = "go-template", language = "Go"),
                createTemplate(name = "python-template", language = "Python"),
                createTemplate(name = "java-template", language = "Java")
            )

            val sorted = IdeLanguageMapper.sortByPreferredLanguage(templates, listOf("Java"))

            assertEquals("java-template", sorted[0].spec.name)
            assertEquals("go-template", sorted[1].spec.name)
            assertEquals("python-template", sorted[2].spec.name)
        }

        @Test
        fun `case-insensitive matching`() {
            val templates = listOf(
                createTemplate(name = "go-template", language = "Go"),
                createTemplate(name = "java-template", language = "java")
            )

            val sorted = IdeLanguageMapper.sortByPreferredLanguage(templates, listOf("Java"))

            assertEquals("java-template", sorted[0].spec.name)
        }

        @Test
        fun `handles null language gracefully`() {
            val templates = listOf(
                createTemplate(name = "null-lang", language = null),
                createTemplate(name = "java-template", language = "Java")
            )

            val sorted = IdeLanguageMapper.sortByPreferredLanguage(templates, listOf("Java"))

            assertEquals("java-template", sorted[0].spec.name)
            assertEquals("null-lang", sorted[1].spec.name)
        }

        @Test
        fun `empty preferred list returns original order`() {
            val templates = listOf(
                createTemplate(name = "go-template", language = "Go"),
                createTemplate(name = "java-template", language = "Java")
            )

            val sorted = IdeLanguageMapper.sortByPreferredLanguage(templates, emptyList())

            assertEquals("go-template", sorted[0].spec.name)
            assertEquals("java-template", sorted[1].spec.name)
        }

        @Test
        fun `multiple preferred languages all sort first`() {
            val templates = listOf(
                createTemplate(name = "python-template", language = "Python"),
                createTemplate(name = "kotlin-template", language = "Kotlin"),
                createTemplate(name = "go-template", language = "Go"),
                createTemplate(name = "java-template", language = "Java")
            )

            val sorted = IdeLanguageMapper.sortByPreferredLanguage(templates, listOf("Java", "Kotlin"))

            val preferredNames = sorted.take(2).map { it.spec.name }.toSet()
            assertEquals(setOf("kotlin-template", "java-template"), preferredNames)

            val nonPreferredNames = sorted.drop(2).map { it.spec.name }.toSet()
            assertEquals(setOf("python-template", "go-template"), nonPreferredNames)
        }
    }
}
