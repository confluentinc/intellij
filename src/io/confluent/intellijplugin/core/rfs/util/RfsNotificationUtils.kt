package io.confluent.intellijplugin.core.rfs.util

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.ui.HideableTitledPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScreenUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBInsets
import io.confluent.intellijplugin.core.settings.defaultui.UiUtil
import io.confluent.intellijplugin.core.ui.MigPanel
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.core.util.toPresentableText
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import net.miginfocom.layout.CC
import java.awt.Dimension
import java.awt.Toolkit
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.JEditorPane
import javax.swing.JTextArea
import javax.swing.text.DefaultCaret
import kotlin.math.max
import kotlin.math.min

object RfsNotificationUtils {
    private val logger = Logger.getInstance(this::class.java)

    const val ACTION_FAILURE_NOTIFICATION_GROUP = "REMOTE_FS_ACTION"


    private fun notifySuccess(
        @NotificationContent message: String, @NotificationTitle title: String, actions: List<DumbAwareAction>,
        project: Project? = null
    ) = invokeLater {
        val notification = Notification(
            ACTION_FAILURE_NOTIFICATION_GROUP,
            title,
            message,
            NotificationType.INFORMATION
        )
        actions.forEach {
            notification.addAction(it)
        }
        Notifications.Bus.notify(notification, project)
    }

    fun notifySuccess(
        @NotificationContent message: String,
        @NotificationTitle title: String,
        action: DumbAwareAction? = null,
        project: Project? = null
    ) = notifySuccess(message, title, listOfNotNull(action), project)

    // ToDo This looks like a copy of TestConnectionPanelWrapper.showDetailsPopup, we should extract common part.
    fun showExceptionMessage(
        project: Project?,
        e: Throwable,
        @DialogTitle title: String = KafkaMessagesBundle.message(
            "notification.title.remote.fs.error"
        )
    ): Unit = invokeAndWaitIfNeeded {
        logger.info("RFS Error", e)

        val stackEditor = JTextArea().apply {
            lineWrap = false
            text = e.stackTraceToString()
            caretPosition = 0
        }

        val errorEditorPane = JEditorPane().apply {
            editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
            isEditable = false
            isOpaque = false
            margin = JBInsets.emptyInsets()

            (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE

            this.text = "<html>${e.toPresentableText()}</html>"
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        }

        val fontMetrics = stackEditor.getFontMetrics(stackEditor.font)
        val lineHeight = fontMetrics.ascent - fontMetrics.descent

        val bestWidth = max(500, Toolkit.getDefaultToolkit().screenSize.width / 5)

        val stackScrollPane = ScrollPaneFactory.createScrollPane(stackEditor, true).apply {
            preferredSize =
                Dimension(bestWidth, min(JBUIScale.scale(200), max(2 * lineHeight, stackEditor.preferredSize.height)))
        }

        val stackPanel = HideableTitledPanel(
            KafkaMessagesBundle.message("testConnection.stacktrace"),
            true,
            stackScrollPane,
            false
        ).apply {
            components.forEach { it.background = this.background }
        }

        val panel = MigPanel(UiUtil.insets10FillXHidemode3).apply {
            preferredSize = Dimension(bestWidth, preferredSize.height)
            block(errorEditorPane)
            add(stackPanel, CC().grow().push().span())
        }

        DialogBuilder(project).apply {
            addOkAction()
            setCenterPanel(panel)
            setTitle(title)
            show()
        }
    }

    fun showErrorMessage(
        project: Project?,
        @DialogMessage msg: String,
        @DialogTitle title: String = KafkaMessagesBundle.message("notification.title.bdt"),
    ): Unit = invokeAndWaitIfNeeded {
        Messages.showErrorDialog(project, msg, title)
    }


    fun notifyException(
        e: Throwable,
        @NotificationTitle title: String? = null,
        @DialogTitle detailsTitle: String? = null
    ) {
        logger.info("RFS Error", e)
        notify(
            title ?: KafkaMessagesBundle.message("notification.title.remote.fs.error"),
            e.toPresentableText(),
            getStackTrace(e),
            detailsTitle
        )
    }

    private fun showDetailsDialog(
        @DialogTitle detailsTitle: String?,
        @NotificationContent message: String,
        stacktrace: String
    ) {
        val focusedFrame = FocusManagerImpl.getInstance().lastFocusedFrame

        val project = focusedFrame?.project ?: ProjectManager.getInstance().openProjects.firstOrNull() ?: return

        val dialogBuilder = DialogBuilder()

        val detailsConsole = ConsoleViewImpl(project, true).apply {
            Disposer.register(dialogBuilder, this)
            // Call component to create an editor immediately, to set the border and get preferred size a few lines below.
            component
            (editor as EditorImpl).scrollPane.border = IdeBorderFactory.createBorder()
            print(message.replace("<br>", "\n"), ConsoleViewContentType.NORMAL_OUTPUT)
            print("\n\n", ConsoleViewContentType.NORMAL_OUTPUT)
            print(stacktrace, ConsoleViewContentType.ERROR_OUTPUT)
            flushDeferredText()
            scrollTo(0)

            preferredSize = Dimension(
                ScreenUtil.getMainScreenBounds().width / 4, min(
                    ScreenUtil.getMainScreenBounds().height / 3,
                    preferredSize.height
                )
            )
            minimumSize = preferredSize
        }

        dialogBuilder.apply {
            addOkAction()
            setCenterPanel(detailsConsole.component)
            setPreferredFocusComponent(detailsConsole.component)
            setTitle(detailsTitle ?: KafkaMessagesBundle.message("dialog.title.error.stacktrace"))
        }.show()
    }

    private fun notify(
        @NotificationTitle title: String,
        @NotificationContent message: String,
        stacktrace: String,
        @DialogTitle detailsTitle: String? = null
    ) {
        val notification = Notification(
            ACTION_FAILURE_NOTIFICATION_GROUP,
            title,
            message,
            NotificationType.ERROR
        )

        notification.addAction(DumbAwareAction.create(KafkaMessagesBundle.message("error.notification.show.details.text")) {
            showDetailsDialog(detailsTitle, message, stacktrace)
        })

        Notifications.Bus.notify(notification)
    }

    private fun getStackTrace(e: Throwable): String {
        val errors = StringWriter()
        e.printStackTrace(PrintWriter(errors)).toString()
        return errors.toString()
    }
}