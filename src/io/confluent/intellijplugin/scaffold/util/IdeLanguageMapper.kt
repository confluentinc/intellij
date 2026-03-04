package io.confluent.intellijplugin.scaffold.util

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner

object IdeLanguageMapper {

    private val IDE_LANGUAGE_MAP: Map<String, List<String>> = mapOf(
        "IC" to listOf("Java", "Kotlin"),
        "IU" to listOf("Java", "Kotlin"),
        "PC" to listOf("Python"),
        "PY" to listOf("Python"),
        "GO" to listOf("Go"),
        "WS" to listOf("JavaScript"),
        "CL" to listOf("C/C++", "C#"),
        "RD" to listOf("C#", ".NET"),
        "RM" to listOf("Ruby"),
        "PS" to listOf("PHP"),
    )

    fun getPreferredLanguages(
        productCodeProvider: () -> String = { ApplicationInfo.getInstance().build.productCode }
    ): List<String> {
        val productCode = productCodeProvider()
        val languages = IDE_LANGUAGE_MAP[productCode] ?: emptyList()
        thisLogger().debug("IDE product code: $productCode, preferred languages: $languages")
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
