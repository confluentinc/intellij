package com.jetbrains.bigdatatools.kafka.core.settings.connections

import org.jetbrains.annotations.Nls
import javax.swing.Icon
import kotlin.random.Random

abstract class ConnectionGroup(
  val id: String,
  @Nls(capitalization = Nls.Capitalization.Title)
  val name: String,
  val icon: Icon? = null,
  val parentGroupId: String? = null,
  val visible: Boolean = true
) {
  init {
    // only intermediate groups can be invisible (=flat), currently we don't have such
    require(visible || parentGroupId != null)
  }
}

abstract class ConnectionFactory<T : ConnectionData>(
  id: String,
  @Nls(capitalization = Nls.Capitalization.Title)
  name: String,
  icon: Icon? = null,
  parentGroupId: String? = null
) : ConnectionGroup(id, name, icon, parentGroupId) {
  fun createBlankData(forcedId: String? = null, perProject: Boolean = false) = newData().apply {
    innerId = forcedId ?: "$name@$id@${Random.nextLong()}"
    groupId = id
    isPerProject = perProject
  }
  abstract fun newData(): T
}