package io.confluent.intellijplugin.toolwindow

import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import org.jetbrains.concurrency.Promise
import javax.swing.tree.TreePath

/**
 * Interface for controllers that support navigation to RFS paths.
 * Implemented by both KafkaMainController and ConfluentMainController.
 */
interface NavigableController {
    /**
     * Opens/navigates to the specified RFS path in the tree view.
     * @return A promise that resolves to the selected TreePath
     */
    fun open(rfsPath: RfsPath): Promise<TreePath>
}
