package io.confluent.intellijplugin.core.rfs.url

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.io.URLUtil
import io.confluent.intellijplugin.core.rfs.util.withPrefixSlash
import io.confluent.intellijplugin.core.settings.connections.ConnectionData

interface UrlFileOpener {
  val name: String
  fun getConnections(project: Project): List<ConnectionData>

  fun canOpenUrl(url: String): Boolean
  fun openIfRfsViewer(project: Project, url: String, connectionData: ConnectionData)

  fun createConnection(project: Project, url: String)

  companion object {
    private val EXTENSION_POINT_NAME: ExtensionPointName<UrlFileOpener> =
      ExtensionPointName.create("com.intellij.bigdatatools.rfs.urlOpener")

    fun getOpenersFor(url: String) = EXTENSION_POINT_NAME.extensions.filter { it.canOpenUrl(url) }

    fun getPath(url: String) = url.split("://").last().withPrefixSlash()

    fun getStoragePathFromHttpWithPostBucket(rawUrl: String): String {
      val parsed = URLUtil.unescapePercentSequences(rawUrl)
      val prefixFs = parsed.split("://").first()
      val urlWithoutFs = parsed.removePrefix("$prefixFs://").split("?").first()
      val host = urlWithoutFs.split("/").first()
      return urlWithoutFs.removePrefix(host).removePrefix("/")
    }

  }
}