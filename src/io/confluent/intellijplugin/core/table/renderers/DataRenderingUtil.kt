package io.confluent.intellijplugin.core.table.renderers

import io.confluent.intellijplugin.core.table.DecoratableDataTableModel
import javax.swing.table.TableCellRenderer
import kotlin.reflect.full.primaryConstructor

object DataRenderingUtil {
    fun shouldRenderFrom(annotations: Array<out Annotation>?): Boolean =
        annotations?.let { ans -> !ans.any { it is NoRendering } } ?: true

    fun getRenderer(annotations: Array<out Annotation>, model: DecoratableDataTableModel? = null): TableCellRenderer? {
        annotations.forEach {
            when (it) {
                is ProgressRendering -> return if (model == null) null else ProgressCellRenderer(
                    it.maxPropertyName,
                    model
                )

                is FixedProgressRendering -> return FixedProgressCellRenderer(it.limit)
                is StatusRendering -> return StatusRenderer(it.failName, it.successName, it.warningName)
                is LinkArrayRendering -> return LinkArrayRenderer()
                is LinkRendering -> return LinkRenderer()
                is CustomRendering -> return it.clazz.primaryConstructor?.call()
                is UnixtimeRendering -> return UnixtimeRenderer()
                is DurationRendering -> return DurationRenderer(it.neededAddChecking)
                is DoubleRendering -> return DoubleRenderer()
                is DataSizeRendering -> return DataSizeRenderer()
                is DateRendering -> return DateRenderer(it.neededAddChecking)
                is LoadingRendering -> return LoadingRenderer(it.rightAligned)
            }
        }
        return null
    }
}
