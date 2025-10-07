package io.confluent.intellijplugin.core.monitoring.toolwindow

import com.intellij.openapi.project.Project

abstract class TabbedDetailsMonitoringController<ID>(project: Project) : DetailsMonitoringController<ID>,
    TabbedMonitoringController(
        project
    ) {
    var detailsId: ID? = null
        private set
    abstract override val tabsControllers: List<Pair<String, DetailsMonitoringController<ID>>>


    override fun setDetailsId(id: ID) {
        detailsId = id
        tabsControllers.forEach { it.second.setDetailsId(id) }
    }
}