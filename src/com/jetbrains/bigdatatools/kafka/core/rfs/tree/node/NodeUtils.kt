package com.jetbrains.bigdatatools.kafka.core.rfs.tree.node

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.writeLocked
import com.jetbrains.bigdatatools.kafka.core.rfs.fileType.RfsFileType
import com.jetbrains.bigdatatools.kafka.core.rfs.icons.RfsIcons
import javax.swing.Icon
import javax.swing.tree.TreePath

fun AbstractTreeNode<*>.getPath(acc: List<AbstractTreeNode<*>> = emptyList()): TreePath = if (parent == null)
  TreePath((acc + this).reversed().toTypedArray())
else
  parent.getPath(acc + this)


fun getIconByType(fileInfo: FileInfo): Icon = RfsFileType.getFileType(fileInfo)?.getIcon(fileInfo.writeLocked())
                                              ?: if (fileInfo.writeLocked()) RfsIcons.FILE_LOCKED_ICON else RfsIcons.FILE_ICON