package io.confluent.kafka.core.rfs.editorviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.layout.migLayout.createLayoutConstraints
import com.intellij.ui.layout.migLayout.patched.MigLayout
import com.intellij.util.ui.JBUI
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.fileinfo.ErrorResult
import io.confluent.kafka.core.rfs.driver.fileinfo.SafeResult
import io.confluent.kafka.core.rfs.driver.metainfo.details.getComponent
import io.confluent.kafka.core.rfs.fileInfo.DriverRfsListener
import io.confluent.kafka.core.rfs.fileInfo.RfsFileInfoChildren
import io.confluent.kafka.core.rfs.projectview.actions.RfsActionPlaces
import io.confluent.kafka.core.rfs.search.impl.BackListElement
import io.confluent.kafka.core.rfs.search.impl.MoreListElement
import io.confluent.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.kafka.core.ui.setCenterComponent
import io.confluent.kafka.core.util.ToolbarUtils
import io.confluent.kafka.core.util.executeOnPooledThread
import io.confluent.kafka.core.util.invokeLater
import io.confluent.kafka.util.KafkaMessagesBundle
import net.miginfocom.layout.ConstraintParser
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

class RfsTableViewerEditor(val project: Project, val virtualFile: VirtualFile) : FileEditor, UserDataHolderBase() {
  private val driver = virtualFile.getUserData(RfsViewerEditorProvider.RFS_EDITOR_VIEWER_DRIVER) ?: error(
    "Cannot find Driver for file ${virtualFile.path}")

  private val path = virtualFile.getUserData(RfsViewerEditorProvider.RFS_EDITOR_VIEWER_PATH) ?: error(
    "Cannot find Rfs Path for file ${virtualFile.path}")

  private val searchField = SearchTextField(false, false, null)
  private val viewer = RfsTableViewer(project, driver, path, searchField).apply {
    Disposer.register(this@RfsTableViewerEditor, this)
  }

