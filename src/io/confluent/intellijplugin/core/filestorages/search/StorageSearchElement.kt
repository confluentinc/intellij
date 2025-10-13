package io.confluent.intellijplugin.core.filestorages.search

import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.search.impl.SearchElement
import javax.swing.Icon

data class StorageSearchElement(
    override val query: String,
    val driver: Driver,
    override val rfsPath: RfsPath,
    override val fileInfo: FileInfo?
) : SearchElement {
    override val fileName: String = rfsPath.stringRepresentation()
    override val connId: String = driver.getExternalId()
    override val name: String = rfsPath.name
    override val snippet: String = ""
    override val text: String = ""
    override val id: String = rfsPath.stringRepresentation()
    override val header: String = ""
    override var icon: Icon? = null
}