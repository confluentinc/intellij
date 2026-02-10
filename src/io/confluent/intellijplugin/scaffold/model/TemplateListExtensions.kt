package io.confluent.intellijplugin.scaffold.model

/**
 * Wrapper for ScaffoldV1TemplateListDataInner with properly typed spec field.
 * The auto-generated model types 'spec' as kotlin.Any, but we know it's Scaffoldv1TemplateSpec.
 */
data class TypedTemplateListItem(
    val metadata: ScaffoldV1TemplateMetadata,
    val spec: Scaffoldv1TemplateSpec,
    val apiVersion: ScaffoldV1TemplateListDataInner.ApiVersion? = null,
    val kind: ScaffoldV1TemplateListDataInner.Kind? = null
)

/**
 * Converts the auto-generated ScaffoldV1TemplateListDataInner to a typed wrapper.
 * The 'spec' field is deserialized from Map to Scaffoldv1TemplateSpec.
 */
fun ScaffoldV1TemplateListDataInner.toTyped(): TypedTemplateListItem {
    // Convert spec (which is already deserialized by Moshi as a Map) to typed object
    @Suppress("UNCHECKED_CAST")
    val specMap = spec as? Map<String, Any?>
        ?: throw IllegalArgumentException("spec is not a Map, it's ${spec?.javaClass?.name}: $spec")

    // Manually construct Scaffoldv1TemplateSpec from the Map
    val typedSpec = Scaffoldv1TemplateSpec(
        name = specMap["name"] as? String,
        displayName = specMap["display_name"] as? String,
        description = specMap["description"] as? String,
        version = specMap["version"] as? String,
        language = specMap["language"] as? String,
        tags = (specMap["tags"] as? List<*>)?.mapNotNull { it as? String },
        options = null, // Not used in our current tests
        templateCollection = null // Not used in our current tests
    )

    return TypedTemplateListItem(
        metadata = metadata,
        spec = typedSpec,
        apiVersion = apiVersion,
        kind = kind
    )
}

/**
 * Converts all items in a template list to typed wrappers.
 */
fun Scaffoldv1TemplateList.toTyped(): List<TypedTemplateListItem> {
    return data.map { it.toTyped() }
}
