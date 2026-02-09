package io.confluent.intellijplugin.scaffold.models

import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner

/**
 * Display-friendly representation of a scaffold template.
 * Extracts metadata from the template spec for UI display.
 */
data class TemplateDisplayInfo(
    val displayName: String,
    val description: String,
    val language: String,
    val version: String,
    val tags: String
) {
    companion object {
        /**
         * Creates a TemplateDisplayInfo from a ScaffoldV1TemplateListDataInner.
         * Handles the fact that spec is typed as kotlin.Any and is deserialized as a Map.
         */
        fun from(template: ScaffoldV1TemplateListDataInner): TemplateDisplayInfo {
            val spec = template.spec as? Map<*, *>

            @Suppress("UNCHECKED_CAST")
            val specMap = spec as? Map<String, Any?>

            return TemplateDisplayInfo(
                displayName = extractDisplayName(specMap),
                description = extractString(specMap, "description"),
                language = extractString(specMap, "language"),
                version = extractString(specMap, "version"),
                tags = extractTags(specMap)
            )
        }

        private fun extractDisplayName(spec: Map<String, Any?>?): String {
            if (spec == null) return "Unknown Template"

            val displayName = spec["display_name"] as? String
            if (!displayName.isNullOrBlank()) return displayName

            val name = spec["name"] as? String
            if (!name.isNullOrBlank()) return name

            return "Unknown Template"
        }

        private fun extractString(spec: Map<String, Any?>?, key: String): String {
            if (spec == null) return ""
            val value = spec[key] as? String
            return value ?: ""
        }

        private fun extractTags(spec: Map<String, Any?>?): String {
            if (spec == null) return ""

            @Suppress("UNCHECKED_CAST")
            val tagsList = spec["tags"] as? List<String>
            return tagsList?.joinToString(", ") ?: ""
        }
    }
}
