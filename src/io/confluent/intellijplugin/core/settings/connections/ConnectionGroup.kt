package io.confluent.intellijplugin.core.settings.connections

import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
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

    /** Override to provide a custom panel when this group is selected in the settings tree. */
    open fun createOptionsPanel(): JComponent = JPanel()
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