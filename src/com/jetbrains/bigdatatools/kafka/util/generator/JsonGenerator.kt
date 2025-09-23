package io.confluent.kafka.util.generator

import com.google.gson.JsonObject
import io.confluent.kafka.util.generator.PrimitivesGenerator.generateInt
import io.confluent.kafka.util.generator.PrimitivesGenerator.generateLong
import kotlin.random.Random

object JsonGenerator {
  private val cities = listOf("London", "New York", "Sydney", "Paris", "Tokyo", "Paphos", "Toronto", "Dubai", "Dublin", "Rio de Janeiro")
  private val temperatures = listOf(15.2, 20.5, 25.0, 18.0, 22.9, 28.0, 12.0, 35.6, 10.2, 27.0)
  private val names = listOf("Alex", "Jordan", "Taylor", "Casey", "Charlie", "Drew", "Jamie", "Jesse", "Kris", "Morgan", "Pat", "Clara",
                             "Robin", "Sage", "Sam", "Spencer", "Sutton", "Terry", "Tony", "Tyler")
  private val colors = listOf("Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "Gray", "Black", "White")
  private val statuses = listOf("Active", "Inactive", "Pending", "Approved", "Denied", "Cancelled", "Completed", "Processing", "On hold",
                                "Returned")
  private val tags = listOf("Technology", "Sports", "Politics", "Entertainment", "Travel", "Food", "Fashion", "Education", "Health",
                            "Business")

  private fun createSimpleObject() = hashMapOf<String, Any>(
    "id".addRandomChars() to generateLong(),
    "year".addRandomChars() to generateInt(1970, 2023),
    "count".addRandomChars() to generateInt(100, 10_000),
    "city".addRandomChars() to getRandomItem(cities),
    "temperature".addRandomChars() to getRandomItem(temperatures),
    "name".addRandomChars() to getRandomItem(names),
    "color".addRandomChars() to getRandomItem(colors),
    "status".addRandomChars() to getRandomItem(statuses),
    "tag".addRandomChars() to getRandomItem(tags)
  )

  private fun String.addRandomChars() = "$this/${generateLong()}"
  private fun String.removeRandomChars() = this.takeWhile { it.isLetter() }

  private fun <T> getRandomItem(array: List<T>): T = array[Random.nextInt(array.size)]

  fun generateJson(): JsonObject {
    val jsonObject = JsonObject()

    val simpleObject = createSimpleObject()
    val numberOfFields = Random.nextInt(2, simpleObject.size)
    var count = 0
    for ((key, value) in simpleObject.entries) {
      if (count > numberOfFields)
        break

      jsonObject.addProperty(key.removeRandomChars(), value.toString())
      count += 1
    }
    return jsonObject
  }
}