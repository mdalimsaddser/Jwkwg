package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(private val dao: BrowserDao) {

    val allBookmarks: Flow<List<Bookmark>> = dao.getAllBookmarks()
    val allHistory: Flow<List<HistoryItem>> = dao.getAllHistory()
    val allTabs: Flow<List<TabItem>> = dao.getAllTabs()

    suspend fun insertBookmark(bookmark: Bookmark) {
        dao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmarkByUrl(url: String) {
        dao.deleteBookmarkByUrl(url)
    }

    fun isBookmarkedFlow(url: String): Flow<Boolean> {
        return dao.matchesBookmarkFlow(url)
    }

    suspend fun isBookmarked(url: String): Boolean {
        return dao.isBookmarked(url)
    }

    suspend fun insertHistory(historyItem: HistoryItem) {
        dao.insertHistory(historyItem)
    }

    suspend fun deleteHistoryById(id: Int) {
        dao.deleteHistoryById(id)
    }

    suspend fun clearHistory() {
        dao.clearAllHistory()
    }

    suspend fun insertTab(tabItem: TabItem): Int {
        return dao.insertTab(tabItem).toInt()
    }

    suspend fun updateTab(tabItem: TabItem) {
        dao.updateTab(tabItem)
    }

    suspend fun deleteTab(id: Int) {
        dao.deleteTabById(id)
    }

    suspend fun clearAllTabs() {
        dao.deleteAllTabs()
    }
}
