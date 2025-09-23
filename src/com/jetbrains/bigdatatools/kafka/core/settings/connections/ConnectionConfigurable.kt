package io.confluent.kafka.core.settings.connections

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import io.confluent.kafka.core.rfs.driver.SafeExecutor
import io.confluent.kafka.core.rfs.settings.RfsConnectionTestingBase
import io.confluent.kafka.core.settings.ConnectionSettingsPanel
import io.confluent.kafka.core.settings.defaultui.DefaultConnectionSettingsPanel
import io.confluent.kafka.core.settings.defaultui.SettingsPanelCustomizer
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager
import io.confluent.kafka.core.ui.doOnChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.swing.Icon

abstract class ConnectionConfigurable<D : ConnectionData, C: SettingsPanelCustomizer<D>>(
  protected val connectionData: D,
  protected val project: Project,
  private val iconUnexpanded: Icon? = null,
  private val iconExpanded: Icon? = iconUnexpanded
) : NamedConfigurable<ConnectionData>(false, null), UserDataHolder {

  protected val disposable = Disposer.newDisposable()

  protected lateinit var coroutineScope: CoroutineScope
    private set

  var component: DefaultConnectionSettingsPanel<D>? = null
    private set

  protected lateinit var settingsCustomizer: C
    private set

  val innerId: String
    get() = connectionData.innerId

  override fun disposeUIResources() {
    super.disposeUIResources()
    Disposer.dispose(disposable)
  }

  //region UserDataHolder
  private val userData = UserDataHolderBase()
  override fun <T> getUserData(key: Key<T>): T? = userData.getUserData(key)
  override fun <T> putUserData(key: Key<T>, value: T?) = userData.putUserData(key, value)
  //endregion UserDataHolder

  override fun getBannerSlogan(): String = "${connectionData.name}[${connectionData.uri}]"
  override fun isModified(): Boolean = component?.isModified() ?: false

  override fun getDisplayName(): String {
    // Special half-hack to change connection node simultaneously in tree and in panel.
    // We need this because the default mechanic of NamedConfigurable will not work in our case.
    return component?.getNameField()?.text ?: connectionData.name
  }

  override fun getEditableObject(): ConnectionData = connectionData
  override fun getIcon(expanded: Boolean): Icon? = if (expanded) iconExpanded else iconUnexpanded

  override fun apply() {
    val changedFields = checkNotNull(component).apply()
    RfsConnectionDataManager.instance?.modifyConnection(project, connectionData, changedFields)
  }

  override fun cancel() {
    super.cancel()
  }

  // Actually will newer be called because NamedConfigurable mechanic will not work in our case.
  override fun setDisplayName(name: String?) {
    name?.let { connectionData.name = it }
  }

  override fun createOptionsPanel(): DefaultConnectionSettingsPanel<D> {
    coroutineScope = SafeExecutor.createInstance(disposable).coroutineScope.plus(ModalityState.any().asContextElement())
    settingsCustomizer = createSettingsCustomizer()
    val connectionSettingsPanel = getUserData(ConnectionSettingsPanel.CONNECTION_SETTINGS_PANEL_KEY)

    connectionSettingsPanel?.let {
      settingsCustomizer.putUserData(ConnectionSettingsPanel.CONNECTION_SETTINGS_PANEL_KEY, it)
    }

    val panel = DefaultConnectionSettingsPanel(project, connectionData, settingsCustomizer, createConnectionTesting(), disposable, coroutineScope)
    panel.getNameField()?.doOnChange {
      connectionSettingsPanel?.nodeChanged(innerId)
    }
    component = panel
    return panel
  }

  protected open fun createConnectionTesting(): ConnectionTesting<D>? = RfsConnectionTestingBase(project, settingsCustomizer)

  protected abstract fun createSettingsCustomizer(): C

  open fun isInitWithWizard(): Boolean = false
}