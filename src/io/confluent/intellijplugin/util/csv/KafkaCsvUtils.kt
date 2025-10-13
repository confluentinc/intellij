package io.confluent.intellijplugin.util.csv

import com.intellij.charts.dataframe.DataFrame
import com.intellij.charts.dataframe.DataFrameCSVAdapter
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.io.File

object KafkaCsvUtils {
    fun readDataFrame(path: String): DataFrame {
        val file = File(path).readText()
        val dataFrame = DataFrameCSVAdapter.fromCsvString(file)
        if (dataFrame.rowsCount == 0)
            throw Exception(KafkaMessagesBundle.message("exception.message.csv.is.empty", path))
        dataFrame.getColumns().map { it.name }
        return dataFrame
    }

    fun getKey(df: DataFrame, index: Int): String {
        val column = when {
            df.has("key") -> df["key"]
            df.columnsCount == 2 -> df[0]
            else -> return ""
        }
        return column[index % df.rowsCount]?.toString() ?: ""
    }

    fun getValue(df: DataFrame, index: Int): String {
        val column = when {
            df.has("value") -> df["value"]
            df.columnsCount == 2 -> df[1]
            df.columnsCount == 1 -> df[0]
            else -> return ""
        }
        return column[index % df.rowsCount]?.toString() ?: ""
    }
}