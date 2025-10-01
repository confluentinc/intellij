package io.confluent.intellijplugin.util.generator

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.util.messageOrDefault
import io.confluent.intellijplugin.util.generator.GenerateRandomData.isValidSchema
import io.confluent.intellijplugin.util.generator.GenerateRandomData.logger
import com.mifmif.common.regex.Generex
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.avro.LogicalType
import org.apache.avro.LogicalTypes
import org.apache.avro.Schema
import org.apache.avro.generic.*
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.io.DatumWriter
import org.apache.avro.io.Encoder
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.get
import kotlin.math.pow

class AvroGenerator private constructor(private val topLevelSchema: Schema) {
  private val generexCache: MutableMap<Schema, Generex> = HashMap<Schema, Generex>()
  private val optionsCache: MutableMap<Schema, List<Any?>> = HashMap<Schema, List<Any?>>()
  private val iteratorCache: MutableMap<Schema, Iterator<Any?>> = IdentityHashMap()

  private val random = Random()
  private val generation = 0L

  /**
   * @return The schema that the generator produces values for.
   */
  fun schema(): Schema {
    return topLevelSchema
  }

  /**
   * Generate an object that matches the given schema and its specified properties.
   * @return An object whose type corresponds to the top-level schema as follows:
   * <table summary="Schema Type-to-Java class specifications">
   *   <tr>
   *     <th>Schema Type</th>
   *     <th>Java Class</th>
   *   </tr>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#ARRAY ARRAY}</td>
   *     <td>{@link Collection}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#BOOLEAN BOOLEAN}</td>
   *     <td>{@link Boolean}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#BYTES BYTES}</td>
   *     <td>{@link ByteBuffer}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#DOUBLE DOUBLE}</td>
   *     <td>{@link Double}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#ENUM ENUM}</td>
   *     <td>{@link GenericEnumSymbol}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#FIXED FIXED}</td>
   *     <td>{@link GenericFixed}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#FLOAT FLOAT}</td>
   *     <td>{@link Float}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#INT INT}</td>
   *     <td>{@link Integer}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#LONG LONG}</td>
   *     <td>{@link Long}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#MAP MAP}</td>
   *     <td>
   *       {@link Map}&lt;{@link String}, V&gt; where V is the corresponding Java class for the
   *       Avro map's values
   *     </td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#NULL NULL}</td>
   *     <td>{@link Object} (but will always be null)</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#RECORD RECORD}</td>
   *     <td>{@link GenericRecord}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#STRING STRING}</td>
   *     <td>{@link String}</td>
   *   <tr>
   *     <td>{@link org.apache.avro.Schema.Type#UNION UNION}</td>
   *     <td>
   *       The corresponding Java class for whichever schema is chosen to be generated out of the
   *       ones present in the given Avro union.
   *     </td>
   * </table>
   */
  fun generate(): Any? {
    return generateObject(topLevelSchema)
  }

  private fun generateObject(schema: Schema): Any? {
    val propertiesProp = getProperties(schema).orElse(emptyMap<Any, Any>())
    if (propertiesProp.containsKey(OPTIONS_PROP)) {
      return generateOption(schema, propertiesProp)
    }
    if (propertiesProp.containsKey(ITERATION_PROP)) {
      return generateIteration(schema, propertiesProp)
    }
    when (schema.type) {
      Schema.Type.ARRAY -> return generateArray(schema, propertiesProp)
      Schema.Type.BOOLEAN -> return generateBoolean(propertiesProp)
      Schema.Type.BYTES -> return generateBytes(schema, propertiesProp)
      Schema.Type.DOUBLE -> return generateDouble(propertiesProp)
      Schema.Type.ENUM -> return generateEnumSymbol(schema)
      Schema.Type.FIXED -> return generateFixed(schema)
      Schema.Type.FLOAT -> return generateFloat(propertiesProp)
      Schema.Type.INT -> return generateInt(propertiesProp)
      Schema.Type.LONG -> return generateLong(propertiesProp)
      Schema.Type.MAP -> return generateMap(schema, propertiesProp)
      Schema.Type.NULL -> return generateNull()
      Schema.Type.RECORD -> return generateRecord(schema)
      Schema.Type.STRING -> return generateString(schema, propertiesProp)
      Schema.Type.UNION -> return generateUnion(schema)
      else -> throw RuntimeException("Unrecognized schema type: " + schema.type)
    }
  }

  private fun getProperties(schema: Schema): Optional<Map<*, *>> =
    when (val propertiesProp = schema.getObjectProp(ARG_PROPERTIES_PROP)) {
      null -> Optional.empty()
      is Map<*, *> -> Optional.of(propertiesProp)
      else -> {
        throw RuntimeException(String.format(
          "%s property must be given as object, was %s instead",
          ARG_PROPERTIES_PROP,
          propertiesProp.javaClass.name
        ))
      }
    }

  private fun <L : LogicalType?> getLogicalType(schema: Schema, logicalTypeName: String, logicalTypeClass: Class<L>): L? {
    return Optional.ofNullable(schema.logicalType)
      .filter { logicalType: LogicalType -> Objects.equals(logicalTypeName, logicalType.name) }
      .map { obj: LogicalType? -> logicalTypeClass.cast(obj) }
      .orElse(null)
  }

  private fun getDecimalLogicalType(schema: Schema) = getLogicalType(schema, DECIMAL_LOGICAL_TYPE_NAME, LogicalTypes.Decimal::class.java)

  private fun enforceMutualExclusion(propertiesProp: Map<*, *>, includedProp: String, vararg excludedProps: String) {
    for (excludedProp: String in excludedProps) {
      if (propertiesProp.containsKey(excludedProp)) {
        throw RuntimeException(String.format("Cannot specify %s prop when %s prop is given", excludedProp, includedProp))
      }
    }
  }

