package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.models.*
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.CineStreamLoadingButton
import com.example.ui.components.CineStreamRefreshButton
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.viewmodels.DashboardViewModel
import com.example.viewmodels.UserFilter
import com.example.viewmodels.UserSort
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    onUserClick: (String) -> Unit,
    onNavigateToSupport: () -> Unit = {},
    onNavigateToProRequests: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onNavigateToAuditLogs: () -> Unit = {},
    onNavigateToEconomy: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val currentLang by AppSettings.language.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val userMetricsState by viewModel.userMetrics.collectAsState()
    val restrictionMetricsState by viewModel.featureRestrictionMetrics.collectAsState()
    val supportMetricsState by viewModel.supportMetrics.collectAsState()
    val proRequestMetricsState by viewModel.proRequestMetrics.collectAsState()
    val extensionMetricsState by viewModel.extensionMetrics.collectAsState()
    val recentActivityState by viewModel.recentActivity.collectAsState()

    val users by viewModel.users.collectAsState()
    val totalUsers by viewModel.totalUsers.collectAsState()
    val activeUsers by viewModel.activeUsers.collectAsState()
    val inactiveUsers by viewModel.inactiveUsers.collectAsState()
    val proUsersCount by viewModel.proUsersCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()
    val currentSort by viewModel.sort.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showAddUserDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    var userToToggleBan by remember { mutableStateOf<User?>(null) }
    var showFilterDropdown by remember { mutableStateOf(false) }

    var isHeaderCollapsed by remember { mutableStateOf(false) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    var maxCollapsePx by remember { mutableFloatStateOf(350f) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val handleVerticalDrag: (Float) -> Unit = { dragAmount ->
        val max = if (maxCollapsePx > 0f) maxCollapsePx else 350f
        val newOffset = if (headerOffsetPx >= 0f && dragAmount > 0f) {
            (headerOffsetPx + dragAmount * 0.35f).coerceAtMost(100f)
        } else if (headerOffsetPx > 0f && dragAmount < 0f) {
            (headerOffsetPx + dragAmount).coerceAtLeast(-max)
        } else {
            (headerOffsetPx + dragAmount).coerceIn(-max - 20f, 100f)
        }
        headerOffsetPx = newOffset
        isHeaderCollapsed = headerOffsetPx < -max * 0.5f
    }

    val handleDragEnd: () -> Unit = {
        coroutineScope.launch {
            val max = if (maxCollapsePx > 0f) maxCollapsePx else 350f
            val target = if (headerOffsetPx < -max * 0.35f) -max else 0f
            Animatable(headerOffsetPx).animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) {
                headerOffsetPx = value
            }
            isHeaderCollapsed = target != 0f
        }
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handleVerticalDrag(dragAmount)
            },
            onDragEnd = handleDragEnd,
            onDragCancel = handleDragEnd
        )
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && headerOffsetPx < 0f) {
                    val isAtTop = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
                    if (isAtTop) {
                        val prev = headerOffsetPx
                        val max = if (maxCollapsePx > 0f) maxCollapsePx else 350f
                        headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-max, 0f)
                        isHeaderCollapsed = headerOffsetPx < -max * 0.5f
                        val consumed = headerOffsetPx - prev
                        return Offset(0f, consumed)
                    }
                }
                return Offset.Zero
            }
        }
    }

    AdaptiveScreenContainer(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Phase C6: Top-Level Dashboard Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DashboardTabPill(
                    title = AppStrings.tabOverview(currentLang),
                    icon = Icons.Default.Dashboard,
                    selected = currentTab == DashboardTab.OVERVIEW,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTab(DashboardTab.OVERVIEW) }
                )
                DashboardTabPill(
                    title = AppStrings.tabUsers(currentLang),
                    icon = Icons.Default.Group,
                    selected = currentTab == DashboardTab.USERS,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTab(DashboardTab.USERS) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (currentTab == DashboardTab.OVERVIEW) {
                DashboardOverviewContent(
                    userMetricsState = userMetricsState,
                    restrictionMetricsState = restrictionMetricsState,
                    supportMetricsState = supportMetricsState,
                    proRequestMetricsState = proRequestMetricsState,
                    extensionMetricsState = extensionMetricsState,
                    recentActivityState = recentActivityState,
                    isRefreshing = isRefreshing,
                    currentLang = currentLang,
                    onRefresh = viewModel::refresh,
                    onNavigateToUsers = { viewModel.setTab(DashboardTab.USERS) },
                    onNavigateToSupport = onNavigateToSupport,
                    onNavigateToProRequests = onNavigateToProRequests,
                    onNavigateToExtensions = onNavigateToExtensions,
                    onNavigateToAuditLogs = onNavigateToAuditLogs,
                    onNavigateToEconomy = onNavigateToEconomy,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // User Management Subsystem
                Column(modifier = Modifier.weight(1f)) {
                    // Upper Section: Responds to vertical drag anywhere on its surface
                    // Contains ALL elements above the users list (Title, Metrics, Search, Filters)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragModifier)
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                if (placeable.height > 0) {
                                    maxCollapsePx = placeable.height.toFloat()
                                }
                                val max = if (maxCollapsePx > 0f) maxCollapsePx else placeable.height.toFloat()
                                val collapse = (-headerOffsetPx).coerceIn(0f, max)
                                val overscroll = if (headerOffsetPx > 0f) headerOffsetPx else 0f
                                val currentHeight = (placeable.height - collapse + overscroll).toInt().coerceAtLeast(0)
                                layout(placeable.width, currentHeight) {
                                    val yOffset = if (headerOffsetPx > 0f) overscroll.toInt() else (-collapse).toInt()
                                    placeable.placeRelative(0, yOffset)
                                }
                            }
                    ) {
                        // Title and "Add User" Action Button Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = AppStrings.users(currentLang),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = AppStrings.usersSubtitle(currentLang),
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CineStreamRefreshButton(
                                        isRefreshing = isRefreshing,
                                        onClick = { viewModel.refresh() },
                                        contentDescription = AppStrings.refresh(currentLang)
                                    )

                                    // Primary Add User Button (CineStream Red) with tactile bounce
                                    CineStreamLoadingButton(
                                        onClick = { showAddUserDialog = true },
                                        leadingIcon = Icons.Default.PersonAdd,
                                        containerColor = CineStreamRed,
                                        shape = RoundedCornerShape(12.dp),
                                        text = AppStrings.addUser(currentLang),
                                        modifier = Modifier.testTag("add_user_button")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Single-Row 4-Category Strip: Compact, unified, aligned (Total, Active, PRO, Inactive)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CompactMetricCard(
                                    value = totalUsers.toString(),
                                    label = if (currentLang == AppLanguage.ARABIC) "الإجمالي" else AppStrings.totalUsers(currentLang),
                                    icon = Icons.Default.Group,
                                    accentColor = MetricBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                CompactMetricCard(
                                    value = activeUsers.toString(),
                                    label = if (currentLang == AppLanguage.ARABIC) "نشط الآن" else AppStrings.activeNow(currentLang),
                                    icon = Icons.Default.TrendingUp,
                                    accentColor = MetricGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                CompactMetricCard(
                                    value = proUsersCount.toString(),
                                    label = if (currentLang == AppLanguage.ARABIC) "مشتركو PRO" else "PRO",
                                    icon = Icons.Default.Star,
                                    accentColor = MetricPurple,
                                    modifier = Modifier.weight(1f)
                                )
                                CompactMetricCard(
                                    value = inactiveUsers.toString(),
                                    label = if (currentLang == AppLanguage.ARABIC) "غير نشط" else AppStrings.inactive(currentLang),
                                    icon = Icons.Default.PersonOff,
                                    accentColor = MetricRed,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                        // Search Input and Filter Tune Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("user_search_input"),
                                placeholder = {
                                    Text(
                                        AppStrings.searchUsersPlaceholder(currentLang),
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = AppStrings.search(currentLang),
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = AppStrings.reset(currentLang), tint = TextSecondary)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = DarkSurface,
                                    focusedContainerColor = DarkSurface,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedBorderColor = CineStreamRed,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Filter / Tune Squircle Button
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(DarkSurface, RoundedCornerShape(14.dp))
                                        .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
                                        .testTag("filter_tune_button")
                                        .clickable { showFilterDropdown = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Tune Filters",
                                        tint = if (currentFilter != UserFilter.ALL || currentSort != UserSort.NEWEST) CineStreamRed else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showFilterDropdown,
                                    onDismissRequest = { showFilterDropdown = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(AppStrings.sortNewestFirst(currentLang), color = Color.White) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = CineStreamRed) },
                                        onClick = {
                                            viewModel.setSort(UserSort.NEWEST)
                                            showFilterDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(AppStrings.sortRecent(currentLang), color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = TextSecondary) },
                                        onClick = {
                                            viewModel.setSort(UserSort.RECENT_LOGIN)
                                            showFilterDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(AppStrings.sortName(currentLang), color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = TextSecondary) },
                                        onClick = {
                                            viewModel.setSort(UserSort.USERNAME)
                                            showFilterDropdown = false
                                        }
                                    )
                                    HorizontalDivider(color = DarkCardBorder)
                                    DropdownMenuItem(
                                        text = { Text(AppStrings.resetSearchAndFilters(currentLang), color = CineStreamRed) },
                                        leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = CineStreamRed) },
                                        onClick = {
                                            viewModel.resetFilters()
                                            showFilterDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter Chips Row (Matching screenshot chips: All, Active, Inactive, PRO, Banned, Sort)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "All" Chip
                            FilterChipItem(
                                label = AppStrings.filterAll(currentLang),
                                icon = Icons.Default.Group,
                                isSelected = currentFilter == UserFilter.ALL,
                                onClick = { viewModel.setFilter(UserFilter.ALL) },
                                modifier = Modifier.testTag("filter_chip_all")
                            )

                            // "Active" Chip (with Green dot indicator)
                            StatusFilterChip(
                                label = AppStrings.filterActive(currentLang),
                                dotColor = MetricGreen,
                                isSelected = currentFilter == UserFilter.ACTIVE,
                                onClick = { viewModel.setFilter(UserFilter.ACTIVE) },
                                modifier = Modifier.testTag("filter_chip_active")
                            )

                            // "Inactive" Chip (with Grey dot indicator)
                            StatusFilterChip(
                                label = AppStrings.filterInactive(currentLang),
                                dotColor = TextSecondary,
                                isSelected = currentFilter == UserFilter.INACTIVE,
                                onClick = { viewModel.setFilter(UserFilter.INACTIVE) },
                                modifier = Modifier.testTag("filter_chip_inactive")
                            )

                            // "PRO" Chip (with Crown/Star)
                            FilterChipItem(
                                label = AppStrings.filterPro(currentLang),
                                icon = Icons.Default.Star,
                                isSelected = currentFilter == UserFilter.PREMIUM,
                                onClick = { viewModel.setFilter(UserFilter.PREMIUM) },
                                iconTint = MetricPurple,
                                modifier = Modifier.testTag("filter_chip_pro")
                            )

                            // "Banned / Restricted" Chip (with Block icon)
                            FilterChipItem(
                                label = AppStrings.filterBanned(currentLang),
                                icon = Icons.Default.Block,
                                isSelected = currentFilter == UserFilter.BANNED,
                                onClick = { viewModel.setFilter(UserFilter.BANNED) },
                                iconTint = MetricRed,
                                modifier = Modifier.testTag("filter_chip_banned")
                            )

                            // Sort Toggle Chip ("Newest First")
                            Box(
                                modifier = Modifier
                                    .background(DarkSurface, RoundedCornerShape(20.dp))
                                    .border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp))
                                    .clickable {
                                        val nextSort = when (currentSort) {
                                            UserSort.NEWEST -> UserSort.RECENT_LOGIN
                                            UserSort.RECENT_LOGIN -> UserSort.USERNAME
                                            UserSort.USERNAME -> UserSort.NEWEST
                                        }
                                        viewModel.setSort(nextSort)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (currentSort) {
                                            UserSort.NEWEST -> AppStrings.sortNewestFirst(currentLang)
                                            UserSort.RECENT_LOGIN -> AppStrings.sortRecent(currentLang)
                                            UserSort.USERNAME -> AppStrings.sortName(currentLang)
                                        },
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Section Title: "User List" + Count Badge, Collapse/Expand Toggle, & Refresh Icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dragModifier),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = AppStrings.userListTitle(currentLang),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Pill badge showing count
                            Box(
                                modifier = Modifier
                                    .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = AppStrings.userCountBadge(users.size, currentLang),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Expand / Collapse Header Arrow Button
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val max = if (maxCollapsePx > 0f) maxCollapsePx else 350f
                                        val target = if (isHeaderCollapsed) 0f else -max
                                        Animatable(headerOffsetPx).animateTo(
                                            targetValue = target,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        ) {
                                            headerOffsetPx = value
                                        }
                                        isHeaderCollapsed = !isHeaderCollapsed
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("toggle_header_collapse_button")
                            ) {
                                Icon(
                                    imageVector = if (isHeaderCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isHeaderCollapsed) "Expand Header" else "Collapse Header",
                                    tint = if (isHeaderCollapsed) CineStreamRed else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { viewModel.refresh() },
                                enabled = !isRefreshing,
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = CineStreamRed
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = AppStrings.refresh(currentLang),
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // User List Items (Scrolls ONLY the users list when dragged from here)
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CineStreamRed)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(AppStrings.syncingUsers(currentLang), color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else if (users.isEmpty()) {
                        val isSearching = searchQuery.isNotBlank() || currentFilter != UserFilter.ALL
                        Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    if (isSearching) Icons.Default.SearchOff else Icons.Default.PeopleOutline,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (isSearching) AppStrings.noUsersMatch(searchQuery, currentLang) else AppStrings.noUsersYet(currentLang),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    AppStrings.usersAutoSyncDesc(currentLang),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                if (isSearching) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.resetFilters() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CineStreamRed),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(CineStreamRed, CineStreamRed)))
                                    ) {
                                        Text(AppStrings.resetSearchAndFilters(currentLang))
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .nestedScroll(nestedScrollConnection),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(users, key = { it.id }) { user ->
                                UserCardItem(
                                    user = user,
                                    onClick = { onUserClick(user.id) },
                                    onTogglePro = { viewModel.toggleUserPremium(user) },
                                    onToggleBan = { userToToggleBan = user },
                                    onDelete = { userToDelete = user }
                                )
                            }
                        }
                    }
                }
}
}
}

    // Add User Dialog
    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { username, email, role, isPro ->
                viewModel.createUser(
                    username = username,
                    email = email,
                    role = role,
                    isPremium = isPro,
                    onSuccess = { showAddUserDialog = false }
                )
            }
        )
    }

    // Confirm Delete User Dialog
    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = {
                Text(
                    AppStrings.confirmDeleteUserTitle(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "${AppStrings.confirmDeleteUserMsg(currentLang)}\n\n(${user.username} - ${user.email})",
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        viewModel.deleteUser(user.id)
                        userToDelete = null
                    },
                    containerColor = MetricRed,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.delete(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Confirm Ban / Unban User Dialog (prevents accidental one-tap actions)
    userToToggleBan?.let { user ->
        val isCurrentlyBanned = user.isAccountBanned
        if (isCurrentlyBanned) {
            AlertDialog(
                onDismissRequest = { userToToggleBan = null },
                title = {
                    Text(
                        AppStrings.unbanAccountBtn(currentLang),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (currentLang == AppLanguage.ARABIC)
                            "هل أنت متأكد من رغبتك في رفع الحظر عن المستخدم (${user.username}) واستعادة الحساب؟"
                        else
                            "Are you sure you want to lift the suspension for user (${user.username}) and restore account access?",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    CineStreamLoadingButton(
                        onClick = {
                            viewModel.toggleUserBan(user)
                            userToToggleBan = null
                        },
                        containerColor = MetricGreen,
                        shape = RoundedCornerShape(10.dp),
                        text = AppStrings.confirm(currentLang)
                    )
                },
                dismissButton = {
                    TextButton(onClick = { userToToggleBan = null }) {
                        Text(AppStrings.cancel(currentLang), color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        } else {
            AlertDialog(
                onDismissRequest = { userToToggleBan = null },
                title = {
                    Text(
                        AppStrings.banAccountBtn(currentLang),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (currentLang == AppLanguage.ARABIC)
                            "هل تريد حظر حساب المستخدم (${user.username})؟ للخيارات المتقدمة وتحديد المدة والسبب، افتح صفحة التفاصيل."
                        else
                            "Are you sure you want to suspend user (${user.username})? For custom duration and reasons, please open User Details.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    CineStreamLoadingButton(
                        onClick = {
                            viewModel.toggleUserBan(user)
                            userToToggleBan = null
                        },
                        containerColor = CineStreamRed,
                        shape = RoundedCornerShape(10.dp),
                        text = AppStrings.confirm(currentLang)
                    )
                },
                dismissButton = {
                    TextButton(onClick = { userToToggleBan = null }) {
                        Text(AppStrings.cancel(currentLang), color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}

/**
 * Ultra-compact single-line Metric Card designed for seamless 4-in-a-row display
 */
@Composable
private fun CompactMetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(10.dp))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Filter Chip Item (Capsule pill)
 */
@Composable
private fun FilterChipItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    iconTint: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) CineStreamRed else DarkSurface,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) CineStreamRed else DarkCardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .bounceClick(scaleDown = 0.93f, shape = RoundedCornerShape(20.dp)) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else (iconTint ?: TextSecondary),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else TextSecondary
            )
        }
    }
}

/**
 * Status Filter Chip (Active / Inactive with dot)
 */
@Composable
private fun StatusFilterChip(
    label: String,
    dotColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) CineStreamRed else DarkSurface,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) CineStreamRed else DarkCardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .bounceClick(scaleDown = 0.93f, shape = RoundedCornerShape(20.dp)) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isSelected) Color.White else dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else TextSecondary
            )
        }
    }
}

