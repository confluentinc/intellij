package io.confluent.intellijplugin.scaffold.util

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner

object IdeLanguageMapper {

    // Maps Language.displayName to template language values where they differ
    private val LANGUAGE_ALIAS_MAP: Map<String, List<String>> = mapOf(
        "C/C++" to listOf("C/C++"),
        "ObjectiveC" to listOf("C/C++"),
        "C#" to listOf("C#", ".NET"),
    )

    fun getPreferredLanguages(
        registeredLanguageProvider: () -> List<String> = {
            Language.getRegisteredLanguages().map { it.displayName }
        }
    ): List<String> {
        val registeredNames = registeredLanguageProvider()
        val languages = registeredNames.flatMap { displayName ->
            LANGUAGE_ALIAS_MAP[displayName] ?: listOf(displayName)
        }.distinct()
        thisLogger().debug("Registered IDE languages: $registeredNames, preferred languages: $languages")
        return languages
    }

    fun sortByPreferredLanguage(
        templates: List<ScaffoldV1TemplateListDataInner>,
        preferredLanguages: List<String> = getPreferredLanguages()
    ): List<ScaffoldV1TemplateListDataInner> {
        if (preferredLanguages.isEmpty()) return templates

        val preferredLower = preferredLanguages.map { it.lowercase() }.toSet()

        return templates.sortedWith(
            compareByDescending { template ->
                template.spec.language?.lowercase() in preferredLower
            }
        ).also { sorted ->
            thisLogger().debug(
                "Sorted templates: ${sorted.map { "${it.spec.displayName ?: it.spec.name} (${it.spec.language})" }}"
            )
        }
    }
}
