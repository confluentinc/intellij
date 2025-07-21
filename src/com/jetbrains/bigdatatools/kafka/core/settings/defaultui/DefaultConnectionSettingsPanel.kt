package com.jetbrains.bigdatatools.kafka.core.settings.defaultui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.depend.MasterConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.*
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData.Companion.TEST_SUFFIX
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionSettingProviderEP
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionTesting
import com.jetbrains.bigdatatools.kafka.core.settings.fields.*
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.core.ui.MigPanel
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.text.JTextComponent

class DefaultConnectionSettingsPanel<D : ConnectionData>(
  project: Project,
  private val conn: D,
  private val settingsCustomizer: SettingsPanelCustomizer<D>?,
  connTesting: ConnectionTesting<D>?,
  private val validatorDisposable: Disposable,
  private val coroutineScope: CoroutineScope
) : JPanel(BorderLayout()) {

  private var wrappedComponents = mutableListOf<WrappedComponent<in D>>()

  private val enabledCheckbox = JCheckBox(KafkaMessagesBundle.message("connection.enable"), conn.isEnabled).also {
    it.addItemListener { _ ->
    }
  }

  val perProjectCheckbox = JCheckBox(KafkaMessagesBundle.message("connection.per.project"), conn.isPerProject).also {
    it.isEnabled = conn.sourceConnection == null && (conn !is MasterConnectionData<*> || conn.getSlaveConnections().isEmpty())
    if (!it.isEnabled)
      it.toolTipText = KafkaMessagesBundle.message("settings.per.project.tooltip.is.depend")

    it.addItemListener { _ ->
    }
  }

  private fun createDefaultFields(): MutableList<WrappedComponent<in D>> {
    val credentialsHolder = CredentialsHolder(conn, null, validatorDisposable, coroutineScope)
    @Suppress("DEPRECATION")
    return mutableListOf(
      StringNamedField(ConnectionData::name, CommonSettingsKeys.NAME_KEY, conn),
      StringNamedField(ConnectionData::uri, CommonSettingsKeys.URL_KEY, conn).withNotEmptyValidator(validatorDisposable),
      NullableIntNamedField(ConnectionData::port, CommonSettingsKeys.PORT_KEY, conn).withNumberValidator(validatorDisposable),
      UsernameNamedField(CommonSettingsKeys.LOGIN_KEY, credentialsHolder),
      PasswordNamedField(CommonSettingsKeys.PASS_KEY, credentialsHolder)
    )
  }

  init {
    border = BorderFactory.createEmptyBorder()
    val panel = JPanel(MigLayout(UiUtil.insets0FillXHidemode3))
    panel.border = JBUI.Borders.empty(SettingsPanelCustomizer.BORDER_WIDTH)

    val checkBoxPanel = JPanel(BorderLayout()).apply {
      add(enabledCheckbox, BorderLayout.LINE_START)

      if (ApplicationInfo.getInstance().versionName != "DataSpell") {
        add(perProjectCheckbox, BorderLayout.LINE_END)
      }
    }

    panel.add(checkBoxPanel, UiUtil.pushXGrowXWrap)

    val scrollPane = ScrollPaneFactory.createScrollPane(panel, true)

    add(scrollPane, BorderLayout.CENTER)

    var defaultComponent: JComponent? = null

    if (settingsCustomizer != null) {
      settingsCustomizer.init(conn)
      wrappedComponents.addAll(settingsCustomizer.getDefaultFields())
      defaultComponent = settingsCustomizer.getDefaultComponent(wrappedComponents, conn)?.let {
        MigPanel().apply {
          val masterConnectionId = conn.sourceConnection
          if (masterConnectionId != null) {
            val connection = RfsConnectionDataManager.instance?.getConnectionById(project = project, connId = masterConnectionId)
            val masterName = connection?.name
            masterName?.let {
              row(KafkaMessagesBundle.message("notification.connection.is.managed.by.driver", masterName))
            }
          }
          this.block(it)
        }
      }
    }
    else {
      wrappedComponents = createDefaultFields()
    }

    if (defaultComponent == null) {
      defaultComponent = MigPanel(LC().insets("0").fill().hideMode(3))
      val masterConnectionId = conn.sourceConnection
      if (masterConnectionId != null) {
        val connection = RfsConnectionDataManager.instance?.getConnectionById(project = project, connId = masterConnectionId)
        val masterName = connection?.name
        masterName?.let {
          defaultComponent.row(JLabel(KafkaMessagesBundle.message("notification.connection.is.managed.by.driver", masterName)))
        }
      }
      wrappedComponents.forEach { addWrapperComponent(defaultComponent, it) }
    }

    panel.add(defaultComponent, UiUtil.pushXGrowXWrap)

    if (connTesting != null) {
      panel.add(
        TestConnectionPanelWrapper(connTesting, wrappedComponents, this::createTestConnection, validatorDisposable, coroutineScope).getMainComponent(),
        UiUtil.spanXWrap)
    }

    if (settingsCustomizer != null) {
      val additionalFields = settingsCustomizer.getAdditionalFields()
      wrappedComponents.addAll(additionalFields)

      var additionalComponent = settingsCustomizer.getAdditionalComponent(conn)

      if (additionalComponent == null && additionalFields.isNotEmpty()) {
        additionalComponent = MigPanel()
        additionalFields.forEach { addWrapperComponent(additionalComponent, it) }
      }

      additionalComponent?.let { panel.add(it, UiUtil.pushXGrowXWrap) }
    }

    // Here can be any content, for example "Missing driver" message.
    val bottomComponent = settingsCustomizer?.getBottomComponent()
    if (bottomComponent != null) {
      add(JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(bottomComponent)
      }, BorderLayout.SOUTH)
    }

    wrappedComponents.forEach { it.addIsPerProjectListeners(perProjectCheckbox) }
  }

  fun getNameField(): JTextComponent? {
    return (wrappedComponents.find { it.key == CommonSettingsKeys.NAME_KEY } as? WrappedNamedField<*, *>)?.getTextComponent()
  }

  fun isModified(): Boolean =
    enabledCheckbox.isSelected != conn.isEnabled ||
    perProjectCheckbox.isSelected != conn.isPerProject ||
    wrappedComponents.any { it.isModified(conn) } ||
    (settingsCustomizer?.isModified(conn) ?: false)

  fun apply(): List<ModificationKey> {
    validateConfig()

    val allModified = mutableListOf<ModificationKey>()

    val modifiedCommon = wrappedComponents.filter { it.isModified(conn) }.map {
      it.apply(conn)
      it.key
    }
    allModified.addAll(modifiedCommon)

    if (conn.isEnabled != enabledCheckbox.isSelected) {
      conn.isEnabled = enabledCheckbox.isSelected
      allModified.add(CommonSettingsKeys.ENABLED_KEY)
    }

    val modifiedCustom = settingsCustomizer?.apply(conn) ?: emptyList()

    allModified.addAll(modifiedCustom)

    if (conn.isPerProject != perProjectCheckbox.isSelected) {
      conn.isPerProject = perProjectCheckbox.isSelected
      allModified.add(CommonSettingsKeys.IS_GLOBAL_KEY)
    }

    return allModified
  }

  /**
   * Validate the configuration form
   * @throws ConfigurationException with error message
   */
  fun validateConfig() {
    // BDIDE-4063 Mandatory field only is 'Name'
    val validationErrors = getValidationErrors(wrappedComponents.filter { it.key == CommonSettingsKeys.NAME_KEY })

    if (validationErrors.isNotEmpty()) {
      val configName = conn.name.ifBlank { KafkaMessagesBundle.message("validator.emptyConfigName", conn.groupId) }

      @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
      throw ConfigurationException(validationErrors.joinToString(System.lineSeparator()) { it.message },
                                   KafkaMessagesBundle.message("validator.formHasErrors", configName))
    }
  }

  @RequiresEdt
  fun createTestConnection(): D {
    val nc = ConnectionSettingProviderEP.getConnectionFactories()
      .find { it.id == conn.groupId }
      ?.let {
        @Suppress("UNCHECKED_CAST")
        val testConnData = it.createBlankData(forcedId = conn.innerId + TEST_SUFFIX) as D?
        testConnData?.copyFrom(conn)
        testConnData?.innerId = conn.innerId + TEST_SUFFIX
        testConnData
      }

    if (nc == null) return conn
    nc.isPerProject = perProjectCheckbox.isSelected
    wrappedComponents.forEach { it.apply(nc) }
    settingsCustomizer?.apply(nc)

    return nc
  }

  companion object {

    private val logger = Logger.getInstance(this::class.java)

    init {
      // Hack. Probably fixed exception "cannot access a member of class com.intellij.openapi.ui.ComponentValidator with modifiers "private" "
      try {
        ComponentValidator::class.java.getDeclaredField("validationInfo").apply { isAccessible = true }
      }
      catch (e: Exception) {
        logger.warn(e)
      }
    }

    fun <D : ConnectionData> addWrapperComponent(panel: MigPanel, wrapperComponent: WrappedComponent<in D>) {
      if (wrapperComponent.isGhost) return

      if (wrapperComponent is WrappedNamedComponent) {
        panel.row(wrapperComponent)
      }
      else {
        panel.row(wrapperComponent.getComponent())
      }
    }
  }
}