  private fun wrapOption(schema: Schema, option: Any?): Any? {
    return if (schema.type == Schema.Type.BYTES && option is String) {
      ByteBuffer.wrap(option.toByteArray(Charset.defaultCharset()))
    }
    else if (schema.type == Schema.Type.FLOAT && option is Double) {
      option.toFloat()
    }
    else if (schema.type == Schema.Type.LONG && option is Int) {
      option.toLong()
    }
    else if (schema.type == Schema.Type.ARRAY && option is Collection<*>) {
      GenericData.Array(schema, option)
    }
    else if (schema.type == Schema.Type.ENUM && option is String) {
      EnumSymbol(schema, option)
    }
    else if (schema.type == Schema.Type.FIXED && option is String) {
      GenericData.Fixed(schema, option.toByteArray(Charset.defaultCharset()))
    }
    else if (schema.type == Schema.Type.RECORD && option is Map<*, *>) {
      val optionBuilder = GenericRecordBuilder(schema)
      for (field: Schema.Field in schema.fields) {
        if (option.containsKey(field.name())) {
          optionBuilder.set(field, option[field.name()])
        }
      }
      optionBuilder.build()
    }
    else if (schema.type == Schema.Type.MAP && option is Map<*, *>) {
      val newMap = option.entries.associate { (key, value) ->
        key to wrapOption(schema.valueType, value)
      }
      newMap
    }
    else
      option
  }

