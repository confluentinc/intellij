package com.jetbrains.bigdatatools.kafka.model

class InternalPartition(val partition: Int = 0,
                        val leader: Int? = null,
                        val replicas: List<InternalReplica> = emptyList(),
                        val inSyncReplicasCount: Int = 0,
                        val replicasCount: Int = 0,
                        val offsetMin: Long = 0,
                        val offsetMax: Long = 0,
                        val segmentSize: Long = 0,
                        val segmentCount: Long = 0)