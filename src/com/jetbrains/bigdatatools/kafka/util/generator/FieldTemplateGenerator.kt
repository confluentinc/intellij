package com.jetbrains.bigdatatools.kafka.util.generator

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.applyIf

internal object FieldTemplateGenerator {

  enum class FieldType(
    val type: String,
    val description: String,
    val parameters: String? = null,
    // Numbers should not be wrapped with double quote in JSON as string
    val wrapQuotes: Boolean = true,
    val generator: (String?) -> String,
  ) {
    RANDOM_INT("random.integer", "Int", "(from: Int, to: Int)", wrapQuotes = false, { params ->
      val (from, to) = params?.split(",")?.map { it.trim().toInt() } ?: listOf(-1000, 1000)
      PrimitivesGenerator.generateInt(from, to).toString()
    }),
    RANDOM_UINT("random.uint", "UInt", "(from: UInt, to: UInt)", wrapQuotes = false, { params ->
      val (from, to) = params?.split(",")?.map { it.trim().toUInt() } ?: listOf(100u, 1000u)
      PrimitivesGenerator.generateUint(from, to).toString()
    }),
    RANDOM_FLOAT("random.float", "Float", "(from: Float, to: Float)", wrapQuotes = false, { params ->
      val (from, to) = params?.split(",")?.map { it.trim().toFloat() } ?: listOf(-1000f, 1000f)
      PrimitivesGenerator.generateFloat(from, to).toString()
    }),

    RANDOM_ALPHA("random.alphabetic", "String of letters", "(length: Int)", generator = { params ->
      val length = params?.toIntOrNull() ?: 20
      PrimitivesGenerator.generateAlphabetic(length)
    }),
    RANDOM_ALPHANUMERIC("random.alphanumeric", "String of letters, digits and `_`", "(length: Int)", generator = { params ->
      val length = params?.toIntOrNull() ?: 20
      PrimitivesGenerator.createAlphaNum(length)
    }),
    RANDOM_HEX("random.hexadecimal", "Hexadecimal string", "(length: Int)", generator = { params ->
      val length = params?.toIntOrNull() ?: 20
      PrimitivesGenerator.createHex(length)
    }),

    RANDOM_UUID("random.uuid", "UUID-v4", generator = { _ -> PrimitivesGenerator.generateUUID().toString() }),
    RANDOM_EMAIL("random.email", "Email", generator = { _ -> PrimitivesGenerator.generateEmail() }),
    TIMESTAMP("timestamp", "Unix timestamp", generator = { _ -> PrimitivesGenerator.createTimestamp() }),
    ISO_TIMESTAMP("isoTimestamp", "ISO-8601 timestamp", generator = { _ -> PrimitivesGenerator.createIsoTimestamp() });
  }

  fun processTemplate(text: String): String {
    var resultText = text

    FieldType.entries.forEach { template ->
      val regex = """"\$\{${template.type}\((.*?)\)}"""".toRegex()
      val matches = regex.findAll(resultText)

      matches.forEach { match ->
        val parameters = match.groupValues[1]
        val replacement = template.generator(parameters)
        resultText = resultText.replace(match.value, replacement.applyIf(template.wrapQuotes) { StringUtil.wrapWithDoubleQuote(this) })
      }
    }

    return resultText
  }

  fun hasTemplatesWithRemoveQuotas(resText: String): Boolean = FieldType.entries
    .filter { it.wrapQuotes }
    .any { it.type in resText }
}