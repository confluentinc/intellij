package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.SimpleListCellRenderer
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class SchemaVersionsComboboxController(rootDisposable: Disposable,
                                       private val kafkaManager: KafkaDataManager,
                                       private val onListUpdate: (List<Long>) -> Unit) : Disposable {
  var schemaName: String = ""

  var disposable = Disposer.newDisposable(this)

  private val versionCombobox = ComboBox<Long>(emptyArray()).apply {
    isSwingPopup = false
    prototypeDisplayValue = 11111
    renderer = SimpleListCellRenderer.create(KafkaMessagesBundle.message("schema.version.is.not.found")) {
      KafkaMessagesBundle.message("schema.version", it)
    }
  }

  val isVisible = AtomicBooleanProperty(true)

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
      isVisible.set(!versionModel.originObject.isNullOrEmpty())
      versionCombobox.putUserData(VERSIONS_LIST_KEY, versionModel.originObject)
      versionModel.originObject to null
    }

    val listener = KafkaEditorUtils.KafkaDataModelListener(versionCombobox, onListUpdate) {
      val newVersions = versionModel.originObject
      versionCombobox.putUserData(VERSIONS_LIST_KEY, newVersions)
      isVisible.set(!versionModel.originObject.isNullOrEmpty())
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