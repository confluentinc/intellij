package io.confluent.kafka.core.rfs.tree.node

interface CustomDoubleClickable {
  fun onDoubleClick(): Boolean
}