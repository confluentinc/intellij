package io.confluent.intellijplugin.core.settings.defaultui

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.fields.CheckBoxField
import io.confluent.intellijplugin.core.settings.fields.WrappedComponent
import io.confluent.intellijplugin.core.settings.getValidationErrors
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * User: Dmitry.Naydanov
 * Date: 2019-04-18.
 */
abstract class SettingsPanelCustomizer<D : ConnectionData> : UserDataHolder {

  /** Offset and padding settings for connection settings UI*/
  companion object {
    const val BORDER_WIDTH = 10

    fun setupLoginPasswordAnonymous(login: JComponent, password: JComponent, anonymous: CheckBoxField<ConnectionData>) {
      fun onAnonymousCheckBoxChanged() {
        login.isEnabled = !anonymous.getValue()
        password.isEnabled = !anonymous.getValue()
      }
      anonymous.checkBoxField.isFocusable = false
      anonymous.checkBoxField.addItemListener { onAnonymousCheckBoxChanged() }
      onAnonymousCheckBoxChanged()
    }
  }

  //region UserDataHolder
  private val userData = UserDataHolderBase()
  override fun <T> getUserData(key: Key<T>): T? = userData.getUserData(key)
  override fun <T> putUserData(key: Key<T>, value: T?) = userData.putUserData(key, value)
  //endregion UserDataHolder

  /**
   * Default component automatically created basing on list of WrappedComponent processed
   * by customizeDefaultFields. (Typically we have there Name / Url / Port / Login / Password fields)
   * We can override view, layout and default behaviour of this component by overriding this method.
   */
  open fun getDefaultComponent(fields: List<WrappedComponent<in D>>, conn: D): JPanel? = null

  abstract fun getDefaultFields(): List<WrappedComponent<in D>>

  /**
   * This component will be added after default block in the collapsible section "Additional".
   * This method is called once, on creation of settings panel, so there is no need to cache this component.
   */
  open fun getAdditionalComponent(conn: D): JComponent? = null

  open fun getAdditionalFields(): List<WrappedComponent<in D>> = mutableListOf()

  /**
   * This component will be added to the bottom of "Settings panel" and will be always visible (placed no on scroll)
   */
  open fun getBottomComponent(): JComponent? = null

  open fun isModified(conn: D): Boolean = false
  open fun apply(conn: D): List<ModificationKey> = emptyList()

  open fun init(conn: D) {
    getDefaultFields().forEach { it.init(conn) }
    getAdditionalFields().forEach { it.init(conn) }
  }

  open fun getValidationErrors() = getValidationErrors(getDefaultFields() + getAdditionalFields())

  @RequiresEdt
  open fun onTestConnectionFinish(createdDriver: Driver?, success: Boolean) {}

  open fun onTestConnectionStart() {}
}