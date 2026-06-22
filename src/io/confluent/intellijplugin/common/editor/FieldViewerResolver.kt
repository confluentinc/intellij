package io.confluent.intellijplugin.common.editor

import io.confluent.intellijplugin.common.models.KafkaFieldType

/**
 * Pure (UI-free) resolution of how a Key/Value field should be displayed.
 *
 * The selected [FieldViewerType] in the viewer combo box can be [FieldViewerType.AUTO], in which case the
 * effective viewer type is derived from the consumer field type and the field text. All decisions that depend
 * only on these inputs live here so they can be unit tested without the Swing layer.
 */
object FieldViewerResolver {

    /**
     * Resolves the effective viewer type. When [selected] is [FieldViewerType.AUTO] the type is auto-detected
     * from [fieldType] and [text]; otherwise the explicit selection is honored.
     */
    fun resolve(selected: FieldViewerType, fieldType: KafkaFieldType, text: String): FieldViewerType =
        if (selected == FieldViewerType.AUTO) detectAutoType(fieldType, text) else selected

    /**
     * Auto-detects the viewer type from the consumer field type. For [KafkaFieldType.STRING] the text is
     * inspected to decide between JSON and plain text.
     */
    fun detectAutoType(fieldType: KafkaFieldType, text: String): FieldViewerType = when (fieldType) {
        KafkaFieldType.STRING -> if (KafkaEditorUtils.isJsonString(text)) FieldViewerType.JSON else FieldViewerType.TEXT
        KafkaFieldType.JSON -> FieldViewerType.JSON
        KafkaFieldType.LONG -> FieldViewerType.TEXT
        KafkaFieldType.INTEGER -> FieldViewerType.TEXT
        KafkaFieldType.DOUBLE -> FieldViewerType.TEXT
        KafkaFieldType.FLOAT -> FieldViewerType.TEXT
        KafkaFieldType.BASE64 -> FieldViewerType.DECODED_BASE64
        KafkaFieldType.NULL -> FieldViewerType.TEXT
        KafkaFieldType.SCHEMA_REGISTRY -> FieldViewerType.JSON
        KafkaFieldType.PROTOBUF_CUSTOM -> FieldViewerType.JSON
        KafkaFieldType.AVRO_CUSTOM -> FieldViewerType.JSON
    }

    /** Whether the "load file" link should be shown, i.e. the value is being decoded from Base64. */
    fun isLoadFileLinkVisible(resolved: FieldViewerType): Boolean = resolved == FieldViewerType.DECODED_BASE64

    /** Whether the field content should be rendered/formatted as JSON. */
    fun isJsonView(resolved: FieldViewerType): Boolean = resolved == FieldViewerType.JSON
}
