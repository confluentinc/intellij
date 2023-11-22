package com.jetbrains.bigdatatools.kafka.util.generator

import org.jetbrains.annotations.Nls

object FieldTemplateGenerator {
  enum class FieldType(val id: String, @Nls val desc: String, val removeQuotas: Boolean = false, val generator: () -> String) {
    RANDOM_ALPHA("random.alphabetic", "String of letters", generator = { PrimitivesGenerator.generateAlphabetic() }),
    RANDOM_FLOAT("random.float", "Float", removeQuotas = true, generator = { PrimitivesGenerator.generateFloat().toString() }),
    RANDOM_INT("random.integer", "Integer", removeQuotas = true, generator = { PrimitivesGenerator.generateInt().toString() }),
    RANDOM_UINT("random.uint", "UInteger", removeQuotas = true, generator = { PrimitivesGenerator.generateUint().toString() }),
    RANDOM_UUID("random.uuid", "UUID-v4", generator = { PrimitivesGenerator.generateUUID().toString() }),
    RANDOM_ALPHANUM("random.alphanumeric", "String of letters, digits and `_`", generator = { PrimitivesGenerator.createAlphaNum() }),
    RANDOM_HEX("random.hexadecimal", "Hexadecimal string", generator = { PrimitivesGenerator.createHex() }),
    RANDOM_EMAIL("random.email", "Email", generator = { PrimitivesGenerator.generateEmail() }),
    TIMESTAMP("timestamp", "Unix timestamp", generator = { PrimitivesGenerator.createTimestamp() }),
    ISO_TIMESTAMP("isoTimestamp", "ISO-8601 timestamp", generator = { PrimitivesGenerator.createIsoTimestamp() });

    val textRepresentation = "\${$id}"
  }

  fun processTemplate(text: String): String {
    var resText = text
    while (FieldType.entries.any { it.textRepresentation in resText }) {
      for (template in FieldType.entries) {
        if (template.textRepresentation !in resText)
          continue
        if (template.removeQuotas && "\"${template.textRepresentation}\"" in resText)
          resText = resText.replaceFirst("\"${template.textRepresentation}\"", template.generator())
        else
          resText = resText.replaceFirst(template.textRepresentation, template.generator())
      }
    }
    return resText
  }

  fun hasTemplatesWithRemoveQuotas(resText: String) = FieldType.entries
    .filter { it.removeQuotas }
    .any { it.textRepresentation in resText }
}