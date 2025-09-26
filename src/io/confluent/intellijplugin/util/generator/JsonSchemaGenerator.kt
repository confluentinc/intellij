package io.confluent.intellijplugin.util.generator

import com.amazonaws.services.schemaregistry.serializers.json.JsonDataWithSchema
import com.google.gson.*
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.util.generator.GenerateRandomData.isValidSchema
import com.mifmif.common.regex.Generex
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import org.everit.json.schema.*
import org.everit.json.schema.internal.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class JsonSchemaGenerator(private val topLevelSchema: JsonSchema) {
  private val locations = mutableSetOf<String>()

  fun generate(): String {
    val schema = topLevelSchema.rawSchema()
    return GsonBuilder().setPrettyPrinting().create().toJson(generate(schema))
  }

  private fun generate(schema: Schema): JsonElement? = when (schema) {
    is ArraySchema -> generateArray(schema)
    is BooleanSchema -> JsonPrimitive(PrimitivesGenerator.generateBoolean())
    is CombinedSchema -> generateForCombinedSchema(schema)
    is ConditionalSchema -> generateForConditionalSchema()
    is ConstSchema -> JsonPrimitive(schema.permittedValue.toString())
    is EmptySchema -> JsonObject()
    is EnumSchema -> JsonPrimitive(schema.possibleValues.random().toString())
    is NotSchema -> JsonGenerator.generateJson()
    is NullSchema -> JsonNull.INSTANCE
    is NumberSchema -> JsonPrimitive(generateNumber(schema))
    is ObjectSchema -> generateObject(schema)
    is ReferenceSchema -> {
      val referenceSchema = schema.referredSchema
      if (locations.contains(referenceSchema.schemaLocation))
        null
      else {
        locations.add(referenceSchema.schemaLocation)
        generate(referenceSchema)
      }
    }
    is StringSchema -> JsonPrimitive(generateString(schema))
    else -> JsonNull.INSTANCE
  }

  private fun generateForCombinedSchema(schema: CombinedSchema): JsonElement? {
    val constOrEnumSchema = getConstOrEnumSchema(schema)
    if (constOrEnumSchema != null)
      return generate(constOrEnumSchema)

    val criterion = schema.criterion
    return if (criterion != null) {
      generateCriterionCombined(schema, criterion)
    }
    else {
      generate(schema.subschemas.random())
    }
  }

  private fun generateCriterionCombined(schema: CombinedSchema, criterion: CombinedSchema.ValidationCriterion): JsonElement? {
    return if (criterion.toString() == "allOf") {
      // Need to generate the data so that it matches each subSchema
      JsonObject()
    }
    else generate(schema.subschemas.random())
  }

  private fun getConstOrEnumSchema(schema: CombinedSchema): Schema? {
    val subSchemas = schema.subschemas ?: return null
    return subSchemas.firstOrNull { it is ConstSchema } ?: subSchemas.firstOrNull { it is EnumSchema }
  }

  private fun generateForConditionalSchema(): JsonElement {
    // Conditional schema comes after the all fields -- if (ifSchema matches) then (add thenSchema) else (add elseSchema)
    return JsonObject()
  }

  private fun generateObject(schema: ObjectSchema): JsonObject {
    val jsonObject = JsonObject()
    schema.propertySchemas?.forEach { generate(it.value)?.let { data -> jsonObject.add(it.key, data) } }
    return jsonObject
  }

  private fun generateArray(schema: ArraySchema): JsonArray {
    val jsonArray = JsonArray()

    val maxItems = schema.maxItems ?: 13
    val minItems = schema.minItems ?: if (maxItems > 5) 5 else PrimitivesGenerator.generateInt(0, maxItems)

    val itemCount = PrimitivesGenerator.generateInt(minItems, maxItems)
    val needsUniqueItems = schema.needsUniqueItems()

    for (i in 0 until itemCount) {
      var uniqueItem = generate(getItemSchema(schema))
      // if requires unique items, uniqueItem will be regenerated
      while (needsUniqueItems && jsonArray.contains(uniqueItem)) {
        uniqueItem = generate(getItemSchema(schema))
      }
      jsonArray.add(uniqueItem)
    }
    return jsonArray
  }

  private fun getItemSchema(schema: ArraySchema): Schema = if (schema.allItemSchema != null) {
    schema.allItemSchema
  }
  else {
    schema.itemSchemas.random()
  }

  private fun generateString(schema: StringSchema): String {
    val maxLength = schema.maxLength ?: 21
    val minLength = schema.minLength ?: if (maxLength > 8) 8 else PrimitivesGenerator.generateInt(0, maxLength)

    return when {
      schema.formatValidator != null && schema.formatValidator != FormatValidator.NONE -> generateFormattedString(schema.formatValidator)
      schema.pattern != null -> Generex(schema.pattern.toString()).random(minLength, maxLength)
      else -> PrimitivesGenerator.generateString(minLength, maxLength, requireLetters = schema.requireString())
    }
  }

  private fun generateFormattedString(format: FormatValidator): String = when (format) {
    is DateTimeFormatValidator -> generateDateTime()
    is EmailFormatValidator -> generateEmail()
    is HostnameFormatValidator -> generateHost()
    is URIFormatValidator -> generateRandomURI()
    is IPV4Validator -> generateIPV4()
    is IPV6Validator -> generateIPV6()
    else -> ""
  }

  private fun getAllowedChars(): List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

  private fun generateDateTime(): String {
    val dateTimeFormat = "yyyy-MM-dd HH:mm:ss"
    val dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormat)
    val randomYear = PrimitivesGenerator.generateInt(1900, 2100)
    val randomMonth = PrimitivesGenerator.generateInt(1, 13)
    val randomDay = PrimitivesGenerator.generateInt(1, 29)
    val randomHour = PrimitivesGenerator.generateInt(0, 24)
    val randomMinute = PrimitivesGenerator.generateInt(0, 60)
    val randomSecond = PrimitivesGenerator.generateInt(0, 60)
    val randomDateTime = LocalDateTime.of(randomYear, randomMonth, randomDay, randomHour, randomMinute, randomSecond)
    return randomDateTime.format(dateTimeFormatter)
  }

  private fun generateEmail(): String {
    val domains = listOf("example.com", "gmail.com", "yahoo.com", "hotmail.com")
    val usernameLength = PrimitivesGenerator.generateInt(5, 10)
    val username = (1..usernameLength).map { getAllowedChars().random() }.joinToString("")
    val domain = domains.random()
    return "$username@$domain"
  }

  private fun generateHost(): String {
    val domainNameLength = PrimitivesGenerator.generateInt(5, 10)
    val domainName = (1..domainNameLength).map { getAllowedChars().random() }.joinToString("")
    val topLevelDomains = listOf("com", "net", "org", "io")
    val topLevelDomain = topLevelDomains.random()
    return "$domainName.$topLevelDomain"
  }

  private fun generateRandomURI(): String {
    val schemes = listOf("http", "https", "ftp")
    val hosts = listOf("example.com", "google.com", "yahoo.com", "hotmail.com")
    val paths = listOf("/page1", "/page2", "/page3", "/page4", "/page5")
    val queries = listOf("param1=value1&param2=value2", "query=example", "")

    val scheme = schemes.random()
    val host = hosts.random()
    val path = paths.random()
    val query = queries.random()

    return "$scheme://$host$path?$query"
  }

  private fun generateIPV4(): String {
    val octets = List(4) { PrimitivesGenerator.generateInt(0, 256) }
    return octets.joinToString(".")
  }

  private fun generateIPV6(): String {
    val blocks = List(8) { (1..4).map { PrimitivesGenerator.generateInt(0, 65536) }.joinToString(":") }
    return blocks.joinToString(":")
  }

  private fun generateNumber(schema: NumberSchema): Number = if (schema.requiresInteger()) {
    val min = schema.minimum?.toInt()
    val max = schema.maximum?.toInt()

    val minWithExclusive = if (schema.isExclusiveMinimum) min?.plus(1) else min
    val maxWithExclusive = if (schema.isExclusiveMaximum) max?.minus(1) else max

    val integer = PrimitivesGenerator.generateInt(minWithExclusive, maxWithExclusive)
    val divisor = schema.multipleOf?.toInt()
    if (divisor != null) integer - integer.mod(divisor) else integer
  }
  else {
    val min = schema.minimum?.toDouble()
    val max = schema.maximum?.toDouble()

    val minWithExclusive = if (schema.isExclusiveMinimum) min?.plus(0.1) else min
    val maxWithExclusive = if (schema.isExclusiveMaximum) max?.minus(0.1) else max

    val number = PrimitivesGenerator.generateDouble(minWithExclusive, maxWithExclusive)
    val divisor = schema.multipleOf?.toDouble()
    if (divisor != null) number - number.mod(divisor) else number
  }

  companion object {
    fun generateJsonMessage(project: Project?, schema: ParsedSchema?, isConfluent: Boolean): String {
      if (schema?.isValidSchema(project) == false) {
        return ""
      }

      val jsonSchema = schema as? JsonSchema
      if (jsonSchema == null) {
        error("Schema could not be null and the type of it should be JSON")
      }

      val numberOfAttempts = 10
      var result = ""
      for (i in 0 until numberOfAttempts) {
        result = JsonSchemaGenerator(jsonSchema).generate()
        if (result.isValidData(schema, isConfluent))
          break
      }
      return result
    }

    private fun String.isValidData(schema: ParsedSchema, isConfluentSchema: Boolean): Boolean = try {
      if (isConfluentSchema) {
        JsonSchemaUtils.toObject(this, schema as JsonSchema)
      }
      else {
        JsonDataWithSchema.builder(schema.canonicalString(), this).build()
      }
      true
    }
    catch (e: Exception) {
      false
    }
  }
}