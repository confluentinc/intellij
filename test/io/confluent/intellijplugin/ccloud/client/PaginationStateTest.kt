package io.confluent.intellijplugin.ccloud.client

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient.PageLimits
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient.PageOfResults
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient.PaginationState
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class PaginationStateTest {

    companion object {
        private const val INITIAL_URL = "http://first"
        private const val NEXT_PAGE_URL = "http://next"
    }

    @Nested
    @DisplayName("createPage")
    inner class NewPageTests {

        @Test
        fun `returns all items when under limit`() {
            val state = PaginationState(INITIAL_URL, PageLimits(maxItems = 100))
            val items = listOf("a", "b", "c")

            val page = state.createPage(items, NEXT_PAGE_URL)

            assertEquals(3, page.items.size)
            assertEquals(listOf("a", "b", "c"), page.items)
            assertTrue(page.hasMore)
            assertEquals(NEXT_PAGE_URL, state.nextUrl)
        }

        @Test
        fun `truncates items when exceeding limit`() {
            val state = PaginationState(INITIAL_URL, PageLimits(maxItems = 2))
            state.createPage(listOf("a"), "http://second")  // 1 item fetched

            val page = state.createPage(listOf("b", "c", "d"), null)

            assertEquals(1, page.items.size)  // Only 1 more allowed
            assertEquals("b", page.items[0])
            assertFalse(page.hasMore)
        }

        @ParameterizedTest(name = "treats \"{0}\" as no more pages")
        @NullAndEmptySource
        @ValueSource(strings = ["   "])
        fun `treats null, empty, or blank next URL as no more pages`(nextUrl: String?) {
            val state = PaginationState(INITIAL_URL)

            val page = state.createPage(listOf("a"), nextUrl)

            assertFalse(page.hasMore)
            assertEquals(null, state.nextUrl)
        }

        @Test
        fun `applies item limit across multiple pages`() {
            val state = PaginationState(INITIAL_URL, PageLimits(maxItems = 5))

            state.createPage(listOf("a", "b"), "http://page2")      // 2 items
            state.createPage(listOf("c", "d"), "http://page3")      // 2 more = 4 total
            val page = state.createPage(listOf("e", "f", "g"), null)  // Only 1 more allowed

            assertEquals(1, page.items.size)
            assertEquals("e", page.items[0])
        }
    }

    @Nested
    @DisplayName("shouldContinue")
    inner class ShouldContinueTests {

        @Test
        fun `returns false when no more pages`() {
            val state = PaginationState(INITIAL_URL)
            val page = PageOfResults<String>(listOf("a"), hasMore = false)

            assertFalse(state.shouldContinue(page))
        }

        @Test
        fun `returns false when page limit reached`() {
            val state = PaginationState(INITIAL_URL, PageLimits(maxPages = 1))
            state.createPage(listOf("a"), NEXT_PAGE_URL)  // 1 page fetched

            val page = PageOfResults<String>(emptyList(), hasMore = true)

            assertFalse(state.shouldContinue(page))
        }

        @Test
        fun `returns false when item limit reached`() {
            val state = PaginationState(INITIAL_URL, PageLimits(maxItems = 1))
            state.createPage(listOf("a"), NEXT_PAGE_URL)  // 1 item fetched

            val page = PageOfResults<String>(emptyList(), hasMore = true)

            assertFalse(state.shouldContinue(page))
        }
    }

}
