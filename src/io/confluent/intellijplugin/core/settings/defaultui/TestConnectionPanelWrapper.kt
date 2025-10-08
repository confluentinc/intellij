package io.confluent.intellijplugin.core.settings.defaultui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.StatusText
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.use
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionTesting
import io.confluent.intellijplugin.core.settings.connections.ConnectionTestingSession
import io.confluent.intellijplugin.core.settings.fields.WrappedComponent
import io.confluent.intellijplugin.core.settings.isValidateable
import io.confluent.intellijplugin.core.ui.*
import io.confluent.intellijplugin.core.util.async.JobHandle
import io.confluent.intellijplugin.core.util.toPresentableText
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import kotlin.math.max
import kotlin.math.min

class TestConnectionPanelWrapper<D : ConnectionData>(
    private val connectionTesting: ConnectionTesting<D>,
    private val wrappedComponents: List<WrappedComponent<*>>,
    private val connProvider: () -> D,
    private val validatorDisposable: Disposable,
    coroutineScope: CoroutineScope
) {
    private companion object {
        const val MAX_SHORT_DESC_LENGTH = 45
    }

    private val component = JPanel(BorderLayout(10, 0))

    private val statusIndicator = StatusIndicator(EmptyStatus)

    val testingProcess: JobHandle = JobHandle(coroutineScope)

    private val testButtonCardLayout = CardLayout()
    private val testButton = JButton(KafkaMessagesBundle.message("testConnection.button"))
    private val cancelButton = JButton(KafkaMessagesBundle.message("testConnection.button.cancel"))
    private val testButtonPanel = JPanel(testButtonCardLayout)
    private val testCardId = "test"
    private val cancelCardId = "cancel"

    private fun updateStatusFinished(connectionStatus: ConnectionStatus, statisticStatus: TestConnectionResult) {
        if (statusIndicator.isVisible()) {
            statusIndicator.updateStatus(connectionStatus)
            testButtonCardLayout.show(testButtonPanel, testCardId)
            testButton.requestFocus()
        }
    }

    init {
        testButtonPanel.add(testButton, testCardId)
        testButtonPanel.add(cancelButton, cancelCardId)
        testButton.addActionListener {
            statusIndicator.updateStatus(TestingInProcess)
            testButtonCardLayout.show(testButtonPanel, cancelCardId)
            cancelButton.requestFocus()
            testingProcess.cancelAndLaunch {
                try {
                    runTesting()
                } catch (_: CancellationException) {
                    withContext(NonCancellable + Dispatchers.EDT) {
                        updateStatusFinished(EmptyStatus, TestConnectionResult.CANCEL)
                    }
                } catch (e: Throwable) {
                    thisLogger().error(e)
                    withContext(Dispatchers.EDT) {
                        updateStatusFinished(ConnectionError(e), TestConnectionResult.ERROR)
                    }
                }
            }
        }
        cancelButton.addActionListener {
            testingProcess.job?.cancel("Cancelled by user")
        }

        testButtonCardLayout.show(testButtonPanel, testCardId)
        component.add(testButtonPanel, BorderLayout.WEST)
        statusIndicator.addControls(component)
    }

    fun getMainComponent(): JComponent = component

    private suspend fun runTesting() {
        val hasErrors = withContext(Dispatchers.EDT) {
            // If form filled incorrectly, before connect we will try to validate and show results.
            val found = wrappedComponents.flatMap { wrappedComponent ->
                val component = wrappedComponent.getComponent()

                if (!isValidateable(component)) return@flatMap emptyList<Pair<WrappedComponent<*>, ValidationInfo>>()
                val validators = wrappedComponent.getValidators()
                validators.mapNotNull { validator ->
                    validator.enableValidation()
                    validator.revalidate()
                    val validationInfo = validator.validationInfo
                    if (validationInfo == null || validationInfo.warning) null else wrappedComponent to validationInfo
                }
            }
            if (found.isNotEmpty()) {
                val error = ConnectionError(
                    null,
                    shortDescription = KafkaMessagesBundle.message("connection.invalid"),
                    additionalErrorDescription = found.joinToString("\n") { it.first.key.label + " " + it.second.message })

                updateStatusFinished(error, TestConnectionResult.VALIDATION_ERROR)
            }
            found.isNotEmpty()
        }
        if (hasErrors) return

        val modalityState = checkNotNull(currentCoroutineContext().contextModality())
        val testConnectionData = withContext(Dispatchers.EDT) {
            connProvider()
        }
        Disposer.newDisposable().use { testDisposable ->
            val session = ConnectionTestingSession(
                testConnectionData = testConnectionData,
                testDisposable = testDisposable,
                dialogDisposable = validatorDisposable,
                modalityState = modalityState,
                updateStatusIndicator = { status ->
                    val statisticStatus = if (status is ConnectionSuccessful) {
                        TestConnectionResult.SUCCESS
                    } else {
                        TestConnectionResult.ERROR
                    }
                    withContext(Dispatchers.EDT) {
                        updateStatusFinished(status, statisticStatus)
                        status.error?.let { thisLogger().info("Test connection fail", it) }
                    }
                },
                runAgainByExternalEvent = {
                    testingProcess.cancelAndLaunch {
                        withContext(Dispatchers.EDT) {
                            if (statusIndicator.isVisible()) {
                                statusIndicator.updateStatus(ConnectionStatusRunAgain)
                                AppIcon.getInstance().requestFocus(SwingUtilities.getWindowAncestor(component))
                            }
                        }
                        runTesting()
                    }
                })
            with(connectionTesting) {
                session.validateAndTest()
            }
        }
    }

    private class StatusIndicator(private var currentStatus: ConnectionStatus) {

        private val statusLabel = JLabel()
        private val detailsLabel = ActionLink(KafkaMessagesBundle.message("testConnection.details"), null)

        init {
            detailsLabel.addActionListener {
                showDetailsPopup()
            }

            updateStatus(currentStatus)
        }

        private fun showDetailsPopup() {
            var detailedErrorMessage =
                currentStatus.error?.toPresentableText() ?: currentStatus.additionalErrorDescription ?: ""
            detailedErrorMessage = detailedErrorMessage
                .replace("\n", "<br>")
                .replace("\\\"", "\"")
                .replace("<html>", "")
                .replace("</html>", "")
                .replace("<body>", "")
                .replace("</body>", "")

            val errorText = StringUtil.trimTrailing(StringUtil.trimLog(detailedErrorMessage, 500))

            var showFullError = false

            val preferredWidth = min(500, Toolkit.getDefaultToolkit().screenSize.width / 3)

            lateinit var popup: JBPopup
            lateinit var textCell: JEditorPane
            lateinit var link: ActionLink

            val copyAction = DumbAwareAction.create(CommonBundle.message("button.copy"), AllIcons.Actions.Copy) {
                CopyPasteManager.getInstance().setContents(
                    StringSelection(detailedErrorMessage + currentStatus.error?.stackTraceToString())
                )
            }.createButton()

            val panel = JPanel(BorderLayout()).apply {

                val topPanel = JPanel(BorderLayout()).apply {
                    // There is some magic within DSLLabel to show html and make links clickable
                    val dslPanel = panel {
                        row {
                            textCell = text(errorText).align(AlignX.FILL).resizableColumn().component.apply {
                                isFocusable = true
                                setSize(preferredWidth, 10)
                            }
                        }.resizableRow()
                    }

                    setCenterComponent(dslPanel)

                    val copyPanel = JPanel(BorderLayout()).apply {
                        setNorthComponent(copyAction)
                    }
                    setLineEndComponent(copyPanel)
                }

                if (errorText.length < detailedErrorMessage.length) {
                    link = ActionLink(KafkaMessagesBundle.message("testConnection.show.more")) {
                        showFullError = !showFullError
                        textCell.text = if (showFullError) detailedErrorMessage else errorText
                        textCell.setSize(popup.content.width - 20 - copyAction.width, 10)

                        link.text = if (showFullError) KafkaMessagesBundle.message("testConnection.show.less")
                        else KafkaMessagesBundle.message("testConnection.show.more")
                        popup.pack(false, true)
                    }

                    val interPanel = JPanel(null).apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(topPanel)
                        add(JPanel(BorderLayout()).apply { setLineStartComponent(link) })
                    }
                    setNorthComponent(interPanel)
                } else {
                    setNorthComponent(topPanel)
                }

                val throwable: Throwable? = currentStatus.error
                if (throwable != null) {
                    val stackScrollPane = ScrollPaneFactory.createScrollPane(JTextArea().apply {
                        lineWrap = false
                        text = throwable.stackTraceToString()
                        caretPosition = 0
                    }, true)

                    stackScrollPane.preferredSize = Dimension(
                        stackScrollPane.preferredSize.width,
                        max(300, Toolkit.getDefaultToolkit().screenSize.height / 5)
                    )

                    val group = HideableTitledPanel(
                        KafkaMessagesBundle.message("testConnection.stacktrace"),
                        true,
                        stackScrollPane,
                        false
                    )
                    setCenterComponent(group)
                }
            }.apply {
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            }

            popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null)
                .setRequestFocus(true)
                .setModalContext(false)
                .setCancelKeyEnabled(true)
                .setCancelOnClickOutside(true)
                .setLocateWithinScreenBounds(true)
                .setResizable(true)
                .setFocusable(true)
                .createPopup()

            popup.showUnderneathOf(detailsLabel)
        }

        fun isVisible() = statusLabel.isVisible

        fun addControls(parent: JPanel) {
            parent.add(statusLabel, BorderLayout.CENTER)
            parent.add(detailsLabel, BorderLayout.EAST)
        }

        fun updateStatus(status: ConnectionStatus) {
            currentStatus = status

            statusLabel.foreground = status.color
            statusLabel.text = StringUtil.first(status.shortDescription, MAX_SHORT_DESC_LENGTH, true)

            statusLabel.icon = status.icon
            detailsLabel.isVisible = status.error != null || status.additionalErrorDescription != null
        }
    }
}