  private fun parseOptions(schema: Schema, propertiesProp: Map<*, *>): List<Any?> {
    enforceMutualExclusion(propertiesProp, OPTIONS_PROP, LENGTH_PROP, REGEX_PROP, ITERATION_PROP, RANGE_PROP)
    val optionsProp = propertiesProp[OPTIONS_PROP]
    if (optionsProp is Collection<*>) {
      if (optionsProp.isEmpty()) {
        throw RuntimeException(String.format("%s property cannot be empty", OPTIONS_PROP))
      }
      val options: MutableList<Any?> = ArrayList()
      for (option in optionsProp) {
        val tempOption = wrapOption(schema, option)
        if (!GenericData.get().validate(schema, tempOption)) {
          throw RuntimeException(String.format(
            "Invalid option for %s schema: type %s, value '%s'",
            schema.type.getName(),
            tempOption?.javaClass?.name,
            tempOption
          ))
        }
        options.add(tempOption)
      }
      return options
    }
    else {
      throw RuntimeException(String.format(
        "%s prop must be an array or an object, was %s instead",
        OPTIONS_PROP,
        optionsProp?.javaClass?.name
      ))
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> generateOption(schema: Schema, propertiesProp: Map<*, *>): T {
    if (!optionsCache.containsKey(schema)) {
      optionsCache[schema] = parseOptions(schema, propertiesProp)
    }
    val options = optionsCache[schema]!!
    return options[random.nextInt(options.size)] as T
  }

  private fun getBooleanIterator(iterationProps: Map<*, *>): Iterator<Any> {
    val startProp = iterationProps[ITERATION_PROP_START]
                    ?: throw RuntimeException(String.format("%s property must contain %s field", ITERATION_PROP, ITERATION_PROP_START))
    if (startProp !is Boolean) {
      throw RuntimeException(String.format(
        "%s field of %s property for a boolean schema must be a boolean, was %s instead",
        ITERATION_PROP_START,
        ITERATION_PROP,
        startProp.javaClass.name
      ))
    }
    if (iterationProps.containsKey(ITERATION_PROP_RESTART)) {
      throw RuntimeException(String.format(
        "%s property cannot contain %s field for a boolean schema",
        ITERATION_PROP,
        ITERATION_PROP_RESTART
      ))
    }
    if (iterationProps.containsKey(ITERATION_PROP_STEP)) {
      throw RuntimeException(String.format(
        "%s property cannot contain %s field for a boolean schema",
        ITERATION_PROP,
        ITERATION_PROP_STEP
      ))
    }

    // If an odd number of records have been generated previously, then the boolean will have
    // changed state effectively once, and so the start state should be inverted.
    return BooleanIterator((generation % 2 == 1L) xor startProp)
  }

  @Suppress("DuplicatedCode")
  private fun getIntegralIterator(
    iterationStartField: Long?,
    iterationRestartField: Long?,
    iterationStepField: Long?,
    iterationInitialField: Long?,
    type: IntegralIterator.Type): Iterator<Any> {
    if (iterationStartField == null) {
      throw RuntimeException(String.format("%s property must contain %s field", ITERATION_PROP, ITERATION_PROP_START))
    }
    val iterationStart: Long = iterationStartField
    val iterationRestart: Long
    val iterationStep: Long
    val restartHighDefault: Long
    val restartLowDefault: Long
    when (type) {
      IntegralIterator.Type.INTEGER -> {
        restartHighDefault = Int.MAX_VALUE.toLong()
        restartLowDefault = Int.MIN_VALUE.toLong()
      }
      IntegralIterator.Type.LONG -> {
        restartHighDefault = Long.MAX_VALUE
        restartLowDefault = Long.MIN_VALUE
      }
    }
    if (iterationRestartField == null && iterationStepField == null) {
      iterationRestart = restartHighDefault
      iterationStep = 1
    }
    else if (iterationRestartField == null) {
      iterationStep = iterationStepField ?: 0L
      if (iterationStep > 0) {
        iterationRestart = restartHighDefault
      }
      else if (iterationStep < 0) {
        iterationRestart = -1 * restartLowDefault
      }
      else {
        throw RuntimeException(String.format("%s field of %s property cannot be zero", ITERATION_PROP_STEP, ITERATION_PROP))
      }
    }
    else if (iterationStepField == null) {
      iterationRestart = iterationRestartField
      if (iterationRestart > iterationStart) {
        iterationStep = 1
      }
      else if (iterationRestart < iterationStart) {
        iterationStep = -1
      }
      else {
        throw RuntimeException(String.format(
          "%s and %s fields of %s property cannot be equal",
          ITERATION_PROP_START,
          ITERATION_PROP_RESTART,
          ITERATION_PROP
        ))
      }
    }
    else {
      iterationRestart = iterationRestartField
      iterationStep = iterationStepField
      if (iterationStep == 0L) {
        throw RuntimeException(String.format(
          "%s field of %s property cannot be zero",
          ITERATION_PROP_STEP,
          ITERATION_PROP
        ))
      }
      if (iterationStart == iterationRestart) {
        throw RuntimeException(String.format(
          "%s and %s fields of %s property cannot be equal",
          ITERATION_PROP_START,
          ITERATION_PROP_RESTART,
          ITERATION_PROP
        ))
      }
      if (iterationRestart > iterationStart && iterationStep < 0) {
        throw RuntimeException(String.format(
          "%s field of %s property must be positive when %s field is greater than %s field",
          ITERATION_PROP_STEP,
          ITERATION_PROP,
          ITERATION_PROP_RESTART,
          ITERATION_PROP_START
        ))
      }
      if (iterationRestart < iterationStart && iterationStep > 0) {
        throw RuntimeException(String.format(
          "%s field of %s property must be negative when %s field is less than %s field",
          ITERATION_PROP_STEP,
          ITERATION_PROP,
          ITERATION_PROP_RESTART,
          ITERATION_PROP_START
        ))
      }
    }
    var iterationInitial = iterationStart
    if (iterationInitialField != null) {
      iterationInitial = iterationInitialField
    }
    return IntegralIterator(
      iterationStart,
      iterationRestart,
      iterationStep,
      iterationInitial,
      generation,
      type
    )
  }

  @Suppress("DuplicatedCode")
  private fun getDecimalIterator(
    iterationStartField: Double?,
    iterationRestartField: Double?,
    iterationStepField: Double?,
    iterationInitialField: Double?,
    type: DecimalIterator.Type): Iterator<Any> {
    if (iterationStartField == null) {
      throw RuntimeException(String.format("%s property must contain %s field", ITERATION_PROP, ITERATION_PROP_START))
    }
    val iterationRestart: Double
    val iterationStep: Double
    val restartHighDefault: Double
    val restartLowDefault: Double
    when (type) {
      DecimalIterator.Type.FLOAT -> {
        restartHighDefault = Float.MAX_VALUE.toDouble()
        restartLowDefault = (-1 * Float.MAX_VALUE).toDouble()
      }
      DecimalIterator.Type.DOUBLE -> {
        restartHighDefault = Double.MAX_VALUE
        restartLowDefault = -1 * Double.MAX_VALUE
      }
    }
    if (iterationRestartField == null && iterationStepField == null) {
      iterationRestart = restartHighDefault
      iterationStep = 1.0
    }
    else if (iterationRestartField == null) {
      iterationStep = iterationStepField ?: 0.0
      if (iterationStep > 0) {
        iterationRestart = restartHighDefault
      }
      else if (iterationStep < 0) {
        iterationRestart = -1 * restartLowDefault
      }
      else {
        throw RuntimeException(String.format("%s field of %s property cannot be zero", ITERATION_PROP_STEP, ITERATION_PROP))
      }
    }
    else if (iterationStepField == null) {
      iterationRestart = iterationRestartField
      if (iterationRestart > iterationStartField) {
        iterationStep = 1.0
      }
      else if (iterationRestart < iterationStartField) {
        iterationStep = -1.0
      }
      else {
        throw RuntimeException(String.format(
          "%s and %s fields of %s property cannot be equal",
          ITERATION_PROP_START,
          ITERATION_PROP_RESTART,
          ITERATION_PROP
        ))
      }
    }
    else {
      iterationRestart = iterationRestartField
      iterationStep = iterationStepField
      if (iterationStep == 0.0) {
        throw RuntimeException(String.format("%s field of %s property cannot be zero", ITERATION_PROP_STEP, ITERATION_PROP))
      }
      if (iterationStartField == iterationRestart) {
        throw RuntimeException(String.format(
          "%s and %s fields of %s property cannot be equal",
          ITERATION_PROP_START,
          ITERATION_PROP_RESTART,
          ITERATION_PROP
        ))
      }
      if (iterationRestart > iterationStartField && iterationStep < 0) {
        throw RuntimeException(String.format(
          "%s field of %s property must be positive when %s field is greater than %s field",
          ITERATION_PROP_STEP,
          ITERATION_PROP,
          ITERATION_PROP_RESTART,
          ITERATION_PROP_START
        ))
      }
      if (iterationRestart < iterationStartField && iterationStep > 0) {
        throw RuntimeException(String.format(
          "%s field of %s property must be negative when %s field is less than %s field",
          ITERATION_PROP_STEP,
          ITERATION_PROP,
          ITERATION_PROP_RESTART,
          ITERATION_PROP_START
        ))
      }
    }
    var iterationInitial = iterationStartField
    if (iterationInitialField != null) {
      iterationInitial = iterationInitialField
    }
    return DecimalIterator(
      iterationStartField,
      iterationRestart,
      iterationStep,
      iterationInitial,
      generation,
      type
    )
  }

  private fun parseIterations(schema: Schema, propertiesProp: Map<*, *>): Iterator<Any> {
    enforceMutualExclusion(propertiesProp, ITERATION_PROP, LENGTH_PROP, REGEX_PROP, OPTIONS_PROP, RANGE_PROP)
    val iterationProp = propertiesProp[ITERATION_PROP]
    if (iterationProp !is Map<*, *>) {
      throw RuntimeException(String.format(
        "%s prop must be an object, was %s instead",
        ITERATION_PROP,
        iterationProp?.javaClass?.name
      ))
    }
    when (schema.type) {
      Schema.Type.BOOLEAN -> return getBooleanIterator(iterationProp)
      Schema.Type.INT -> return getIntegerIterator(iterationProp)
      Schema.Type.LONG -> return getLongIterator(iterationProp)
      Schema.Type.FLOAT -> return getFloatIterator(iterationProp)
      Schema.Type.DOUBLE -> return getDoubleIterator(iterationProp)
      Schema.Type.STRING -> return createStringIterator(getIntegerIterator(iterationProp), propertiesProp)
      else -> throw UnsupportedOperationException(
        String.format("%s property can only be specified on numeric, boolean or string schemas, not %s schema",
                      ITERATION_PROP,
                      schema.type.toString())
      )
    }
  }

  private fun getDoubleIterator(iterationProps: Map<*, *>): Iterator<Any> {
    val iterationStartField = getDecimalNumberField(ITERATION_PROP, ITERATION_PROP_START, iterationProps)
    val iterationRestartField = getDecimalNumberField(ITERATION_PROP, ITERATION_PROP_RESTART, iterationProps)
    val iterationStepField = getDecimalNumberField(ITERATION_PROP, ITERATION_PROP_STEP, iterationProps)
    val iterationInitialField = getDecimalNumberField(ITERATION_PROP, ITERATION_PROP_INITIAL, iterationProps)
    return getDecimalIterator(
      iterationStartField,
      iterationRestartField,
      iterationStepField,
      iterationInitialField,
      DecimalIterator.Type.DOUBLE
    )
  }

  private fun getFloatIterator(iterationProps: Map<*, *>): Iterator<Any> {
    val iterationStartField = getFloatNumberField(ITERATION_PROP, ITERATION_PROP_START, iterationProps)
    val iterationRestartField = getFloatNumberField(ITERATION_PROP, ITERATION_PROP_RESTART, iterationProps)
    val iterationStepField = getFloatNumberField(ITERATION_PROP, ITERATION_PROP_STEP, iterationProps)
    val iterationInitialField = getFloatNumberField(ITERATION_PROP, ITERATION_PROP_INITIAL, iterationProps)
    return getDecimalIterator(
      iterationStartField?.toDouble(),
      iterationRestartField?.toDouble(),
      iterationStepField?.toDouble(),
      iterationInitialField?.toDouble(),
      DecimalIterator.Type.FLOAT
    )
  }

  private fun getLongIterator(iterationProps: Map<*, *>): Iterator<Any> {
    val iterationStartField = getIntegralNumberField(ITERATION_PROP, ITERATION_PROP_START, iterationProps)
    val iterationRestartField = getIntegralNumberField(ITERATION_PROP, ITERATION_PROP_RESTART, iterationProps)
    val iterationStepField = getIntegralNumberField(ITERATION_PROP, ITERATION_PROP_STEP, iterationProps)
    val iterationInitialField = getIntegralNumberField(ITERATION_PROP, ITERATION_PROP_INITIAL, iterationProps)
    return getIntegralIterator(
      iterationStartField,
      iterationRestartField,
      iterationStepField,
      iterationInitialField,
      IntegralIterator.Type.LONG
    )
  }

  private fun createStringIterator(inner: Iterator<Any>, propertiesProp: Map<*, *>): Iterator<Any> {
    return object : Iterator<Any> {
      override fun hasNext() = inner.hasNext()

      override fun next(): Any = prefixAndSuffixString(inner.next().toString(), propertiesProp)
    }
  }

  private fun getIntegerIterator(iterationProps: Map<*, *>): Iterator<Any> {
    val iterationStartField = getIntegerNumberField(ITERATION_PROP, ITERATION_PROP_START, iterationProps)
    val iterationRestartField = getIntegerNumberField(ITERATION_PROP, ITERATION_PROP_RESTART, iterationProps)
    val iterationStepField = getIntegerNumberField(ITERATION_PROP, ITERATION_PROP_STEP, iterationProps)
    val iterationInitialField = getIntegerNumberField(ITERATION_PROP, ITERATION_PROP_INITIAL, iterationProps)
    return getIntegralIterator(
      iterationStartField?.toLong(),
      iterationRestartField?.toLong(),
      iterationStepField?.toLong(),
      iterationInitialField?.toLong(),
      IntegralIterator.Type.INTEGER
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> generateIteration(schema: Schema, propertiesProp: Map<*, *>): T {
    if (!iteratorCache.containsKey(schema)) {
      iteratorCache[schema] = parseIterations(schema, propertiesProp)
    }
    return iteratorCache[schema]?.next() as T
  }

  private fun generateArray(schema: Schema, propertiesProp: Map<*, *>): Collection<Any?> {
    val length = getLengthBounds(propertiesProp).random()
    val result: MutableCollection<Any?> = ArrayList(length)
    for (i in 0 until length) {
      result.add(generateObject(schema.elementType))
    }
    return result
  }

  private fun generateBoolean(propertiesProp: Map<*, *>): Boolean {
    val odds = getDecimalNumberField(ARG_PROPERTIES_PROP, ODDS_PROP, propertiesProp)
    return if (odds == null)
      random.nextBoolean()
    else {
      if (odds < 0.0 || odds > 1.0) {
        throw RuntimeException(String.format("%s property must be in the range [0.0, 1.0]", ODDS_PROP))
      }
      random.nextDouble() < odds
    }
  }

  private fun generateBytes(schema: Schema, propertiesProp: Map<*, *>): ByteBuffer {
    val decimalLogicalType = getDecimalLogicalType(schema)
    val bytes: ByteArray
    if (decimalLogicalType != null) {
      bytes = generateDecimal(decimalLogicalType, propertiesProp)
    }
    else {
      bytes = ByteArray(getLengthBounds(propertiesProp[LENGTH_PROP]).random())
      random.nextBytes(bytes)
    }
    return ByteBuffer.wrap(bytes)
  }

  private fun generateDouble(propertiesProp: Map<*, *>): Double {
    val rangeProp = propertiesProp[RANGE_PROP]
    if (rangeProp != null) {
      if (rangeProp is Map<*, *>) {
        val rangeMin = getDecimalNumberField(RANGE_PROP, RANGE_PROP_MIN, rangeProp) ?: (-1 * Double.MAX_VALUE)
        val rangeMax = getDecimalNumberField(RANGE_PROP, RANGE_PROP_MAX, rangeProp) ?: Double.MAX_VALUE
        if (rangeMin >= rangeMax) {
          throw RuntimeException(String.format(
            "'%s' field must be strictly less than '%s' field in %s property",
            RANGE_PROP_MIN,
            RANGE_PROP_MAX,
            RANGE_PROP
          ))
        }
        return rangeMin + (random.nextDouble() * (rangeMax - rangeMin))
      }
      else {
        throw RuntimeException(String.format("%s property must be an object", RANGE_PROP))
      }
    }
    return random.nextDouble()
  }

  private fun generateEnumSymbol(schema: Schema): GenericEnumSymbol<*> {
    val enums = schema.enumSymbols
    return EnumSymbol(schema, enums[random.nextInt(enums.size)])
  }

  private fun generateFixed(schema: Schema): GenericFixed {
    val decimalLogicalType = getDecimalLogicalType(schema)
    val bytes: ByteArray
    if (decimalLogicalType != null) {
      // We don't support ranges for fixed decimal types at the moment
      bytes = generateDecimal(decimalLogicalType, null)
    }
    else {
      bytes = ByteArray(schema.fixedSize)
      random.nextBytes(bytes)
    }
    return GenericData.Fixed(schema, bytes)
  }

  private fun generateDecimal(decimalLogicalType: LogicalTypes.Decimal, propertiesProp: Map<*, *>?): ByteArray {
    val rangeProp = Optional.ofNullable(propertiesProp).map { m: Map<*, *>? -> m?.get(RANGE_PROP) }.orElse(null)
    if (rangeProp != null) {
      if (rangeProp is Map<*, *>) {
        val rangeMin = getDecimalNumberField(RANGE_PROP, RANGE_PROP_MIN, rangeProp) ?: (-1 * 10.0.pow(
          (decimalLogicalType.precision - decimalLogicalType.scale).toDouble()))
        val rangeMax = getDecimalNumberField(RANGE_PROP, RANGE_PROP_MAX, rangeProp) ?: 10.0.pow(
          (decimalLogicalType.precision - decimalLogicalType.scale).toDouble())
        if (rangeMin >= rangeMax) {
          throw RuntimeException(String.format(
            "'%s' field must be strictly less than '%s' field in %s property",
            RANGE_PROP_MIN,
            RANGE_PROP_MAX,
            RANGE_PROP
          ))
        }
        // We'll just generate a random double in the requested range and then convert it to a logical decimal type
        val result = rangeMin + (random.nextDouble() * (rangeMax - rangeMin))
        return BigDecimal.valueOf(result)
          // Adjust by the scale of the decimal type in order to get the "unscaled" value described below before
          // converting to a twos-complement byte array
          .scaleByPowerOfTen(decimalLogicalType.scale)
          .toBigInteger()
          .toByteArray()
      }
      else {
        throw RuntimeException(String.format("%s property must be an object", RANGE_PROP))
      }
    }
    else {
      /*
        According to the Avro 1.9.1 spec (http://avro.apache.org/docs/1.9.1/spec.html#Decimal):
        "The decimal logical type represents an arbitrary-precision signed decimal number of the form
      unscaled × 10-scale.
        "A decimal logical type annotates Avro bytes or fixed types. The byte array must contain the
      two's-complement representation of the unscaled integer value in big-endian byte order. The scale
      is fixed, and is specified using an attribute."
        We generate a random decimal here by starting with a value of zero, then repeatedly multiplying
      by 10^15 (15 is the minimum number of significant digits in a double), and adding a new random
      value in the range [0, 10^15) generated using the Random object for this generator. This is done
      until the precision of the current value is equal to or greater than the precision of the logical
      type. At this point, any extra digits (of there should be at most 14) are rounded off from the
      value, a sign is randomly selected, it is converted to big-endian two's-complement representation,
      and returned.
      */
      var bigInteger = BigInteger.ZERO
      val maxIncrementExclusive = 1000000000000000L
      var precision = 0
      while (precision < decimalLogicalType.precision) {
        bigInteger = bigInteger.multiply(BigInteger.valueOf(maxIncrementExclusive))
        val increment = (random.nextDouble() * maxIncrementExclusive).toLong()
        bigInteger = bigInteger.add(BigInteger.valueOf(increment))
        precision += 15
      }
      bigInteger = bigInteger.divide(
        BigInteger.TEN.pow(precision - decimalLogicalType.precision)
      )
      if (random.nextBoolean()) {
        bigInteger = bigInteger.negate()
      }
      return bigInteger.toByteArray()
    }
  }

  private fun generateFloat(propertiesProp: Map<*, *>): Float {
    val rangeProp = propertiesProp[RANGE_PROP]
    if (rangeProp != null) {
      if (rangeProp is Map<*, *>) {
        val rangeMinField = getFloatNumberField(RANGE_PROP, RANGE_PROP_MIN, rangeProp)
        val rangeMaxField = getFloatNumberField(RANGE_PROP, RANGE_PROP_MAX, rangeProp)
        val rangeMin = Optional.ofNullable(rangeMinField).orElse(-1 * Float.MAX_VALUE)
        val rangeMax = Optional.ofNullable(rangeMaxField).orElse(Float.MAX_VALUE)
        if (rangeMin >= rangeMax) {
          throw RuntimeException(String.format(
            "'%s' field must be strictly less than '%s' field in %s property",
            RANGE_PROP_MIN,
            RANGE_PROP_MAX,
            RANGE_PROP
          ))
        }
        return rangeMin + (random.nextFloat() * (rangeMax - rangeMin))
      }
    }
    return random.nextFloat()
  }

  private fun generateInt(propertiesProp: Map<*, *>): Int {
    val rangeProp = propertiesProp[RANGE_PROP]
    if (rangeProp != null) {
      if (rangeProp is Map<*, *>) {
        val rangeMinField = getIntegerNumberField(RANGE_PROP, RANGE_PROP_MIN, rangeProp)
        val rangeMaxField = getIntegerNumberField(RANGE_PROP, RANGE_PROP_MAX, rangeProp)
        val rangeMin = Optional.ofNullable(rangeMinField).orElse(Int.MIN_VALUE)
        val rangeMax = Optional.ofNullable(rangeMaxField).orElse(Int.MAX_VALUE)
        if (rangeMin >= rangeMax) {
          throw RuntimeException(String.format(
            "'%s' field must be strictly less than '%s' field in %s property",
            RANGE_PROP_MIN,
            RANGE_PROP_MAX,
            RANGE_PROP
          ))
        }
        return rangeMin + ((random.nextDouble() * (rangeMax - rangeMin)).toInt())
      }
    }
    return random.nextInt()
  }

  private fun generateLong(propertiesProp: Map<*, *>): Long {
    val rangeProp = propertiesProp[RANGE_PROP]
    if (rangeProp != null) {
      if (rangeProp is Map<*, *>) {
        val rangeMinField = getIntegralNumberField(RANGE_PROP, RANGE_PROP_MIN, rangeProp)
        val rangeMaxField = getIntegralNumberField(RANGE_PROP, RANGE_PROP_MAX, rangeProp)
        val rangeMin = Optional.ofNullable(rangeMinField).orElse(Long.MIN_VALUE)
        val rangeMax = Optional.ofNullable(rangeMaxField).orElse(Long.MAX_VALUE)
        if (rangeMin >= rangeMax) {
          throw RuntimeException(String.format(
            "'%s' field must be strictly less than '%s' field in %s property",
            RANGE_PROP_MIN,
            RANGE_PROP_MAX,
            RANGE_PROP
          ))
        }
        return rangeMin + (((random.nextDouble() * (rangeMax - rangeMin)).toLong()))
      }
    }
    return random.nextLong()
  }

  private fun generateMap(schema: Schema, propertiesProp: Map<*, *>): Map<String, Any?> {
    val result: MutableMap<String, Any?> = HashMap()
    val length = getLengthBounds(propertiesProp).random()
    val keyProp = propertiesProp[KEYS_PROP]
    if (keyProp == null) {
      for (i in 0 until length) {
        result[generateRandomString(1)] = generateObject(schema.valueType)
      }
    }
    else if (keyProp is Map<*, *>) {
      if (keyProp.containsKey(OPTIONS_PROP)) {
        if (!optionsCache.containsKey(schema)) {
          optionsCache[schema] = parseOptions(Schema.create(Schema.Type.STRING), keyProp)
        }
        for (i in 0 until length) {
          result[generateOption(schema, keyProp)] = generateObject(schema.valueType)
        }
      }
      else {
        for (i in 0 until length) {
          result[generateString(schema, keyProp)] = generateObject(schema.valueType)
        }
      }
    }
    else {
      throw RuntimeException(String.format("%s prop must be an object", KEYS_PROP))
    }
    return result
  }

  private fun generateNull() = null

  private fun generateRecord(schema: Schema): GenericRecord {
    val builder = GenericRecordBuilder(schema)
    for (field: Schema.Field in schema.fields) {
      builder.set(field, generateObject(field.schema()))
    }
    return builder.build()
  }

  private fun generateRegexString(schema: Schema, regexProp: Any, lengthBounds: LengthBounds): String {
    if (!generexCache.containsKey(schema)) {
      if (regexProp !is String) {
        throw RuntimeException(String.format("%s property must be a string", REGEX_PROP))
      }
      generexCache[schema] = Generex(regexProp, random)
    }
    // Generex.random(low, high) generates in range [low, high]; we want [low, high), so subtract
    // 1 from maxLength
    return generexCache[schema]?.random(lengthBounds.min(), lengthBounds.max() - 1) ?: ""
  }

  private fun generateRandomString(length: Int): String {
    val bytes = ByteArray(length)
    for (i in 0 until length) {
      bytes[i] = random.nextInt(128).toByte()
    }
    return String(bytes, StandardCharsets.US_ASCII)
  }

  private fun generateString(schema: Schema, propertiesProp: Map<*, *>): String {
    val regexProp = propertiesProp[REGEX_PROP]
    val result: String
    if (regexProp != null) {
      val lengthProp = propertiesProp[LENGTH_PROP]
      val lengthBounds: LengthBounds
      if (lengthProp == null) {
        lengthBounds = LengthBounds(0, Int.MAX_VALUE)
      }
      else {
        lengthBounds = getLengthBounds(lengthProp)
      }
      result = generateRegexString(schema, regexProp, lengthBounds)
    }
    else {
      result = generateRandomString(getLengthBounds(propertiesProp).random())
    }
    return prefixAndSuffixString(result, propertiesProp)
  }

  private fun prefixAndSuffixString(result: String, propertiesProp: Map<*, *>): String {
    val prefixProp = propertiesProp[PREFIX_PROP]
    if (prefixProp != null && prefixProp !is String) {
      throw RuntimeException(String.format("%s property must be a string", PREFIX_PROP))
    }
    val prefix = if (prefixProp != null) prefixProp as String else ""
    val suffixProp = propertiesProp[SUFFIX_PROP]
    if (suffixProp != null && suffixProp !is String) {
      throw RuntimeException(String.format("%s property must be a string", SUFFIX_PROP))
    }
    val suffix = if (suffixProp != null) suffixProp as String else ""
    return prefix + result + suffix
  }

  private fun generateUnion(schema: Schema): Any? {
    val schemas = schema.types
    return generateObject(schemas[random.nextInt(schemas.size)])
  }

  private fun getLengthBounds(propertiesProp: Map<*, *>) = getLengthBounds(propertiesProp[LENGTH_PROP])

  private fun getLengthBounds(lengthProp: Any?): LengthBounds {
    when (lengthProp) {
      null -> return LengthBounds()
      is Int -> {
        if (lengthProp < 0) {
          throw RuntimeException(String.format("when given as integral number, %s property cannot be negative", LENGTH_PROP))
        }
        return LengthBounds(lengthProp)
      }
      is Map<*, *> -> {
        var minLength = getIntegerNumberField(LENGTH_PROP, LENGTH_PROP_MIN, lengthProp)
        var maxLength = getIntegerNumberField(LENGTH_PROP, LENGTH_PROP_MAX, lengthProp)
        if (minLength == null && maxLength == null) {
          throw RuntimeException(String.format(
            "%s property must contain at least one of '%s' or '%s' fields when given as object",
            LENGTH_PROP,
            LENGTH_PROP_MIN,
            LENGTH_PROP_MAX
          ))
        }
        minLength = minLength ?: 0
        maxLength = maxLength ?: Int.MAX_VALUE
        if (minLength < 0) {
          throw RuntimeException(String.format("%s field of %s property cannot be negative", LENGTH_PROP_MIN, LENGTH_PROP))
        }
        if (maxLength <= minLength) {
          throw RuntimeException(String.format(
            "%s field must be strictly greater than %s field for %s property",
            LENGTH_PROP_MAX,
            LENGTH_PROP_MIN,
            LENGTH_PROP
          ))
        }
        return LengthBounds(minLength, maxLength)
      }
      else -> {
        throw RuntimeException(String.format(
          "%s property must either be an integral number or an object, was %s instead",
          LENGTH_PROP,
          lengthProp.javaClass.name
        ))
      }
    }
  }

  private fun getIntegerNumberField(property: String, field: String, propsMap: Map<*, *>): Int? {
    val result = getIntegralNumberField(property, field, propsMap)
    if (result != null && (result < Int.MIN_VALUE || result > Int.MAX_VALUE)) {
      throw RuntimeException(String.format("'%s' field of %s property must be a valid int for int schemas", field, property))
    }
    return result?.toInt()
  }

  private fun getIntegralNumberField(property: String, field: String, propsMap: Map<*, *>): Long? {
    return when (val result = propsMap[field]) {
      null, is Long -> result as Long?
      is Int -> result.toLong()
      else -> {
        throw RuntimeException(String.format(
          "'%s' field of %s property must be an integral number, was %s instead",
          field,
          property,
          result.javaClass.name
        ))
      }
    }
  }

  private fun getFloatNumberField(property: String, field: String, propsMap: Map<*, *>): Float? {
    val result = getDecimalNumberField(property, field, propsMap)
    if (result != null && (result > Float.MAX_VALUE || result < -1 * Float.MAX_VALUE)) {
      throw RuntimeException(String.format("'%s' field of %s property must be a valid float for float schemas", field, property))
    }
    return result?.toFloat()
  }

  private fun getDecimalNumberField(property: String, field: String, propsMap: Map<*, *>): Double? {
    return when (val result = propsMap[field]) {
      null, is Double -> result as Double?
      is Float -> result.toDouble()
      is Int -> result.toDouble()
      is Long -> result.toDouble()
      else -> {
        throw RuntimeException(String.format(
          "'%s' field of %s property must be a number, was %s instead",
          field,
          property,
          result.javaClass.name
        ))
      }
    }
  }

  private inner class LengthBounds(private val min: Int = 8, private val max: Int = 16) {
    constructor(exact: Int) : this(exact, exact + 1)

    fun random() = min + random.nextInt(max - min)

    fun min() = min

    fun max() = max
  }

  private class IntegralIterator(start: Long, restart: Long,
                                 step: Long,
                                 initial: Long,
                                 count: Long,
                                 private val type: Type) : Iterator<Any> {
    enum class Type {
      INTEGER,
      LONG
    }

    private val start: BigInteger = BigInteger.valueOf(start)
    private val restart: BigInteger = BigInteger.valueOf(restart)
    private val step: BigInteger = BigInteger.valueOf(step)
    private var current: BigInteger

    init {
      current = BigInteger.valueOf(initial).subtract(this.start)
      if (count > 0) {
        // This is essentially the following expression when ignoring negative values:
        // current = (count * step) % (restart - start)
        // except BigInteger::mod only operates on positive numbers, so remove and re-add the sign after the modulo.
        current = BigInteger.valueOf(count)
          .multiply(this.step)
          .add(current)
          .abs()
          .mod(this.restart.subtract(this.start).abs())
          .multiply(this.step.divide(this.step.abs()))
      }
    }

    override fun next(): Any {
      val result = current.add(start)
      current = current
        .add(step)
        .abs()
        .mod(restart.subtract(start).abs())
        .multiply(step.divide(step.abs()))
      when (type) {
        Type.INTEGER -> return result.toInt()
        Type.LONG -> return result.toLong()
        else -> throw RuntimeException(String.format("Unexpected Type: %s", type))
      }
    }

    override fun hasNext() = true
  }

  private class DecimalIterator(start: Double,
                                restart: Double,
                                step: Double,
                                initial: Double,
                                count: Long,
                                private val type: Type) : Iterator<Any> {
    enum class Type {
      FLOAT,
      DOUBLE
    }

    private val start: BigDecimal = BigDecimal.valueOf(start)
    private val restart: BigDecimal = BigDecimal.valueOf(restart)
    private val modulo: BigDecimal = this.restart.subtract(this.start)
    private val step: BigDecimal = BigDecimal.valueOf(step)
    private var current: BigDecimal

    init {
      current = BigDecimal.valueOf(initial).subtract(this.start)
      if (count > 0) {
        current = BigDecimal.valueOf(count)
          .multiply(this.step)
          .add(current)
          .remainder(modulo)
      }
    }

    override fun next(): Any {
      val result = current.add(start)
      current = current
        .add(step)
        .remainder(modulo)
      when (type) {
        Type.FLOAT -> return result.toFloat()
        Type.DOUBLE -> return result.toDouble()
        else -> throw RuntimeException(String.format("Unexpected Type: %s", type))
      }
    }

    override fun hasNext() = true
  }

  private class BooleanIterator(private var current: Boolean) : Iterator<Any> {
    override fun next(): Boolean {
      val result = current
      current = !current
      return result
    }

    override fun hasNext() = true
  }

  companion object {
    /**
     * The name to use for the top-level JSON property when specifying ARG-specific attributes.
     */
    const val ARG_PROPERTIES_PROP = "arg.properties"

    /**
     * The name of the attribute for specifying length for supported schemas. Can be given as either
     * an integral number or an object with at least one of [.LENGTH_PROP_MIN] or
     * [.LENGTH_PROP_MAX] specified.
     */
    const val LENGTH_PROP = "length"

    /**
     * The name of the attribute for specifying the minimum length a generated value should have.
     * Must be given as an integral number greater than or equal to zero.
     */
    const val LENGTH_PROP_MIN = "min"

    /**
     * The name of the attribute for specifying the maximum length a generated value should have.
     * Must be given as an integral number strictly greater than the value given for
     * [.LENGTH_PROP_MIN], or strictly greater than zero if none is specified.
     */
    const val LENGTH_PROP_MAX = "max"

    /**
     * The name of the attribute for specifying a regex that generated values should adhere to. Can
     * be used in conjunction with [.LENGTH_PROP]. Must be given as a string.
     */
    const val REGEX_PROP = "regex"

    /**
     * The name of the attribute for specifying a prefix that generated values should begin with. Will
     * be prepended to the beginning of any string values generated. Can be used in conjunction with
     * [.SUFFIX_PROP].
     */
    const val PREFIX_PROP = "prefix"

    /**
     * The name of the attribute for specifying a suffix that generated values should end with. Will
     * be appended to the end of any string values generated. Can be used in conjunction with
     * [.PREFIX_PROP].
     */
    const val SUFFIX_PROP = "suffix"

    /**
     * The name of the attribute for specifying specific values which should be randomly chosen from
     * when generating values for the schema. Can be given as either an array of values or an object
     * with both [.OPTIONS_PROP_FILE] and [.OPTIONS_PROP_ENCODING]
     * specified.
     */
    const val OPTIONS_PROP = "options"

    /**
     * The name of the attribute for specifying special properties for keys in map schemas. Since
     * all Avro maps have keys of type string, no schema is supplied to specify key attributes; this
     * special keys attribute takes its place. Must be given as an object.
     */
    const val KEYS_PROP = "keys"

    /**
     * The name of the attribute for specifying a possible range of values for numeric types. Must be
     * given as an object.
     */
    const val RANGE_PROP = "range"

    /**
     * The name of the attribute for specifying the (inclusive) minimum value in a range. Must be
     * given as a numeric type that is integral if the given schema is as well.
     */
    const val RANGE_PROP_MIN = "min"

    /**
     * The name of the attribute for specifying the (exclusive) maximum value in a range. Must be
     * given as a numeric type that is integral if the given schema is as well.
     */
    const val RANGE_PROP_MAX = "max"

    /**
     * The name of the attribute for specifying the likelihood that the value true is generated for a
     * boolean schema. Must be given as a floating type in the range [0.0, 1.0].
     */
    const val ODDS_PROP = "odds"

    /**
     * The name of the attribute for specifying iterative behavior for generated values. Must be
     * given as an object with at least the [.ITERATION_PROP_START] property specified. The
     * first generated value for the schema will then be equal to the value given for
     * [.ITERATION_PROP_START], and successive values will increment by the value given for
     * [.ITERATION_PROP_STEP] (or its default, if no value is given), wrapping around at the
     * value given for [.ITERATION_PROP_RESTART] (or its default, if no value is given).
     */
    const val ITERATION_PROP = "iteration"

    /**
     * The name of the attribute for specifying the first value in a schema with iterative
     * generation. Must be given as a numeric type that is integral if the given schema is as well.
     */
    const val ITERATION_PROP_START = "start"

    /**
     * The name of the attribute for specifying the wraparound value in a schema with iterative
     * generation. If given, must be a numeric type that is integral if the given schema is as well.
     * If not given, defaults to the maximum possible value for the schema type if the value for
     * [.ITERATION_PROP_STEP] is positive, or the minimum possible value for the schema type if
     * the value for [.ITERATION_PROP_STEP] is negative.
     */
    const val ITERATION_PROP_RESTART = "restart"

    /**
     * The name of the attribute for specifying the increment value in a schema with iterative
     * generation. If given, must be a numeric type that is integral if the given schema is as well.
     * If not given, defaults to 1 if the value for [.ITERATION_PROP_RESTART] is greater than
     * the value for [.ITERATION_PROP_START], and -1 if the value for
     * [.ITERATION_PROP_RESTART] is less than the value for [.ITERATION_PROP_START].
     */
    const val ITERATION_PROP_STEP = "step"

    /**
     * The name of the attribute for specifying the initial value in a schema with iterative
     * generation. If given, must be a numeric type that is integral if the given schema is as well.
     * If not given, defaults to the value for [.ITERATION_PROP_START].
     */
    const val ITERATION_PROP_INITIAL = "initial"
    const val DECIMAL_LOGICAL_TYPE_NAME = "decimal"

    private const val PRETTY_FORMAT = true
    private const val ITERATION_NUM: Long = 1

    fun generateAvroMessage(project: Project?, schema: ParsedSchema?): String {
      if (schema?.isValidSchema(project) == false) {
        return ""
      }

      val avroSchema = schema?.rawSchema() as? Schema
      if (schema?.schemaType() != "AVRO" || avroSchema == null) {
        logger.warn("Schema could not be null and the type of it should be AVRO")
        return ""
      }

      val jsonFormat = PRETTY_FORMAT
      val iterations = ITERATION_NUM

      val generator = AvroGenerator(avroSchema)
      val dataWriter: DatumWriter<Any?> = GenericDatumWriter(generator.schema())
      try {
        ByteArrayOutputStream().use { output ->
          val encoder: Encoder = EncoderFactory.get().jsonEncoder(generator.schema(), output, jsonFormat)
          for (i in 0 until iterations) {
            dataWriter.write(generator.generate(), encoder)
          }
          encoder.flush()
          output.write('\n'.code)
          return output.toString()
        }
      }
      catch (ioe: IOException) {
        logger.warn("Error occurred while trying to generate avro: " + ioe.messageOrDefault())
        return ""
      }
    }
  }
}