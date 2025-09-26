package io.confluent.intellijplugin.core.rfs.search.impl

import com.intellij.icons.AllIcons
import io.confluent.intellijplugin.core.rfs.driver.FileInfo

class BackListElement(fileInfo: FileInfo) : ListElement(fileInfo, AllIcons.Actions.Back)