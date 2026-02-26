package io.confluent.intellijplugin.scaffold.util

import com.intellij.openapi.application.ApplicationInfo
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner

object TemplateIdeFilter {

    private val IDE_LANGUAGE_PREFERENCES: Map<String, List<String>> = mapOf(
        "IntelliJ IDEA" to listOf("java", "kotlin", "scala", "groovy"),
        "PyCharm" to listOf("python"),
        "WebStorm" to listOf("javascript", "typescript"),
        "GoLand" to listOf("go"),
        "Rider" to listOf("c#", "csharp", ".net"),
        "CLion" to listOf("c", "c++", "cpp", "rust"),
        "RubyMine" to listOf("ruby"),
        "PhpStorm" to listOf("php"),
    )

    fun sortByIdeAffinity(
        templates: List<ScaffoldV1TemplateListDataInner>,
        applicationInfo: ApplicationInfo = ApplicationInfo.getInstance()
    ): List<ScaffoldV1TemplateListDataInner> {
        val ideName = applicationInfo.fullApplicationName.split(" ").take(2).joinToString(" ")
        val preferredLanguages = findPreferredLanguages(ideName)

        if (preferredLanguages.isEmpty()) return templates

        return templates.sortedWith(
            compareByDescending<ScaffoldV1TemplateListDataInner> { template ->
                val language = template.spec.language?.lowercase() ?: ""
                preferredLanguages.any { preferred -> language.contains(preferred) }
            }.thenBy { it.spec.displayName ?: it.spec.name ?: "" }
        )
    }

    internal fun findPreferredLanguages(ideName: String): List<String> {
        for ((ide, languages) in IDE_LANGUAGE_PREFERENCES) {
            if (ideName.contains(ide, ignoreCase = true)) {
                return languages
            }
        }
        return emptyList()
    }
}
