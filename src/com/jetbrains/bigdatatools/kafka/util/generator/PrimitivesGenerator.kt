package com.jetbrains.bigdatatools.kafka.util.generator

import java.util.*
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.random.nextULong

object PrimitivesGenerator {
  fun generateString(minLength: Int = 3, maxLength: Int = 20): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..Random.nextInt(minLength, maxLength)).map { allowedChars.random() }.joinToString("")
  }

  fun generateLong(from: Long = Long.MIN_VALUE, until: Long = Long.MAX_VALUE) = Random.nextLong(from, until)

  fun generateDouble(from: Double = Double.MIN_VALUE, until: Double = Double.MAX_VALUE) = Random.nextDouble(from, until)

  fun generateFloat(from: Float = Float.MIN_VALUE, until: Float = Float.MAX_VALUE) = (from + Random.nextFloat() * (until - from))

  fun generateBytesBase64(minSize: Int = 10, maxSize: Int = 100): ByteArray =
    Base64.getEncoder().encode(Random.nextBytes(Random.nextInt(minSize, maxSize)))

  fun generateBytes(minSize: Int = 10, maxSize: Int = 100): ByteArray = Random.nextBytes(Random.nextInt(minSize, maxSize))

  fun generateInt(from: Int = Int.MIN_VALUE, until: Int = Int.MAX_VALUE) = Random.nextInt(from, until)

  fun generateUlong(from: ULong = ULong.MIN_VALUE, until: ULong = ULong.MAX_VALUE) = Random.nextULong(from, until)

  fun generateUint(from: UInt = UInt.MIN_VALUE, until: UInt = UInt.MAX_VALUE) = Random.nextUInt(from, until)

  fun generateBoolean() = Random.nextBoolean()
}