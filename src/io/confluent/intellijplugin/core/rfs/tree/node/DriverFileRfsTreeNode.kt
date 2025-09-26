package io.confluent.intellijplugin.core.rfs.tree.node

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import io.confluent.intellijplugin.core.rfs.driver.*
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsViewerEditorProvider
import io.confluent.intellijplugin.core.rfs.exception.RfsAuthRequiredError
import io.confluent.intellijplugin.core.rfs.icons.RfsIcons
import io.confluent.intellijplugin.core.util.SizeUtils
import io.confluent.intellijplugin.core.util.TimeUtils
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.core.util.toPresentableText
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.isPending
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

open class DriverFileRfsTreeNode(project: Project,
                                 val rfsPath: RfsPath,
                                 driver: Driver
) : DriverRfsTreeNode(driver, project), CustomDoubleClickable {
  var fileInfo: FileInfo? = null
  var cachedChildren: List<DriverFileRfsTreeNode>? = null
    private set
  var metaInfoNodes: List<RfsMetaInfoNode>? = null
    private set
  var loadMoreNode: DriverRfsLoadMoreNode?
    get() = (serviceNode as? DriverRfsLoadMoreNode)
    private set(value) {
      serviceNode = value
    }

  var loadingNode: DriverRfsLoadingNode?
    get() = (serviceNode as? DriverRfsLoadingNode).takeIf { this.updatingChildrenTask.get().let { it != null && !it.refresh && !it.isExpired() } }
    private set(value) {
      serviceNode = value
    }

  private var serviceNode: DriverRfsServiceNode? = null

  class UpdatingChildrenTask(val refresh: Boolean = false,
                             private val expires: Duration = 60.seconds) {
    var future: Promise<*>? = null
    val timestamp: Long = System.currentTimeMillis()
    fun isExpired(): Boolean {
      return future.let { it == null || !it.isPending } || (System.currentTimeMillis() - timestamp).milliseconds > expires
    }
  }

  val updatingChildrenTask = AtomicReference<UpdatingChildrenTask?>()

  fun loadChildrenIfNotRunning(loadingNode: DriverRfsLoadingNode? = null, refresh: Boolean = false, body: () -> Promise<*>) {
    if (updatingChildrenTask.get()?.isExpired() == true) updatingChildrenTask.set(null)
    val loadTask = UpdatingChildrenTask(refresh = refresh && !this.cachedChildren.isNullOrEmpty())
    if (updatingChildrenTask.compareAndSet(null, loadTask)) {
      this.loadingNode = loadingNode
      loadTask.future = body()
    }
    this.update()
  }

  internal val isInvalidated = AtomicBoolean(true)
  val isUpdatingChildren: Boolean
    get() = updatingChildrenTask.get()?.isExpired() == false
  override val isLoading
    get() = fileInfo == null && error == null || isUpdatingChildren

  override val isMount
    get() = parent == null

  init {
    myName = rfsPath.name
  }

  open fun getGrayText(): @Nls String? = null
  private fun getNodeIcon(): Icon? = when {
    error is RfsAuthRequiredError -> AllIcons.General.Warning
    error != null -> RfsIcons.ERROR_ICON
    isLoading -> RfsIcons.LOADING_ICON
    isMount -> driver.icon
    else -> getIdleIcon()
  }

  override fun onDoubleClick() = if (error is RfsAuthRequiredError) {
    executeOnPooledThread {
      runBlockingCancellable {
        driver.fileInfoManager.refreshDriver(ActivitySource.ACTION)
      }
    }
    true
  }
  else {
    false
  }

  open fun getIdleIcon(): Icon? {
    val isWriteLocked = fileInfo?.writeLocked() == true

    return when {
      rfsPath.isDirectory && isWriteLocked -> RfsIcons.REMOTE_DIRECTORY_LOCKED_ICON
      rfsPath.isDirectory -> RfsIcons.REMOTE_DIRECTORY_ICON
      else -> fileInfo?.let { getIconByType(it) } ?: RfsIcons.FILE_ICON
    }
  }

  override fun getChildren(): List<RfsTreeNode> = cachedChildren ?: emptyList()

  fun addLoadedChildren(newChildren: List<DriverFileRfsTreeNode>,
                        loadMoreNode: DriverRfsLoadMoreNode?,
                        newMetaInfoNodes: List<RfsMetaInfoNode> = emptyList()) {
    cachedChildren = newChildren
    this.loadMoreNode = loadMoreNode
    metaInfoNodes = newMetaInfoNodes
    updatingChildrenTask.set(null)
    isInvalidated.set(false)
    update()
  }

  override fun canNavigate(): Boolean = fileInfo != null
  override fun canNavigateToSource(): Boolean = fileInfo != null

  override fun navigate(requestFocus: Boolean) {
    val project = project ?: return
    val fileInfo = fileInfo ?: return
    if (fileInfo.isFile) {
      //FileTypeViewerManager.getInstance(project).openViewer(fileInfo, requestFocus)
    }
    else {
      RfsViewerEditorProvider.createFileViewerEditor(project, fileInfo.driver, fileInfo.path)
    }
  }

  override fun update(presentation: PresentationData) {
    val nodeIcon = getNodeIcon()
    presentation.setIcon(nodeIcon)
    val errorText = error?.toPresentableText()
    if (errorText != null)
      presentation.tooltip = errorText

    fileInfo?.let {
      val size = it.length
      val modified = it.modificationTime
      if (it.isFile && size > 1 && modified > 1)
        presentation.tooltip = KafkaMessagesBundle.message("rfs.node.file.tooltip", SizeUtils.toString(size),
                                                           TimeUtils.unixTimeToString(modified))
    }

    getGrayText()?.let {
      presentation.addText(authMessage() + name(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
      presentation.addText("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    } ?: let {
      presentation.presentableText = authMessage() + name()
    }
  }

  @NlsContexts.Label
  protected open fun name(): String {
    if (isMount) return driver.presentableName
    val parentPath = (parent as? DriverFileRfsTreeNode)?.rfsPath ?: RfsPath(emptyList(), isDirectory = true)
    val relativePath = if (rfsPath.startsWith(parentPath)) rfsPath.dropPrefix(parentPath.elements.size) else rfsPath
    @Suppress("HardCodedStringLiteral") // The name is a path string.
    return relativePath.elements.joinToString("/")
  }

  @NlsSafe
  private fun authMessage() = if (error is RfsAuthRequiredError)
    KafkaMessagesBundle.message("auth.double.click.hint") + " "
  else
    ""

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as DriverFileRfsTreeNode

    if (rfsPath != other.rfsPath) return false
    return driver == other.driver
  }

  override fun hashCode(): Int {
    var result = rfsPath.hashCode()
    result = 31 * result + driver.hashCode()
    return result
  }
}