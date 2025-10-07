package io.confluent.intellijplugin.core.rfs.driver

enum class ActivitySource(val calledByUser: Boolean) {
    DEPEND_UPDATED(false),
    DRIVER_CREATION(false),
    TIMER(false),

    ACTION(true),
    TEST_ACTION(true),
}