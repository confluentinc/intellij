package io.confluent.intellijplugin.core.rfs.driver

import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.launch

fun Driver.refreshConnectionLaunch(activitySource: ActivitySource) {
    safeExecutor.coroutineScope.launch {
        refreshConnection(activitySource)
    }
}

fun Driver.refreshConnectionBlocking(activitySource: ActivitySource): DriverConnectionStatus {
    return runBlockingCancellable {
        refreshConnection(activitySource)
    }
}