  private val component: JPanel = object : JPanel(BorderLayout()), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[PlatformDataKeys.HELP_ID] = "big.data.tools.file.viewer"
    }
  }

  private val navBar = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
    border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
  }

  private val splitter = OnePixelSplitter(false, 0.8f)

  private val metaInfoProvider = driver.getMetaInfoProvider()
  private var curWindowDisposable = Disposer.newDisposable()

  private val showErrorPanel = AtomicBoolean(false)

  private val driverListener = object : DriverRfsListener {
    override fun nodeUpdated(path: RfsPath) {
      val selectedPath = getSelectedNode()?.rfsPath
      if (selectedPath == path) {
        invokeLater {
          updateDetails()
        }
      }
    }

    override fun fileInfoLoaded(path: RfsPath, fileInfo: SafeResult<FileInfo?>) {
      nodeUpdated(path)
    }

    override fun childrenLoaded(path: RfsPath, children: SafeResult<RfsFileInfoChildren>) {
      val selectedPath = getSelectedNode()?.rfsPath
      if (selectedPath?.startsWith(path) == true) {
        invokeLater {
          updateDetails()
        }
      }
      if (this@RfsTableViewerEditor.path == path) {
        val exception = (children as? ErrorResult)?.exception
        invokeLater {
          updateMainPanel(exception)
        }
      }
    }

    override fun treeUpdated(path: RfsPath) = executeOnPooledThread {
      val selectedPath = getSelectedNode()?.rfsPath
      if (selectedPath?.startsWith(path) == true) invokeLater {
        updateDetails()
      }

      if (this@RfsTableViewerEditor.path.startsWith(path) || this@RfsTableViewerEditor.path == path) {
        detectAndUpdateStateOfPanel()
      }
    }
  }

  private fun updateNavBar(path: RfsPath) {
    navBar.removeAll()

    var previousPath: RfsPath? = path
    for (i in 0 until path.elements.size) {
      val localPath = previousPath ?: break
      navBar.add(RfsTableViewerNavBarItem(localPath.name, first = i == path.elements.size - 1).apply {
        addActionListener {
          viewer.openPath(localPath)
        }
      }, 0)
      previousPath = localPath.parent
    }

    navBar.repaint()
  }

  private fun detectAndUpdateStateOfPanel() {
    val fileInfoStatus = driver.getFileStatus(this@RfsTableViewerEditor.path) as? ErrorResult
    val childrenStatus = driver.listStatus(this@RfsTableViewerEditor.path) as? ErrorResult
    val exception = fileInfoStatus?.exception ?: childrenStatus?.exception
    updateMainPanel(exception)
  }

  init {
    initSplitter()
    setNormalView()
    driver.addListener(driverListener)
    viewer.addListener { updateNavBar(it) }
    updateNavBar(path)
  }

  override fun dispose() {
    driver.removeListener(driverListener)
    Disposer.dispose(curWindowDisposable)
  }

  override fun getFile() = virtualFile

  override fun getComponent(): JComponent = component
  override fun getPreferredFocusedComponent(): JComponent = viewer.getComponent()
  override fun getName(): String = path.stringRepresentation()

  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getCurrentLocation(): FileEditorLocation? = null

  private fun updateMainPanel(exception: Throwable?) = invokeAndWaitIfNeeded {
    if (exception == null == !showErrorPanel.get()) return@invokeAndWaitIfNeeded
    component.removeAll()
    if (exception == null) {
      showErrorPanel.set(false)
      setNormalView()
    }
    else {
      showErrorPanel.set(true)
      setErrorPanel(exception)
    }
  }

  private fun setErrorPanel(exception: Throwable) = invokeAndWaitIfNeeded {
    val errorPanel = RfsEditorErrorPanel(exception, this, driver)
    component.add(JPanel(GridBagLayout()).apply {
      val constraints = GridBagConstraints().apply {
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        gridwidth = GridBagConstraints.REMAINDER
      }
      add(navBar, constraints)
      add(createToolbar(errorPanel), constraints)
    }, BorderLayout.NORTH)
    component.add(errorPanel, BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
  }

  private fun setNormalView() = invokeAndWaitIfNeeded {
    component.add(JPanel(GridBagLayout()).apply {
      val constraints = GridBagConstraints().apply {
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        gridwidth = GridBagConstraints.REMAINDER
      }
      add(navBar, constraints)
      add(createToolbar(viewer.getComponent()), constraints)
    }, BorderLayout.NORTH)
    component.add(splitter, BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
  }

  private fun initSplitter() {
    viewer.table.selectionModel.addListSelectionListener { event ->
      if (event.valueIsAdjusting) {
        return@addListSelectionListener
      }
      updateDetails()
    }
    splitter.firstComponent = viewer.getComponent()
    updateDetails()
  }

  private fun createToolbar(targetComponent: JComponent): JPanel {
    val actionGroup = DefaultActionGroup().apply {
      add(RfsColumnVisibility.createAction(driver, viewer.table))
      addSeparator()
      add(ActionManager.getInstance().getAction("BigDataTools.RfsViewer.Toolbar.RfsActionGroup"))
    }

    val leftToolbar = ToolbarUtils.createActionToolbar(RfsActionPlaces.RFS_VIEWER_EDITOR, actionGroup, true).apply {
      this.targetComponent = targetComponent
    }

    val detailsToggle = object : DumbAwareToggleAction(KafkaMessagesBundle.message("file.viewer.toggle.details"), null,
                                                       AllIcons.Actions.PreviewDetails) {
      override fun isSelected(e: AnActionEvent) = RfsFileViewerSettings.getInstance().showDetailsPanel
      override fun getActionUpdateThread() = ActionUpdateThread.BGT
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        RfsFileViewerSettings.getInstance().showDetailsPanel = !RfsFileViewerSettings.getInstance().showDetailsPanel
        updateDetails()
      }
    }

    val rightToolbar = ToolbarUtils.createActionToolbar(targetComponent = targetComponent, RfsActionPlaces.RFS_VIEWER_EDITOR,
                                                        DefaultActionGroup(detailsToggle), true).apply {
      layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY // For removing empty space on the right.
    }

    val toolbarPanel = JPanel(
      MigLayout(createLayoutConstraints(0, 0).noVisualPadding().fill(), ConstraintParser.parseColumnConstraints("[grow][pref!]"))).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)

      val leftPanel = JPanel(MigLayout(createLayoutConstraints(0, 0).noVisualPadding().fill()))
      leftPanel.add(searchField)
      leftPanel.add(leftToolbar.component)

      add(leftPanel)
      add(rightToolbar.component)
    }
    return toolbarPanel
  }

  private var infoPanel: JPanel? = null

  private fun createInfoPanel() = JPanel(BorderLayout()).apply {
    val title = JLabel(KafkaMessagesBundle.message("file.viewer.details.title")).apply {
      border = BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM), JBUI.Borders.emptyLeft(5))
    }

    add(title, BorderLayout.NORTH)
    add(JLabel(KafkaMessagesBundle.message("file.viewer.details.empty"), SwingConstants.CENTER))
  }

  private fun updateDetails() {
    if (!RfsFileViewerSettings.getInstance().showDetailsPanel) {
      splitter.secondComponent = null
      infoPanel = null
      return
    }

    val node = getSelectedNode()
    if (node == null || node is MoreListElement || node is BackListElement) {
      infoPanel = createInfoPanel()
      splitter.secondComponent = infoPanel
      return
    }

    Disposer.dispose(curWindowDisposable)
    curWindowDisposable = Disposer.newDisposable()
    Disposer.register(this@RfsTableViewerEditor, curWindowDisposable)

    if (infoPanel == null) {
      infoPanel = createInfoPanel()
      splitter.secondComponent = infoPanel
    }

    infoPanel?.apply {
      val fileInfo = metaInfoProvider.getFileDetails(DriverFileRfsTreeNode(project, node.rfsPath, driver).apply {
        fileInfo = node.fileInfo
      }, curWindowDisposable).getComponent()
      setCenterComponent(ScrollPaneFactory.createScrollPane(fileInfo, true))
      revalidate()
      repaint()
    }
  }

  private fun getSelectedNode() = viewer.getSelectedNode()
}