interface ConnectionStatus {
    val color: Color

    @get:StatusText
    val shortDescription: String
    val error: Throwable?
    val additionalErrorDescription: String?
    val icon: Icon?
}

data class ConnectionSuccessful(override val shortDescription: String = KafkaMessagesBundle.message("connection.success")) :
    ConnectionStatus {
    override val error: Throwable? = null
    override val color: Color = if (UIUtil.isUnderDarcula()) JBColor.GREEN else JBColor.GREEN.darker().darker()
    override val additionalErrorDescription: String? = null
    override val icon: Icon = AllIcons.General.InspectionsOK
}

data class ConnectionWarning(
    override val shortDescription: String,
    override val additionalErrorDescription: String? = null,
    override val error: Throwable? = null,
) : ConnectionStatus {
    override val color: Color = MessageType.ERROR.titleForeground

    override val icon: Icon = AllIcons.General.NotificationWarning
}

data class ConnectionError(
    override val error: Throwable?,
    override val shortDescription: String = KafkaMessagesBundle.message("connection.error"),
    override val additionalErrorDescription: String? = null
) : ConnectionStatus {
    override val color: Color = MessageType.ERROR.titleForeground

    override val icon: Icon = AllIcons.General.NotificationError
}

object ConnectionStatusRunAgain : ConnectionStatus {
    override val color: Color get() = JBColor.GRAY
    override val additionalErrorDescription: String? = null
    override val error: Throwable? = null
    override val shortDescription: String = KafkaMessagesBundle.message("connection.runAgain")
    override val icon: Icon get() = AnimatedIcon.Default()
}

object TestingInProcess : ConnectionStatus {
    override val color: Color = JBColor.GRAY
    override val additionalErrorDescription: String? = null
    override val error: Throwable? = null
    override val shortDescription: String = KafkaMessagesBundle.message("connection.connecting")

    override val icon: Icon = AnimatedIcon.Default()
}

object EmptyStatus : ConnectionStatus {
    override val color: Color = JBColor.GRAY
    override val additionalErrorDescription: String? = null
    override val shortDescription: String = ""
    override val error: Throwable? = null

    override val icon: Icon? = null
}

enum class TestConnectionResult {
    SUCCESS,
    VALIDATION_ERROR,
    ERROR,
    CANCEL
}