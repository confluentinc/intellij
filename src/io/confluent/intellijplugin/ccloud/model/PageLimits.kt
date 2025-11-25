package io.confluent.intellijplugin.ccloud.model

/**
 * Pagination limits for API requests.
 */
data class PageLimits(
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val maxPages: Int = DEFAULT_MAX_PAGES
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 100
        const val DEFAULT_MAX_PAGES = 100
        val DEFAULT = PageLimits()
    }
}

