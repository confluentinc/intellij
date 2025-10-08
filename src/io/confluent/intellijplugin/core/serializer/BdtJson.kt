package io.confluent.intellijplugin.core.serializer

import com.intellij.credentialStore.OneTimeString
import com.intellij.openapi.util.NlsSafe
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Token.END_ARRAY
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


/**
 * Object, which implements methods for work with Json
 */
object BdtJson {
    val moshi: Moshi = Moshi.Builder()
        .add(OneTimeString::class.java, OneTimeStringAdapter)
        .add(Date::class.java, SparkMonitoringDateJsonAdapter().nullSafe())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @NlsSafe
    fun toJson(value: Any, pretty: Boolean = false, indent: String = "  "): String {
        var adapter = moshi.adapter(Any::class.java)

        if (pretty) {
            adapter = adapter.indent(indent)
            adapter = adapter.serializeNulls()
        }
        return adapter.toJson(value) ?: throw JsonSerializeException(value.toString())
    }

    fun <T> fromJsonToClass(jsonString: String, clazz: Class<T>): T =
        moshi.adapter(clazz).fromJson(jsonString) ?: throw JsonDeserializeException(jsonString, clazz::class.toString())

    @Suppress("UNCHECKED_CAST")
    fun fromJsonToMapStringAny(jsonString: String): Map<String, Any?> =
        moshi.adapter(Any::class.java).fromJson(jsonString) as Map<String, Any>


    @Suppress("unused")
    fun <T> fromJsonToClassMap(jsonString: String, clazz: Class<T>): Map<String, T> {
        val mapObj = moshi.adapter(Map::class.java).fromJson(jsonString)
            ?: throw JsonDeserializeException(jsonString, List::class.toString())

        return mapObj.entries.associate { (k, v) -> k.toString() to fromValueObjToClass(v, clazz) }
    }


    fun <T> fromValueObjToClass(value: Any?, clazz: Class<T>): T =
        moshi.adapter(clazz).fromJsonValue(value) ?: throw JsonDeserializeException(
            value.toString(),
            clazz::class.toString()
        )
}

class JsonSerializeException(obj: Any) : Exception() {
    override val message: String = "Cannot convert object to json.\nObject:\n$obj"
}

class JsonDeserializeException(
    json: String,
    parseClass: String
) : Exception() {
    override val message: String = "Cannot parse json to object.\nJson:\n$json\nClass:\n$parseClass"
}

object OneTimeStringAdapter : JsonAdapter<OneTimeString>() {
    override fun fromJson(reader: JsonReader): OneTimeString {
        reader.beginArray()
        val resultingList = mutableListOf<Char>()
        while (reader.peek() != END_ARRAY) {
            resultingList.add(reader.nextString()[0])
        }
        reader.endArray()
        return OneTimeString(resultingList.toCharArray())
    }

    override fun toJson(writer: JsonWriter, value: OneTimeString?) {
        writer.beginArray()
        if (value == null) {
            writer.endArray()
            return
        }
        for (i in value.toCharArray()) {
            writer.value(i.toString())
        }
        writer.endArray()
    }

}


@Suppress("unused")
private class SparkMonitoringDateJsonAdapter : JsonAdapter<Date>() {
    private val threadLocalFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz", Locale.US)
    }

    override fun fromJson(p0: JsonReader): Date? {
        return try {
            threadLocalFormat.get().parse(p0.nextString())
        } catch (e: ParseException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun toJson(p0: JsonWriter, p1: Date?) {
        p0.value(threadLocalFormat.get().format(p1))
    }
}
