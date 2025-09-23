package io.confluent.kafka.core.rfs.search.impl

import com.intellij.icons.AllIcons
import io.confluent.kafka.core.rfs.driver.FileInfo

class BackListElement(fileInfo: FileInfo) : ListElement(fileInfo, AllIcons.Actions.Back)