/**
 * User Item Card matching the reference design exactly
 */
@Composable
private fun UserCardItem(
    user: User,
    onClick: () -> Unit,
    onTogglePro: () -> Unit,
    onToggleBan: () -> Unit,
    onDelete: () -> Unit
) {
    val currentLang by AppSettings.language.collectAsState()
    val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val isActive = user.lastLoginTimestamp >= threshold
    val isBanned = user.isAccountBanned
    val isExpiredBan = user.isBanExpired
    val hasFeatureRestrictions = user.hasFeatureRestrictions
    var showMenu by remember { mutableStateOf(false) }

    val dateStr = if (user.lastLoginTimestamp > 0L) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(user.lastLoginTimestamp))
    } else {
        AppStrings.neverLoggedIn(currentLang)
    }

    val initialLetter = (user.username.firstOrNull() ?: user.email.firstOrNull() ?: 'U').uppercaseChar().toString()

    val statusColor = when {
        isBanned -> MetricRed
        isExpiredBan -> WarningOrange
        hasFeatureRestrictions -> WarningOrange
        isActive -> MetricGreen
        else -> TextSecondary
    }
    val statusText = when {
        isBanned -> if (currentLang == AppLanguage.ARABIC) "موقوف" else "BANNED"
        isExpiredBan -> if (currentLang == AppLanguage.ARABIC) "حظر منتهي" else "BAN EXPIRED"
        hasFeatureRestrictions -> AppStrings.restrictedBadge(currentLang)
        isActive -> AppStrings.activeStatus(currentLang)
        else -> AppStrings.inactiveStatus(currentLang)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
            .testTag("user_card_item_${user.id}")
            .bounceClick(scaleDown = 0.97f, shape = RoundedCornerShape(14.dp)) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Trailing status & arrow on the far edge (depending on RTL / LTR)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Date and Status (Active/Inactive dot)
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.widthIn(min = 90.dp)
        ) {
            Text(
                text = dateStr,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            color = statusColor,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Username and Email
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (user.subscriptionState == SubscriptionState.ACTIVE_PRO) {
                    Box(
                        modifier = Modifier
                            .background(MetricPurpleBg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, MetricPurple, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(user.subscriptionTier.uppercase(), color = MetricPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else if (user.subscriptionState == SubscriptionState.EXPIRED_PRO) {
                    Box(
                        modifier = Modifier
                            .background(WarningOrange.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, WarningOrange, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("EXP PRO", color = WarningOrange, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (user.role == "admin") {
                    Box(
                        modifier = Modifier
                            .background(CineStreamRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(AppStrings.adminBadge(currentLang), color = CineStreamRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = user.username.ifBlank { "User" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = user.email.ifBlank { user.id },
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Circular Avatar with Letter
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF202532), CircleShape)
                .border(1.dp, if (user.isSubscriptionActive) MetricPurple else DarkCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialLetter,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 3-dots Menu Button
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text(AppStrings.viewDetails(currentLang), color = Color.White) },
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = CineStreamRed) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (user.isSubscriptionActive) AppStrings.revokePro(currentLang) else AppStrings.upgradeToPro(currentLang),
                            color = MetricPurple
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = MetricPurple) },
                    onClick = {
                        showMenu = false
                        onTogglePro()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isBanned) AppStrings.unbanUser(currentLang) else AppStrings.restrictUser(currentLang),
                            color = if (isBanned) MetricGreen else MetricRed
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isBanned) Icons.Default.CheckCircle else Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isBanned) MetricGreen else MetricRed
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleBan()
                    }
                )
                HorizontalDivider(color = DarkCardBorder)
                DropdownMenuItem(
                    text = { Text(AppStrings.deleteUser(currentLang), color = MetricRed) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MetricRed) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Add New User Dialog
 */
@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (username: String, email: String, role: String, isPro: Boolean) -> Unit
) {
    val currentLang by AppSettings.language.collectAsState()
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isPro by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = CineStreamRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    AppStrings.addNewUserDialogTitle(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(AppStrings.username(currentLang), color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CineStreamRed,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(AppStrings.email(currentLang), color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CineStreamRed,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Toggle PRO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AppStrings.proAccountCheckbox(currentLang), color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = isPro,
                        onCheckedChange = { isPro = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MetricPurple
                        )
                    )
                }

                // Toggle Admin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(AppStrings.adminRoleCheckbox(currentLang), color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CineStreamRed
                        )
                    )
                }
            }
        },
        confirmButton = {
            CineStreamLoadingButton(
                onClick = {
                    if (username.isNotBlank() && email.isNotBlank()) {
                        onConfirm(username.trim(), email.trim(), if (isAdmin) "admin" else "user", isPro)
                    }
                },
                enabled = username.isNotBlank() && email.isNotBlank(),
                containerColor = CineStreamRed,
                shape = RoundedCornerShape(10.dp),
                text = AppStrings.add(currentLang)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel(currentLang), color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun DashboardTabPill(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .background(
                if (selected) CineStreamRed.copy(alpha = 0.2f) else Color.Transparent,
                shape
            )
            .border(
                1.dp,
                if (selected) CineStreamRed.copy(alpha = 0.8f) else Color.Transparent,
                shape
            )
            .bounceClick(scaleDown = 0.94f, shape = shape, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) CineStreamRed else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = if (selected) Color.White else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

