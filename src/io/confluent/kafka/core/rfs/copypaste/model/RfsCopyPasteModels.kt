package io.confluent.kafka.core.rfs.copypaste.model

import io.confluent.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import java.awt.datatransfer.DataFlavor

data class TransferableDescriptor(val data: List<DriverFileRfsTreeNode>, val deleteAfter: Boolean)

internal val rfsDataFlavor = DataFlavor(DriverFileRfsTreeNode::class.java, "Files")
internal val supportedFlavors = arrayOf(DataFlavor.stringFlavor, rfsDataFlavor)

