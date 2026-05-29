package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

data class WebTabState(
    val id: Int,
    val url: String = "about:blank",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

sealed interface BrowserAction {
    data class LoadUrl(val url: String) : BrowserAction
    object GoBack : BrowserAction
    object GoForward : BrowserAction
    object Reload : BrowserAction
    object StopLoading : BrowserAction
    data class InjectJS(val script: String) : BrowserAction
    object ClearHistory : BrowserAction
    object ClearCache : BrowserAction
}

data class ConsoleLog(
    val message: String,
    val level: String, // "log", "warn", "error"
    val timestamp: Long = System.currentTimeMillis()
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BrowserRepository

    init {
        val db = BrowserDatabase.getDatabase(application)
        repository = BrowserRepository(db.browserDao())
    }

    // Bookmarks and History Flow
    val bookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tabs management state
    private val _tabs = MutableStateFlow<List<WebTabState>>(emptyList())
    val tabs: StateFlow<List<WebTabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Int?>(null)
    val activeTabId: StateFlow<Int?> = _activeTabId.asStateFlow()

    // Search Engine & Address bar
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchEngines = listOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Bing" to "https://www.bing.com/search?q="
    )
    private val _currentEngineIndex = MutableStateFlow(0)
    val currentEngineIndex: StateFlow<Int> = _currentEngineIndex.asStateFlow()

    // Developer configs
    private val _isJavaScriptEnabled = MutableStateFlow(true)
    val isJavaScriptEnabled: StateFlow<Boolean> = _isJavaScriptEnabled.asStateFlow()

    private val _isCacheEnabled = MutableStateFlow(true)
    val isCacheEnabled: StateFlow<Boolean> = _isCacheEnabled.asStateFlow()

    // Console Logging & Dev Panel
    private val _consoleLogs = MutableStateFlow<List<ConsoleLog>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLog>> = _consoleLogs.asStateFlow()

    // User Agent settings
    val userAgents = listOf(
        "Mobile (Android)" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Desktop (Chrome Windows)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Desktop (Safari MacOS)" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15",
        "Sabbir Dev Agent" to "SabbirBrowser/1.0 (Android; MD_SABBIR_DEVELOPMENT; sabbir347iii@gmail.com)"
    )
    private val _userAgentIndex = MutableStateFlow(0) // Default Android Mobile
    val userAgentIndex: StateFlow<Int> = _userAgentIndex.asStateFlow()

    // Navigation and communication actions sharing
    private val _browserActions = MutableSharedFlow<BrowserAction>(extraBufferCapacity = 64)
    val browserActions: SharedFlow<BrowserAction> = _browserActions.asSharedFlow()

    init {
        // Retrieve saved tabs from Room if we want, or initialize with a direct "Sabbir Home" tab
        viewModelScope.launch {
            repository.allTabs.first().let { saved ->
                if (saved.isNotEmpty()) {
                    val initialList = saved.map {
                        WebTabState(
                            id = it.id,
                            url = it.url,
                            title = it.title,
                            canGoBack = false,
                            canGoForward = false
                        )
                    }
                    _tabs.value = initialList
                    val activeId = saved.find { it.isLastActive }?.id ?: saved.first().id
                    _activeTabId.value = activeId
                } else {
                    // Create default first tab
                    addNewTab("about:blank", "Sabbir Browser")
                }
            }
        }
    }

    // Action execution helpers
    fun loadUrl(url: String) {
        val engineUrl = searchEngines[_currentEngineIndex.value].second
        val parsedUrl = resolveUrlOrSearch(url, engineUrl)
        viewModelScope.launch {
            _browserActions.emit(BrowserAction.LoadUrl(parsedUrl))
        }
    }

    fun goBack() {
        viewModelScope.launch { _browserActions.emit(BrowserAction.GoBack) }
    }

    fun goForward() {
        viewModelScope.launch { _browserActions.emit(BrowserAction.GoForward) }
    }

    fun reload() {
        viewModelScope.launch { _browserActions.emit(BrowserAction.Reload) }
    }

    fun stop() {
        viewModelScope.launch { _browserActions.emit(BrowserAction.StopLoading) }
    }

    fun injectJS(script: String) {
        viewModelScope.launch { _browserActions.emit(BrowserAction.InjectJS(script)) }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectSearchEngine(index: Int) {
        if (index in searchEngines.indices) {
            _currentEngineIndex.value = index
        }
    }

    fun selectUserAgent(index: Int) {
        if (index in userAgents.indices) {
            _userAgentIndex.value = index
            // Reload page to apply new header
            reload()
        }
    }

    // Tab CRUD
    fun addNewTab(url: String = "about:blank", title: String = "New Tab") {
        viewModelScope.launch {
            val dbTabId = repository.insertTab(
                TabItem(
                    url = url,
                    title = title,
                    isLastActive = true
                )
            )
            // Deselect old ones
            val currentList = _tabs.value.map { it.copy() }
            val newList = currentList + WebTabState(id = dbTabId, url = url, title = title)
            _tabs.value = newList
            _activeTabId.value = dbTabId
            updatePersistentTabs(dbTabId)
            
            if (url != "about:blank") {
                loadUrl(url)
            }
        }
    }

    fun closeTab(id: Int) {
        viewModelScope.launch {
            repository.deleteTab(id)
            val currentList = _tabs.value.filter { it.id != id }
            _tabs.value = currentList
            
            if (_activeTabId.value == id) {
                if (currentList.isNotEmpty()) {
                    val nextId = currentList.last().id
                    _activeTabId.value = nextId
                    updatePersistentTabs(nextId)
                } else {
                    addNewTab() // Stay with at least one empty tab
                }
            }
        }
    }

    fun selectTab(id: Int) {
        if (_activeTabId.value == id) return
        _activeTabId.value = id
        viewModelScope.launch {
            updatePersistentTabs(id)
        }
    }

    fun clearAllTabs() {
        viewModelScope.launch {
            repository.clearAllTabs()
            _tabs.value = emptyList()
            addNewTab() // Stay with at least one empty tab
        }
    }

    private suspend fun updatePersistentTabs(activeId: Int) {
        _tabs.value.forEach { tab ->
            repository.updateTab(
                TabItem(
                    id = tab.id,
                    url = tab.url,
                    title = tab.title,
                    isLastActive = (tab.id == activeId)
                )
            )
        }
    }

    // Updates state from WebView callbacks
    fun onPageStarted(tabId: Int, url: String) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(url = url, isLoading = true, progress = 0) else it
        }
        if (_activeTabId.value == tabId) {
            _searchQuery.value = if (url == "about:blank") "" else url
        }
    }

    fun onPageFinished(tabId: Int, url: String, title: String?) {
        val pageTitle = if (title.isNullOrEmpty() || title == "about:blank") "Sabbir Browser" else title
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(url = url, title = pageTitle, isLoading = false, progress = 100) else it
        }
        if (_activeTabId.value == tabId) {
            _searchQuery.value = if (url == "about:blank") "" else url
        }
        
        // Add to history if not about:blank or empty
        if (url.isNotEmpty() && url != "about:blank" && !url.startsWith("file://")) {
            viewModelScope.launch {
                repository.insertHistory(
                    HistoryItem(
                        url = url,
                        title = pageTitle
                    )
                )
            }
        }
    }

    fun onProgressChanged(tabId: Int, progress: Int) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(progress = progress) else it
        }
    }

    fun updateNavigationState(tabId: Int, canGoBack: Boolean, canGoForward: Boolean) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(canGoBack = canGoBack, canGoForward = canGoForward) else it
        }
    }

    // Bookmark Toggle
    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            if (repository.isBookmarked(url)) {
                repository.deleteBookmarkByUrl(url)
            } else {
                repository.insertBookmark(Bookmark(url = url, title = if (title.isEmpty()) url else title))
            }
        }
    }

    fun deleteHistory(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Config Toggles
    fun setJavaScriptEnabled(enabled: Boolean) {
        _isJavaScriptEnabled.value = enabled
    }

    fun setCacheEnabled(enabled: Boolean) {
        _isCacheEnabled.value = enabled
        viewModelScope.launch {
            _browserActions.emit(BrowserAction.ClearCache)
        }
    }

    // Developer Console Logs
    fun addConsoleLog(message: String, level: String) {
        val newLog = ConsoleLog(message, level)
        val current = _consoleLogs.value.takeLast(99) // Limit to 100 items for memory
        _consoleLogs.value = current + newLog
    }

    fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    // Helper: Smart url resolver
    private fun resolveUrlOrSearch(input: String, engineUrl: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "about:blank"

        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true) ||
                trimmed.startsWith("about:", ignoreCase = true)

        if (hasScheme) {
            return trimmed
        }

        // Detect if matches common domain name pattern
        val urlPattern = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$".toRegex()
        if (urlPattern.matches(trimmed)) {
            return "https://$trimmed"
        }

        return try {
            "$engineUrl${URLEncoder.encode(trimmed, "UTF-8")}"
        } catch (e: UnsupportedEncodingException) {
            "$engineUrl$trimmed"
        }
    }
}
