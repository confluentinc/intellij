package io.confluent.intellijplugin.scaffold.model

import com.squareup.moshi.Moshi

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
fun ScaffoldV1TemplateListDataInner.toTyped(moshi: Moshi): TypedTemplateListItem {
    val specAdapter = moshi.adapter(Scaffoldv1TemplateSpec::class.java)
    val typedSpec = (spec as? Map<*, *>)?.let { specAdapter.fromJsonValue(it) }
        ?: throw IllegalStateException("Failed to deserialize spec field to Scaffoldv1TemplateSpec")

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
fun Scaffoldv1TemplateList.toTyped(moshi: Moshi): List<TypedTemplateListItem> {
    return data.map { it.toTyped(moshi) }
}
