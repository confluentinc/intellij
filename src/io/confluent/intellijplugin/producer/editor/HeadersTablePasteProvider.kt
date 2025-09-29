package io.confluent.intellijplugin.producer.editor

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ide.CopyPasteManager
import io.confluent.intellijplugin.common.editor.PropertiesTable
import io.confluent.intellijplugin.core.serializer.BdtJson
import java.awt.datatransfer.DataFlavor

class HeadersTablePasteProvider(private val table: PropertiesTable) : PasteProvider {
  override fun performPaste(dataContext: DataContext) {
    val contents = CopyPasteManager.getInstance().contents ?: return
    val data = try {
      contents.getTransferData(DataFlavor.stringFlavor) as? String ?: return
    }
    catch (t: Throwable) {
      return
    }

    //Parse JSON
    try {
      val json = BdtJson.fromJsonToMapStringAny(data)
      val jsonEntries = json.entries.map { it.key to (it.value?.toString() ?: "") }
      table.addEntries(jsonEntries)
      return
    }
    catch (t: Throwable) {
      //Ignore
    }
    val parsedCsv = data.split("\n").map { row ->
      val origin = row.replace("\n", "").trim('\t', ',', ';', ' ')
      val first = origin.takeWhile { it !in setOf('\r', '\t', ',', ';', ' ') }
      val second = origin.removePrefix(first)
      first.trim('\t', ',', ';', ' ') to second.trim('\t', ',', ';', ' ')
    }
    table.addEntries(parsedCsv)
  }

  override fun isPastePossible(dataContext: DataContext) = true
  override fun isPasteEnabled(dataContext: DataContext) = true
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}