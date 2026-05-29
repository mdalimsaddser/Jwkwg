package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Bookmark
import com.example.data.HistoryItem
import com.example.ui.theme.*
import com.example.viewmodel.BrowserAction
import com.example.viewmodel.BrowserViewModel
import com.example.viewmodel.ConsoleLog
import com.example.viewmodel.WebTabState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel(),
    onEmailDeveloper: () -> Unit
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentEngineIndex by viewModel.currentEngineIndex.collectAsState()
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val javascriptEnabled by viewModel.isJavaScriptEnabled.collectAsState()
    val cacheEnabled by viewModel.isCacheEnabled.collectAsState()
    val userAgentIndex by viewModel.userAgentIndex.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Active Tab Helper computed from tabs and activeTabId
    val activeTab = tabs.find { it.id == activeTabId }

    // Tab-specific WebViews map to preserve state of every page open in background
    val webViewMap = remember { mutableStateMapOf<Int, WebView>() }
    val context = LocalContext.current

    // Modal Sheet Controllers
    var showTabsSheet by remember { mutableStateOf(false) }
    var showDeveloperSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    // Script Inspector Source State
    var htmlSourceCode by remember { mutableStateOf("") }
    var inspectingSourceTitle by remember { mutableStateOf("") }

    // Setup action triggers for current WebViews
    LaunchedEffect(activeTabId) {
        viewModel.browserActions.collectLatest { action ->
            val activeWebView = webViewMap[activeTabId]
            if (activeWebView != null) {
                when (action) {
                    is BrowserAction.LoadUrl -> activeWebView.loadUrl(action.url)
                    is BrowserAction.GoBack -> if (activeWebView.canGoBack()) activeWebView.goBack()
                    is BrowserAction.GoForward -> if (activeWebView.canGoForward()) activeWebView.goForward()
                    is BrowserAction.Reload -> activeWebView.reload()
                    is BrowserAction.StopLoading -> activeWebView.stopLoading()
                    is BrowserAction.InjectJS -> activeWebView.evaluateJavascript(action.script, null)
                    is BrowserAction.ClearCache -> activeWebView.clearCache(true)
                    else -> {}
                }
            }
        }
    }

    // Tab list sync triggers: clean up closed WebView elements
    LaunchedEffect(tabs) {
        val openTabIds = tabs.map { it.id }.toSet()
        val webViewIds = webViewMap.keys.toList()
        webViewIds.forEach { id ->
            if (!openTabIds.contains(id)) {
                webViewMap[id]?.destroy()
                webViewMap.remove(id)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                // Address & Search Hub
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search Engine Switcher
                    var showEngineMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showEngineMenu = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("engine_dropdown_btn")
                        ) {
                            Icon(
                                imageVector = when (viewModel.searchEngines.getOrNull(currentEngineIndex)?.first) {
                                    "DuckDuckGo" -> Icons.Default.Public
                                    "Bing" -> Icons.Default.Search
                                    else -> Icons.Default.Language
                                },
                                contentDescription = "Search Engine Selector",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showEngineMenu,
                            onDismissRequest = { showEngineMenu = false }
                        ) {
                            viewModel.searchEngines.forEachIndexed { idx, (name, _) ->
                                DropdownMenuItem(
                                    text = { Text(name, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.selectSearchEngine(idx)
                                        showEngineMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Main Text Input
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("address_bar_input"),
                        placeholder = {
                            Text(
                                text = "Enter URL or Search...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(26.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = if (activeTab?.url?.startsWith("https://") == true) Icons.Default.Lock else Icons.Default.Language,
                                contentDescription = "Security State",
                                tint = if (activeTab?.url?.startsWith("https://") == true) TechGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.updateSearchQuery("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear address bar",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                val starFilled = activeTab?.let { active ->
                                    val isBookmarked by viewModel.bookmarks.collectAsState()
                                    isBookmarked.any { it.url == active.url }
                                } ?: false

                                IconButton(
                                    onClick = {
                                        activeTab?.let {
                                            viewModel.toggleBookmark(it.url, it.title)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (starFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Bookmark site",
                                        tint = if (starFilled) Color.Yellow else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (searchQuery.isNotEmpty()) {
                                    viewModel.loadUrl(searchQuery)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Loading/Stop/Reload Hub
                    IconButton(
                        onClick = {
                            if (activeTab?.isLoading == true) {
                                viewModel.stop()
                            } else {
                                viewModel.reload()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                            .testTag("action_go_or_reload_btn")
                    ) {
                        Icon(
                            imageVector = if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = "Reload or Stop",
                            tint = if (activeTab?.isLoading == true) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Smooth M3 Progress Indicator
                if (activeTab?.isLoading == true && activeTab.progress < 100) {
                    LinearProgressIndicator(
                        progress = { activeTab.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                } else {
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp
            ) {
                // Back Button
                NavigationBarItem(
                    selected = false,
                    onClick = { viewModel.goBack() },
                    enabled = activeTab?.canGoBack == true,
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    },
                    label = { Text("Back", fontSize = 11.sp) }
                )

                // Forward Button
                NavigationBarItem(
                    selected = false,
                    onClick = { viewModel.goForward() },
                    enabled = activeTab?.canGoForward == true,
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go forward"
                        )
                    },
                    label = { Text("Forward", fontSize = 11.sp) }
                )

                // Bookmarks & History Controller
                NavigationBarItem(
                    selected = showBookmarksSheet,
                    onClick = { showBookmarksSheet = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Saved directory"
                        )
                    },
                    label = { Text("Saved", fontSize = 11.sp) }
                )

                // Tab Manager Trigger with custom open tabs size Badge
                NavigationBarItem(
                    selected = showTabsSheet,
                    onClick = { showTabsSheet = true },
                    icon = {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(tabs.size.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tab,
                                contentDescription = "Tab Manager"
                            )
                        }
                    },
                    label = { Text("Tabs", fontSize = 11.sp) }
                )

                // Advanced Developer Web Tools Shell
                NavigationBarItem(
                    selected = showDeveloperSheet,
                    onClick = { showDeveloperSheet = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Developer Console",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    label = {
                        Text(
                            text = "Console",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (activeTab != null) {
                if (activeTab.url == "about:blank") {
                    // Render the majestic Md Sabbir advanced Launchpad
                    LauncherHome(
                        viewModel = viewModel,
                        onEmailDeveloper = onEmailDeveloper,
                        onOpenDeveloperConsole = { showDeveloperSheet = true }
                    )
                } else {
                    // Load the core WebView layout
                    key(activeTab.id) {
                        WebViewContainer(
                            tabState = activeTab,
                            viewModel = viewModel,
                            webViewMap = webViewMap,
                            javascriptEnabled = javascriptEnabled,
                            cacheEnabled = cacheEnabled,
                            userAgentIndex = userAgentIndex
                        )
                    }
                }
            } else {
                // Fallback, if somehow no tabs are open
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { viewModel.addNewTab() }) {
                        Text("Create Web Tab")
                    }
                }
            }
        }
    }

    // Modal Sheet 1: TAB LIST SHEETS
    if (showTabsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTabsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TabsManagerSheetContent(
                tabs = tabs,
                activeTabId = activeTabId,
                onSelectTab = {
                    viewModel.selectTab(it)
                    showTabsSheet = false
                },
                onCloseTab = { viewModel.closeTab(it) },
                onAddTab = {
                    viewModel.addNewTab()
                    showTabsSheet = false
                },
                onClearAll = {
                    viewModel.clearAllTabs()
                }
            )
        }
    }

    // Modal Sheet 2: SAVED CHANNELS (BOOKMARKS & HISTORY)
    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            BookmarksHistorySheetContent(
                viewModel = viewModel,
                onNavigateToUrl = { url ->
                    viewModel.loadUrl(url)
                    showBookmarksSheet = false
                }
            )
        }
    }

    // Modal Sheet 3: DEVELOPER ADVANCED CONTROL TERMINAL
    if (showDeveloperSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDeveloperSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            DeveloperToolsSheetContent(
                viewModel = viewModel,
                activeTab = activeTab,
                consoleLogs = consoleLogs,
                isJavaScriptEnabled = javascriptEnabled,
                isCacheEnabled = cacheEnabled,
                userAgentIndex = userAgentIndex,
                onDismiss = { showDeveloperSheet = false },
                onInspectSource = { title, html ->
                    inspectingSourceTitle = title
                    htmlSourceCode = html
                }
            )
        }
    }

    // Full screen Source Code inspection modal
    if (htmlSourceCode.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { htmlSourceCode = "" },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source: $inspectingSourceTitle",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { htmlSourceCode = "" }) {
                        Icon(Icons.Default.Close, "Dismiss Source Inspector")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "RAW OUTER HTML INSPECTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        color = Color.Black
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            item {
                                Text(
                                    text = htmlSourceCode,
                                    color = TechGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// -------------------------------------------------------------
// MAIN HOME / PORTAL LAUNCHPAD COMPOSABLE
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherHome(
    viewModel: BrowserViewModel,
    onEmailDeveloper: () -> Unit,
    onOpenDeveloperConsole: () -> Unit
) {
    val searchEngineIndex by viewModel.currentEngineIndex.collectAsState()
    val activeEngine = viewModel.searchEngines[searchEngineIndex]
    var innerSearchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val context = LocalContext.current

    // Digital clock state
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            currentTime = sdf.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Top Branding Header Component
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag("branding_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Advance Browser",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "DEV BY MD SABBIR",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.5.sp
                    )
                }

                // Monogram Avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Compact Digital Clock Visual
            Text(
                text = currentTime,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.testTag("digital_clock")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Search Bar Pill (MD3 Pill style)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        RoundedCornerShape(30.dp)
                    )
                    .testTag("md3_search_pill"),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = innerSearchQuery,
                        onValueChange = { innerSearchQuery = it },
                        placeholder = {
                            Text(
                                "Search with ${activeEngine.first} or enter URL",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("launcher_search_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (innerSearchQuery.isNotEmpty()) {
                                    viewModel.loadUrl(innerSearchQuery)
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    )
                    if (innerSearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { innerSearchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search query",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Quick Links Grid (Clean Minimalism Style with letter initials & subtle labels)
            val tiles = listOf(
                QuickTile("Google", "https://www.google.com", "G", Color(0xFF4285F4)),
                QuickTile("YouTube", "https://www.youtube.com", "Y", Color(0xFFEA4335)),
                QuickTile("GitHub", "https://github.com", "G", Color(0xFF24292E)),
                QuickTile("StackOverflow", "https://stackoverflow.com", "S", Color(0xFFF48024)),
                QuickTile("MDN Web", "https://developer.mozilla.org", "M", Color(0xFF005FB8)),
                QuickTile("GPT AI", "https://chatgpt.com", "C", Color(0xFF10A37F)),
                QuickTile("Wikipedia", "https://wikipedia.org", "W", Color(0xFF72777D)),
                QuickTile("Compose", "https://developer.android.com/compose", "A", Color(0xFF3DDC84))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "QUICK LINKS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(4).forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.loadUrl(tile.url) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Card(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tile.letter,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tile.color
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tile.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowTiles.size < 4) {
                            repeat(4 - rowTiles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Developer Mode Purple Accent Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)) // Elegant Lilac/Purple Container
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Developer Mode",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF21005D)
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF21005D), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Welcome to Sabbir's custom build. Contact for inquiry/support: Sabbir347iii@gmail.com",
                        fontSize = 13.sp,
                        color = Color(0xFF21005D).copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenDeveloperConsole,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF21005D),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Text("Open Dev Console", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onEmailDeveloper,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.4f),
                                contentColor = Color(0xFF21005D)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Developer",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Contact Info", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Latest Discoveries Feed Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "LATEST DISCOVERIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val feedItems = listOf(
                        FeedItem("Compose Adaptive Layouts", "A comprehensive guide on dynamic grids, window class responsive sizing, and nested list structures.", "Android Devs", "https://developer.android.com/develop/ui/compose/layouts/adaptive"),
                        FeedItem("Chrome Sandbox Isolation", "Understanding Javascript isolates, client-side evaluation contexts, and memory tracking in webviews.", "Mozilla MDN", "https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy"),
                        FeedItem("Slick Kotlin Flow Architectures", "Best practices on StateFlow, SharedFlow, and stateIn in Clean Architecture apps.", "Sabbir Tech Hub", "https://kotlinlang.org/docs/flow.html")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        feedItems.forEach { feed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadUrl(feed.url) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = "Read article",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = feed.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = feed.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "by ${feed.author}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class QuickTile(
    val name: String,
    val url: String,
    val letter: String,
    val color: Color
)

data class FeedItem(
    val title: String,
    val description: String,
    val author: String,
    val url: String
)


// -------------------------------------------------------------
// INDIVIDUAL WEBVIEW CONTROLLER COMPOSABLE WITH SANDBOX AGENTS
// -------------------------------------------------------------
@Composable
fun WebViewContainer(
    tabState: WebTabState,
    viewModel: BrowserViewModel,
    webViewMap: MutableMap<Int, WebView>,
    javascriptEnabled: Boolean,
    cacheEnabled: Boolean,
    userAgentIndex: Int
) {
    val context = LocalContext.current

    val webView = remember(tabState.id) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Setup state delegates
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    viewModel.onProgressChanged(tabState.id, newProgress)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage != null) {
                        viewModel.addConsoleLog(
                            message = "${consoleMessage.message()} (line: ${consoleMessage.lineNumber()} in ${consoleMessage.sourceId()})",
                            level = when (consoleMessage.messageLevel()) {
                                ConsoleMessage.MessageLevel.WARNING -> "warn"
                                ConsoleMessage.MessageLevel.ERROR -> "error"
                                else -> "log"
                            }
                        )
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let { viewModel.onPageStarted(tabState.id, it) }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let { viewModel.onPageFinished(tabState.id, it, view?.title) }
                    // Update flags
                    viewModel.updateNavigationState(
                        tabState.id,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    viewModel.updateNavigationState(
                        tabState.id,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }
            }

            // Standard secure configuration defaults
            settings.apply {
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
            }

            webViewMap[tabState.id] = this
        }
    }

    // Reactively update settings upon VM state flows changes
    LaunchedEffect(javascriptEnabled) {
        webView.settings.javaScriptEnabled = javascriptEnabled
    }

    LaunchedEffect(cacheEnabled) {
        webView.settings.cacheMode = if (cacheEnabled) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_NO_CACHE
        }
    }

    LaunchedEffect(userAgentIndex) {
        val userAgent = viewModel.userAgents[userAgentIndex].second
        webView.settings.userAgentString = userAgent
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize().testTag("webview_shell_view")
    )
}


// -------------------------------------------------------------
// TAB MANAGER MODAL PANEL
// -------------------------------------------------------------
@Composable
fun TabsManagerSheetContent(
    tabs: List<WebTabState>,
    activeTabId: Int?,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onAddTab: () -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tabs Manager (${tabs.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Clear All")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onAddTab,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Add, "New")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Tab")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tabs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No open tabs. Tap '+' to begin.", color = MutedSlateText)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tabs) { tab ->
                    val isActive = tab.id == activeTabId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTab(tab.id) },
                        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tab.url,
                                    fontSize = 11.sp,
                                    color = MutedSlateText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            IconButton(onClick = { onCloseTab(tab.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close tab")
                            }
                        }
                    }
                }
            }
        }
    }
}


// -------------------------------------------------------------
// DATABASE DRIVEN SAVED COMPONENT (BOOKMARKS & HISTORY)
// -------------------------------------------------------------
@Composable
fun BookmarksHistorySheetContent(
    viewModel: BrowserViewModel,
    onNavigateToUrl: (String) -> Unit
) {
    var activeTabIdx by remember { mutableStateOf(0) } // 0 = Bookmarks, 1 = History

    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(16.dp)
    ) {
        // Tab Segment Selection Control
        TabRow(selectedTabIndex = activeTabIdx) {
            Tab(
                selected = activeTabIdx == 0,
                onClick = { activeTabIdx = 0 },
                text = { Text("Bookmarks (${bookmarks.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTabIdx == 1,
                onClick = { activeTabIdx = 1 },
                text = { Text("History (${history.size})", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeTabIdx == 0) {
            // BOOKMARKS LIST PANEL
            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.StarOutline,
                            contentDescription = "Empty Bookmarks",
                            tint = MutedSlateText,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No saved bookmarks yet.", color = MutedSlateText)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(bookmarks) { bookmark ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToUrl(bookmark.url) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bookmark.url,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleBookmark(bookmark.url, bookmark.title) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Bookmark",
                                        tint = Color.Red.copy(0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // CHRONOLOGICAL SEARCH HISTORY
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear database history")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wipe Browser Cache/History")
                    }
                }

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Empty History",
                                tint = MutedSlateText,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Your browsing history is clean.", color = MutedSlateText)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(history) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToUrl(item.url) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.url,
                                            fontSize = 10.sp,
                                            color = MutedSlateText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteHistory(item.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete item",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// -------------------------------------------------------------
// ADVANCED DEVELOPER SHELL, PREFERRED SCRIPTS & JS SHELL
// -------------------------------------------------------------
@Composable
fun DeveloperToolsSheetContent(
    viewModel: BrowserViewModel,
    activeTab: WebTabState?,
    consoleLogs: List<ConsoleLog>,
    isJavaScriptEnabled: Boolean,
    isCacheEnabled: Boolean,
    userAgentIndex: Int,
    onDismiss: () -> Unit,
    onInspectSource: (String, String) -> Unit
) {
    var devTabIdx by remember { mutableStateOf(0) } // 0 = Console, 1 = Injection Engine, 2 = settings
    var customScriptToExecute by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .padding(16.dp)
    ) {
        // Tab Header Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SANDBOX DEVELOPER HUBS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Dismiss dev console")
            }
        }

        TabRow(selectedTabIndex = devTabIdx) {
            Tab(selected = devTabIdx == 0, onClick = { devTabIdx = 0 }, text = { Text("Log Console") })
            Tab(selected = devTabIdx == 1, onClick = { devTabIdx = 1 }, text = { Text("JS Injector") })
            Tab(selected = devTabIdx == 2, onClick = { devTabIdx = 2 }, text = { Text("Config API") })
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (devTabIdx) {
            0 -> {
                // TAB 0: LIVE CONSOLE TERMINAL
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Standard JS Console Output:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { viewModel.clearConsoleLogs() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Shell", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        color = Color(0xFF030A14)
                    ) {
                        if (consoleLogs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No logs output. Execute JS or load dynamic pages",
                                    color = MutedSlateText,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                items(consoleLogs) { log ->
                                    val logColor = when (log.level) {
                                        "error" -> Color.Red
                                        "warn" -> Color.Yellow
                                        else -> TechGreen
                                    }
                                    val timestampStr = remember(log.timestamp) {
                                        val date = Date(log.timestamp)
                                        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                                        format.format(date)
                                    }
                                    Text(
                                        text = "[$timestampStr] ${log.level.uppercase()}: ${log.message}",
                                        color = logColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: JS EXECUTION & OUTSOURCING SCRIPT ENG
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "JavaScript Sandboxed Injector",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Execute direct JS statements or scripts on the current active tab context.",
                        fontSize = 11.sp,
                        color = MutedSlateText
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customScriptToExecute,
                        onValueChange = { customScriptToExecute = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text("Code editor (JavaScript)") },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        placeholder = { Text("alert('Loaded by Sabbir Pro Browser');") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (customScriptToExecute.isNotEmpty()) {
                                    viewModel.injectJS(customScriptToExecute)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Send, "Execute")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Javascript", fontWeight = FontWeight.Black)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // PREFERRED QUICK UTILITY INJECTIONS
                    Text(
                        text = "READY-MADE SHELL UTILITIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val quickTemplates = listOf(
                        "Dark Mode Override" to "(function(){ var d=document.createElement('style'); d.innerHTML='html { filter: invert(1) hue-rotate(180deg) !important; img, video { filter: invert(1) hue-rotate(180deg) !important; } }'; document.head.appendChild(d); })();",
                        "Audit Forms Input Fields" to "alert('Detected inputs: ' + document.getElementsByTagName('input').length);",
                        "Invert Layout Colors" to "document.body.style.filter = 'invert(1)';",
                        "Reveal Saved Cookies" to "alert(document.cookie || 'No cookies loaded on scope');",
                        "Extract Page Images" to "alert(Array.from(document.getElementsByTagName('img')).map(i=>i.src).join('\\n'));"
                    )

                    quickTemplates.forEach { (name, code) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.injectJS(code)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.PlayArrow, "Inject UI", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            2 -> {
                // TAB 2: SYSTEM AND USER-AGENT SHELL
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Browser Runtime Settings",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // JavaScript Toggle state
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Javascript Sandbox", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Crucial for web apps and loading dynamic logic.", fontSize = 11.sp, color = MutedSlateText)
                        }
                        Switch(
                            checked = isJavaScriptEnabled,
                            onCheckedChange = { viewModel.setJavaScriptEnabled(it) }
                        )
                    }

                    // Cache Toggle State
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Dynamic Web-Cache", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Speeds up loads. Disabling clears current data.", fontSize = 11.sp, color = MutedSlateText)
                        }
                        Switch(
                            checked = isCacheEnabled,
                            onCheckedChange = { viewModel.setCacheEnabled(it) }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Page Inspector
                    Text(
                        text = "ADVANCED SOURCE INSPECTOR API",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val activeWebView = remember { mutableStateOf<WebView?>(null) }
                    // Inspect HTML source
                    Button(
                        onClick = {
                            viewModel.injectJS("(function() { return document.documentElement.outerHTML; })();")
                            // Retrieve the HTML and present via alert
                            activeTab?.let { active ->
                                // Trigger temporary JS return interception
                                // Since we evaluate JS asynchronously, we can assign JS to return source via logs
                                viewModel.injectJS("console.log('SABBIR_SOURCE_CODE:' + document.documentElement.outerHTML);")
                                // Also inject alert for user-friendly manual copy
                                viewModel.injectJS("alert('Source Code sent to Log Console in real time with SABBIR_SOURCE_CODE prefix! Check your Console logs tab.');")
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.MenuBook, "Book")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Outer HTML to Log Console")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Customizable User Agent selectors
                    Text(
                        text = "CLIENT USER-AGENT OVERRIDES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    viewModel.userAgents.forEachIndexed { idx, (agentName, agentVal) ->
                        val isAgentSelected = idx == userAgentIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.selectUserAgent(idx)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAgentSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            border = if (isAgentSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(agentName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    agentVal,
                                    fontSize = 10.sp,
                                    color = MutedSlateText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Simple internal helper to unescape strings returned by Javascript
fun unescapeJson(escaped: String): String {
    var s = escaped
    if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
        s = s.substring(1, s.length - 1)
    }
    return s.replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
}
