package io.confluent.kafka.util.generator

import com.intellij.util.applyIf
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.*
import kotlin.random.Random
import kotlin.streams.asSequence

object PrimitivesGenerator {
  private val random = ThreadLocalRandom.current()
  private val lowercaseLetters = ('a'..'z').toList()
  private val letters = lowercaseLetters + ('A'..'Z')
  private val lettersAndDigits = letters + ('0'..'9') + '_'
  private val domains = listOf("com", "org", "nl", "cz", "de", "io", "cy", "us", "uk", "cn", "it")
  private val hexadecimal = ('0'..'9').toList() + ('A'..'F')

  private val userNameLength = 5..20
  private val hostNameLength = 3..7

  fun generateString(minLength: Int = 3, maxLength: Int = 20, requireLetters: Boolean = false): String {
    val allowedChars = (('A'..'Z') + ('a'..'z')).applyIf(!requireLetters) {
      this.plus('0'..'9')
    }
    return (1..Random.nextInt(minLength, maxLength)).map { allowedChars.random() }.joinToString("")
  }

  fun generateLong(from: Long = Long.MIN_VALUE, until: Long = Long.MAX_VALUE): Long = Random.nextLong(from, until)

  fun generateDouble(from: Double? = Double.MIN_VALUE, until: Double? = Double.MAX_VALUE): Double =
    Random.nextDouble(from ?: Double.MIN_VALUE, until ?: Double.MAX_VALUE)

  fun generateFloat(from: Float = Float.MIN_VALUE, until: Float = Float.MAX_VALUE): Float = (from + Random.nextFloat() * (until - from))

  fun generateBytesBase64(minSize: Int = 10, maxSize: Int = 50): String =
    Base64.getEncoder().encodeToString(Random.nextBytes(Random.nextInt(minSize, maxSize)))

  fun generateBytes(minSize: Int = 10, maxSize: Int = 100): ByteArray = Random.nextBytes(Random.nextInt(minSize, maxSize))

  fun generateInt(from: Int? = Int.MIN_VALUE, until: Int? = Int.MAX_VALUE): Int =
    Random.nextInt(from ?: Int.MIN_VALUE, until ?: Int.MAX_VALUE)

  fun generateUlong(from: ULong = ULong.MIN_VALUE, until: ULong = ULong.MAX_VALUE): ULong = Random.nextULong(from, until)

  fun generateUint(from: UInt = UInt.MIN_VALUE, until: UInt = UInt.MAX_VALUE): UInt = Random.nextUInt(from, until)

  fun generateBoolean(): Boolean = Random.nextBoolean()

  fun generateUUID(): UUID = UUID.randomUUID()

  fun generateEmail(): String {
    val random = random.asKotlinRandom()
    val domain = domains.random(random)

    val userNameLength = random.nextInt(userNameLength)
    val hostNameLength = random.nextInt(hostNameLength)

    val userName = lettersAndDigits.chooseNTimes(userNameLength).collectString()
    val hostName = letters.chooseNTimes(hostNameLength).collectString()

    return "$userName@$hostName.$domain"

  }

  fun createTimestamp(): String = Instant.now().epochSecond.toString()

  fun createIsoTimestamp(): String {
    return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
  }

  fun createHex(length: Int): String = buildString {
    append(hexadecimal[random.nextInt(1, hexadecimal.size)])
    if (length > 1) {
      for (c in hexadecimal.chooseNTimes(length - 1)) {
        append(c)
      }
    }
  }

  fun createAlphaNum(length: Int): String = lettersAndDigits.chooseNTimes(length).collectString()

  fun generateAlphabetic(length: Int): String = letters.chooseNTimes(length).collectString()

  private fun Sequence<Char>.collectString(): String = joinToString("") { "$it" }

  private fun <T> List<T>.chooseNTimes(n: Int): Sequence<T> =
    random.ints(0, size).asSequence()
      .take(n)
      .map { this[it] }
}