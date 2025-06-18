package com.jetbrains.bigdatatools.kafka.core.rfs.driver

import org.jetbrains.annotations.TestOnly

enum class ActivitySource(val calledByUser: Boolean) {
  DEPEND_UPDATED(false),
  DRIVER_CREATION(false),
  TIMER(false),
  ZEPPELIN_RECONNECT(false),
  AWAIT_MONITORING(false),
  ARBITRARY_CLUSTER_DEPENDENT(false),
  EMR_DEPENDENT(false),
  DATABRICKS_DELEGATE(false),


  ACTION(true),
  TEST_ACTION(true),
  SFTP_CLONE_SUDO(true),
  DATAPROC_DEPENDENT(true),
  EMR_DEPENDENT_USER(true),
  DATABRICKS_DELEGATE_USER(true),

  @TestOnly
  TEST(true),
}