package io.confluent.kafka.core.table.renderers

import javax.swing.table.TableCellRenderer
import kotlin.reflect.KClass

/**
 * User: Dmitry.Naydanov
 * Date: 2019-04-10.
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class NoRendering

@Retention(AnnotationRetention.RUNTIME)
annotation class DoubleRendering

@Retention(AnnotationRetention.RUNTIME)
annotation class DataSizeRendering

@Retention(AnnotationRetention.RUNTIME)
annotation class UnixtimeRendering

@Retention(AnnotationRetention.RUNTIME)
annotation class DurationRendering(val neededAddChecking: Boolean = false)

@Retention(AnnotationRetention.RUNTIME)
annotation class DateRendering(val neededAddChecking: Boolean = false)

@Retention(AnnotationRetention.RUNTIME)
annotation class LoadingRendering(val rightAligned: Boolean = false)

@Retention(AnnotationRetention.RUNTIME)
annotation class ProgressRendering(val maxPropertyName: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class FixedProgressRendering(val limit: Int)

@Retention(AnnotationRetention.RUNTIME)
annotation class StatusRendering(val failName: String = "", val successName: String = "", val warningName: String = "")

@Retention(AnnotationRetention.RUNTIME)
annotation class LinkRendering

@Retention(AnnotationRetention.RUNTIME)
annotation class LinkArrayRendering

@Retention(AnnotationRetention.RUNTIME)
annotation class CustomRendering(val clazz: KClass<out TableCellRenderer>)