package io.confluent.kafka.core.settings.paneadd

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.util.containers.MultiMap
import io.confluent.kafka.core.settings.connections.BrokerConnectionGroup
import io.confluent.kafka.core.settings.connections.ConnectionFactory
import io.confluent.kafka.core.settings.connections.ConnectionGroup
import io.confluent.kafka.core.settings.connections.ConnectionSettingProviderEP

object StandaloneCreateConnectionUtil {
  val groupsPriority: Map<String, Int> = mapOf(BrokerConnectionGroup.GROUP_ID to 0)

  class FlattenedGroup(private val treeGroup: ActionGroup) : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val actions = mutableListOf<AnAction>()
      linearize(treeGroup, e, actions)
      return actions.toTypedArray()
    }

  }

  private fun linearize(actionGroup: ActionGroup, e: AnActionEvent?, list: MutableList<AnAction>) {
    if (list.lastOrNull().let { it is Separator && actionGroup.templateText != null }) {
      list.removeLast()
    }
    list.add(Separator.create(actionGroup.templateText))
    val children = if (e != null || actionGroup !is DefaultActionGroup)
      actionGroup.getChildren(e)
    else
      actionGroup.getChildren(ActionManager.getInstance())
    for (member in children) {
      if (member is ActionGroup) {
        linearize(member, e, list)
      }
      else {
        list.add(member)
      }
    }
  }

  internal fun linearizeActions(actionGroup: ActionGroup): List<AnAction> {
    val actions = mutableListOf<AnAction>()
    linearize(actionGroup, null, actions)
    return actions
  }

  fun createRootAddAction(project: Project, groups: List<ConnectionGroup> = ConnectionSettingProviderEP.getGroups()): DefaultActionGroup {
    val groupsGraph = MultiMap<String?, ConnectionGroup>()

    val parentGroups = groups.map { it.id }.toSet()
    for (group in groups) {
      groupsGraph.putValue(group.parentGroupId.takeIf { it in parentGroups }, group)
    }

    val rootAction = DefaultActionGroup(null, true)
    dfsActions(rootAction, groupsGraph, project)
    return rootAction
  }

  private fun dfsActions(root: DefaultActionGroup, groupsGraph: MultiMap<String?, ConnectionGroup>, project: Project) {
    val marked = mutableSetOf<ConnectionGroup>()

    fun dfs(currentRoot: DefaultActionGroup, currentGroup: ConnectionGroup) {
      if (marked.contains(currentGroup)) return //todo log cyclic dependency
      marked.add(currentGroup)
      if (currentGroup is ConnectionFactory<*>) currentRoot.add(StandaloneCreateConnectionAction(project, currentGroup))

      val children = groupsGraph.get(currentGroup.id)
      if (children.isNotEmpty()) {
        val newGroup = DefaultActionGroup(currentGroup.name, true)
        newGroup.templatePresentation.icon = currentGroup.icon

        for (child in children) dfs(newGroup, child)
        currentRoot.add(newGroup)
      }
    }

    val topLevel = groupsGraph.get(null).sortedBy { groupsPriority.getOrDefault(it.id, 4) }
    topLevel.forEach { dfs(root, it) }
  }
}