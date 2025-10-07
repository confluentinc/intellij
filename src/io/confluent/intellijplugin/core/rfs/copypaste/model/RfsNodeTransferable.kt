package io.confluent.intellijplugin.core.rfs.copypaste.model

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

internal class RfsNodeTransferable(val data: TransferableDescriptor) : Transferable {
    override fun getTransferData(flavor: DataFlavor?): Any = when (flavor) {
        rfsDataFlavor -> data
        DataFlavor.stringFlavor -> data.data.mapNotNull { it.fileInfo?.name }.joinToString("\n")
        else -> throw UnsupportedFlavorException(flavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = supportedFlavors.contains(flavor)
    override fun getTransferDataFlavors(): Array<DataFlavor> = supportedFlavors
}