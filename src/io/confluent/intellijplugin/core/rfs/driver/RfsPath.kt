package io.confluent.intellijplugin.core.rfs.driver

import com.intellij.openapi.util.NlsSafe

open class RfsPath(val elements: List<String>, val isDirectory: Boolean) : Comparable<RfsPath> {
  val isFile: Boolean = !isDirectory

  val size: Int = elements.size

  /**
   * Returns the <em>parent path</em>, or {@code null} if this path does not
   * have a parent.
   *
   * @return  a path representing the path's parent
   */
  open val parent: RfsPath? =
    if (elements.isEmpty())
      null
    else
      RfsPath(elements.subList(0, elements.size - 1), true)

  /**
   * Returns the name of the file or directory denoted by this path as a
   * {@code Path} object. The file name is the <em>farthest</em> element from
   * the root in the directory hierarchy.
   *
   * @return  a path representing the name of the file or directory, or empty string
   *          if this path has zero elements
   */
  val name: String =
    if (elements.isEmpty())
      ROOT_FILE_NAME
    else
      elements.last()

  /**
   * Returns the number of name elements in the path, or 0 if this path only represents a root component
   */
  val nameCount: Int = elements.size

  val isRoot: Boolean = elements.isEmpty()

  /**
   * The index parameter is the index of the name element to return.
   * The element that is closest to the root in the directory hierarchy has index 0.
   * The element that is farthest from the root has index count-1.
   *
   * @return   Returns a name element of this path as a Path object.
   */
  fun name(i: Int): String = elements[i]

  /**
   * Resolves the given path against this path's {@link #getParent parent}
   * path. This is useful where a file name needs to be <i>replaced</i> with
   * another file name. For example, suppose that the name separator is
   * "{@code /}" and a path represents "{@code dir1/dir2/foo}", then invoking
   * this method with the {@code Path} "{@code bar}" will result in the {@code
   * Path} "{@code dir1/dir2/bar}". If this path does not have a parent path,
   * or {@code other} is {@link #isAbsolute() absolute}, then this method
   * returns {@code other}. If {@code other} is an empty path then this method
   * returns this path's parent, or where this path doesn't have a parent, the
   * empty path.
   *
   * @param   path - the path to resolve against this path's parent
   *
   * @return  the resulting path
   */
  fun resolveSibling(path: String, isDirectory: Boolean): RfsPath? = parent?.child(path, isDirectory)

  fun resolve(path: RfsPath): RfsPath {
    if (isFile)
      error("Cannot resolve path against file")
    if (path.stringRepresentation().removePrefix("/").isBlank())
      return this
    return RfsPath(elements + path.elements, path.isDirectory)
  }

  /**
   * Resolve the given path against this path.
   *
   * @param   subPath - the path to resolve against this path
   * @param   isDirectory - whether child path should be directory or file
   *
   * @return  the resulting path
   */
  open fun child(subPath: String, isDirectory: Boolean): RfsPath {
    require(isDirectory || !subPath.endsWith("/")) {
      "Wrong path $subPath, isDirectory = $isDirectory"
    }
    val cleanPath = subPath.removePrefix("/").removeSuffix("/")
    return RfsPath(elements + cleanPath, isDirectory)
  }

  fun addRelative(relPath: String, isDirectory: Boolean): RfsPath {
    assert(!relPath.startsWith("/") && (isDirectory || !relPath.endsWith("/"))) {
      "Relative path: ${relPath}, isDirectory: ${isDirectory} cannot be added to $this"
    }

    val relPathParts = relPath.removeSuffix("/").split("/")
    assert(relPathParts.isNotEmpty()) {
      "Relative path is empty, isDirectory: ${isDirectory}. It cannot be added to $this"
    }

    var res = this
    relPathParts.dropLast(1).forEach {
      res = res.child(it, true)
    }
    return res.child(relPathParts.last(), isDirectory)
  }

  open fun replacePrefix(prefixElementsCount: Int, newPrefix: RfsPath): RfsPath =
    if (prefixElementsCount >= elements.size)
      newPrefix
    else
      RfsPath(newPrefix.elements + elements.drop(prefixElementsCount), isDirectory)

  fun dropPrefix(prefixElementsCount: Int): RfsPath = replacePrefix(prefixElementsCount, RfsPath(emptyList(), true))


  /**
   * Tests if this path starts with the given path.
   *
   * <p> This path <em>starts</em> with the given path if this path's root
   * component <em>starts</em> with the root component of the given path,
   * and this path starts with the same name elements as the given path.
   * If the given path has more name elements than this path then {@code false}
   * is returned.
   *
   * <p> Whether or not the root component of this path starts with the root
   * component of the given path is file system specific. If this path does
   * not have a root component and the given path has a root component then
   * this path does not start with the given path.
   *
   * @param   other - the given path
   *
   * @return  {@code true} if this path starts with the given path; otherwise
   *          {@code false}
   */
  fun startsWith(other: RfsPath): Boolean {
    val otherElements = other.elements

    return when {
      otherElements.size > elements.size -> false
      elements.size == otherElements.size -> isDirectory == other.isDirectory && elements == otherElements
      else -> elements.take(otherElements.size) == otherElements
    }
  }

  /**
   * Returns a Rfs Path that is a subsequence of the name elements of this path.
   * If the endIndex is greater than end, full path will be returned.
   *
   * @param endIndex -  the index of the last element, exclusive
   *
   * @return a new RfsPath object that is a subsequence of the name elements in this RfsPath
   */
  open fun prefixPath(endIndex: Int): RfsPath =
    if (endIndex >= elements.size)
      this
    else
      RfsPath(elements.take(endIndex), true)

  @NlsSafe
  fun stringRepresentation(): String =
    if (elements.isEmpty())
      ROOT_FILE_NAME
    else
      elements.joinToString(separator = "/", postfix = if (isDirectory) "/" else "")

  override fun toString() = if (elements.isEmpty())
    "<Root>"
  else
    elements.joinToString(separator = "/", postfix = if (isDirectory) "/" else "") { it.replace("/", "\\/") }

  fun asDirectoryPath() = RfsPath(elements, true)

  fun asFilePath() = RfsPath(elements, false)


  override fun compareTo(other: RfsPath) = stringRepresentation().compareTo(other.stringRepresentation())

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RfsPath) return false

    if (elements != other.elements) return false
    if (isDirectory != other.isDirectory) return false

    return true
  }

  override fun hashCode(): Int {
    var result = elements.hashCode()
    result = 31 * result + isDirectory.hashCode()
    return result
  }

  companion object {
    const val ROOT_FILE_NAME = ""
  }
}

fun RfsPath.child(info: FileInfo): RfsPath = child(info.name, info.isDirectory)

@NlsSafe
fun presentableText(driver: Driver, path: RfsPath) = "${driver.presentableName}:/${path.stringRepresentation().removePrefix("/")}"