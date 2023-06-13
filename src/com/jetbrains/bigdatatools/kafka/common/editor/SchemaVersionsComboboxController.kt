package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.SimpleListCellRenderer
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class SchemaVersionsComboboxController(rootDisposable: Disposable,
                                       private val kafkaManager: KafkaDataManager,
                                       private val onListUpdate: (List<Long>) -> Unit) : Disposable {
  var schemaName: String = ""

  var disposable = Disposer.newDisposable(this)

  private val versionCombobox = ComboBox<Long>(emptyArray()).apply {
    isSwingPopup = false
    prototypeDisplayValue = 11111
    renderer = SimpleListCellRenderer.create(KafkaMessagesBundle.message("schema.version.is.not.found")) { "Version $it" }
  }

  init {
    Disposer.register(rootDisposable, this)
  }

  override fun dispose() {}

  fun getComponent() = versionCombobox

  fun setSchema(schemaName: String) {
    Disposer.dispose(disposable)
    disposable = Disposer.newDisposable(this)
    val versionModel = kafkaManager.getSchemaVersionsModel(schemaName)

    KafkaEditorUtils.updateComboBox(versionCombobox, onListUpdate = onListUpdate) {
      versionModel.originObject to null
    }

    val listener = KafkaEditorUtils.KafkaDataModelListener(versionCombobox, onListUpdate) {
      val newVersions = versionModel.originObject
      versionCombobox.putUserData(VERSIONS_LIST_KEY, newVersions)
      newVersions to null
    }
    versionModel.addListener(listener)
    Disposer.register(disposable) {
      versionModel.removeListener(listener)
    }
  }

  companion object {
    val VERSIONS_LIST_KEY = Key<List<Long>?>("VERSIONS_LIST_KEY")
  }
}