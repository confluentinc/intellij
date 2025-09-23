package io.confluent.kafka.core.rfs.projectview.actions

import com.intellij.ide.IdeBundle
import io.confluent.kafka.core.rfs.driver.FileInfo
import javax.swing.tree.TreePath

object RfsActionUtil {

  fun excludeNestedPaths(paths: List<TreePath>): Set<TreePath> = paths.sortedBy { it.pathCount }.fold(emptySet()) { mySet, newPath ->
    if (!setContainsAncestor(mySet, newPath))
      mySet + newPath
    else
      mySet
  }

  private tailrec fun setContainsAncestor(set: Set<TreePath>, path: TreePath): Boolean = when {
    path.parentPath == null -> false
    set.contains(path.parentPath) -> true
    else -> setContainsAncestor(set, path.parentPath)
  }


  fun filesAndDirectoriesString(files: List<FileInfo>) =
    if (files.size == 1)
      files[0].externalPath
    else {
      val simpleFiles = files.filter { it.isFile && !it.isSymbolicLink }.size
      val symlinks = files.filter { it.isSymbolicLink }.size
      val dirs = files.filter { it.isDirectory && !it.isSymbolicLink }.size

      fun plural(count: Int): Int = if (count > 1) 2 else 1

      val result = StringBuilder()
      if (simpleFiles > 0) {
        result.append(simpleFiles).append(" ").append(IdeBundle.message("prompt.delete.file", plural(simpleFiles)))
      }
      if (symlinks > 0) {
        if (result.isNotEmpty()) result.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ")
        result.append(symlinks).append(" ").append(IdeBundle.message("prompt.delete.symlink", plural(symlinks)))
      }
      if (dirs > 0) {
        if (result.isNotEmpty()) result.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ")
        result.append(dirs).append(" ").append(IdeBundle.message("prompt.delete.directory", plural(dirs)))
      }
      result.toString()
    }
}