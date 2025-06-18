package com.jetbrains.bigdatatools.kafka.core.rfs.driver.metainfo.details

import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class FileInfoBlock(@Nls val title: String,
                    val component: JComponent,
                    val isModified: (() -> Boolean)? = null,
                    val apply: (() -> Unit)? = null,
                    val revert: (() -> Unit)? = null)