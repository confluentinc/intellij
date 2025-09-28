package io.confluent.intellijplugin.core.rfs.copypaste.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(name = "BigDataToolsRfsCopySettings", storages = [Storage("BigDataToolsRfsCopySettings.xml")])
class RfsCopySettings : PersistentStateComponent<RfsCopySettings> {
  var ignoreConfirmationCopyMove = mutableListOf<String>()

  override fun getState() = this

  override fun loadState(state: RfsCopySettings) = try {
    XmlSerializerUtil.copyBean(state, this)
  }
  catch (ignore: Exception) {

  }

  companion object {
    fun getInstance(): RfsCopySettings = service()
  }
}