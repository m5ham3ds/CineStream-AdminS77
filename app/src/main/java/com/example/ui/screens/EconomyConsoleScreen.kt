package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.*
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodels.EconomyViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EconomyConsoleScreen(
    viewModel: EconomyViewModel = viewModel()
) {
    val currentLang by AppSettings.language.collectAsState()
    val isAuthorized by viewModel.isAuthorized.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val featureConfig by viewModel.featureConfig.collectAsState()
    val economyConfig by viewModel.economyConfig.collectAsState()
    val rewardTasks by viewModel.rewardTasks.collectAsState()
    val weeklyLeaderboard by viewModel.weeklyLeaderboard.collectAsState()
    val filteredUsers by viewModel.filteredUsers.collectAsState()
    val selectedUser by viewModel.selectedUser.collectAsState()
    val userTransactions by viewModel.userTransactions.collectAsState()
    val userSearchQuery by viewModel.userSearchQuery.collectAsState()
    val filteredAuditLogs by viewModel.filteredAuditLogs.collectAsState()
    val auditFilter by viewModel.auditFilter.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    if (!isAuthorized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(MetricRed, MetricRedBg))),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MetricRed, modifier = Modifier.size(54.dp))
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC) "صلاحيات الوصول مرفوضة" else "Access Restricted",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "هذا القسم مخصص حصرياً للمشرفين المعتمدين في /admins/{uid} مع enabled == true."
                            else "This section is restricted to authoritative administrators verified in /admins/{uid}.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        AdaptiveScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "وحدة إدارة الاقتصاد والنقاط" else "Economy & Points Console",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC)
                                "التحكم المركزي في ميزات النظام، أسعار الاشتراكات، المهام، ورصيد النقاط"
                                else "Authoritative control plane for features, subscription pricing & tasks",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = CineStreamRed,
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = Color.White,
                    edgePadding = 4.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = CineStreamRed,
                                height = 3.dp
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .testTag("economy_tab_row")
                ) {
                    val tabs = listOf(
                        Triple(0, if (currentLang == AppLanguage.ARABIC) "الملخص العام" else "Overview", Icons.Default.Dashboard),
                        Triple(1, if (currentLang == AppLanguage.ARABIC) "مفاتيح الميزات" else "Features", Icons.Default.ToggleOn),
                        Triple(2, if (currentLang == AppLanguage.ARABIC) "الأسعار والقواعد" else "Pricing & Rules", Icons.Default.MonetizationOn),
                        Triple(3, if (currentLang == AppLanguage.ARABIC) "مهام المكافآت" else "Tasks", Icons.Default.Assignment),
                        Triple(4, if (currentLang == AppLanguage.ARABIC) "أرصدة المستخدمين" else "User Points", Icons.Default.AccountBalanceWallet),
                        Triple(5, if (currentLang == AppLanguage.ARABIC) "المتصدرون" else "Leaderboard", Icons.Default.EmojiEvents),
                        Triple(6, if (currentLang == AppLanguage.ARABIC) "سجل العمليات" else "Audit Trail", Icons.Default.History)
                    )

                    tabs.forEach { (index, title, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.setSelectedTab(index) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (selectedTab == index) CineStreamRed else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) Color.White else TextSecondary
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content Views by Tab
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    when (selectedTab) {
                        0 -> EconomyOverviewTab(
                            currentLang = currentLang,
                            featureConfig = featureConfig,
                            economyConfig = economyConfig,
                            taskCount = rewardTasks.size,
                            leaderboardCycle = weeklyLeaderboard.cycleId,
                            onNavigateToTab = { viewModel.setSelectedTab(it) }
                        )
                        1 -> EconomyFeaturesTab(
                            currentLang = currentLang,
                            featureConfig = featureConfig,
                            onUpdateFeature = { key, state, comingSoon, disabled ->
                                viewModel.updateFeature(key, state, comingSoon, disabled)
                            }
                        )
                        2 -> EconomyPricingRulesTab(
                            currentLang = currentLang,
                            economyConfig = economyConfig,
                            onUpdatePrice = { sku, price -> viewModel.updateSubscriptionPrice(sku, price) },
                            onUpdateDaily = { list -> viewModel.updateDailyLoginRewards(list) },
                            onUpdateAds = { pts, cap, cd -> viewModel.updateRewardedAdsConfig(pts, cap, cd) }
                        )
                        3 -> EconomyTasksTab(
                            currentLang = currentLang,
                            tasks = rewardTasks,
                            onSaveTask = { task, isNew -> viewModel.saveRewardTask(task, isNew) },
                            onToggleActive = { viewModel.toggleTaskActive(it) },
                            onDeleteTask = { viewModel.deleteRewardTask(it) }
                        )
                        4 -> EconomyUserPointsTab(
                            currentLang = currentLang,
                            users = filteredUsers,
                            selectedUser = selectedUser,
                            searchQuery = userSearchQuery,
                            transactions = userTransactions,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onSelectUser = { viewModel.selectUser(it) },
                            onGrantPoints = { uid, amt, rsn -> viewModel.grantPoints(uid, amt, rsn) },
                            onRemovePoints = { uid, amt, rsn -> viewModel.removePoints(uid, amt, rsn) }
                        )
                        5 -> EconomyLeaderboardTab(
                            currentLang = currentLang,
                            leaderboard = weeklyLeaderboard
                        )
                        6 -> EconomyAuditTrailTab(
                            currentLang = currentLang,
                            auditLogs = filteredAuditLogs,
                            currentFilter = auditFilter,
                            onFilterChange = { viewModel.setAuditFilter(it) }
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// TAB 0: ECONOMY OVERVIEW / DASHBOARD
// =====================================================================

@Composable
fun EconomyOverviewTab(
    currentLang: AppLanguage,
    featureConfig: FeatureControlConfig,
    economyConfig: EconomyConfig,
    taskCount: Int,
    leaderboardCycle: String,
    onNavigateToTab: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Absolute Contract Rule Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(MetricBlue, MetricPurple))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MetricBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MetricBlue, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC) "مبدأ العقد الكنسي: حذف الإعلانات فقط" else "Canonical Rule: Remove Ads Only",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "الاشتراكات تمنح ميزة إزالة الإعلانات حصرياً. جودة الفيديو (4K/1080p) وسرعات التحميل غير مقيدة لأي مستخدم."
                            else "Subscription tier only grants ad removal. Video qualities and download limits are never gated by tier.",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Feature Status Summary Grid
        Text(
            text = if (currentLang == AppLanguage.ARABIC) "حالة ميزات النظام الحالية (/config/features)" else "System Feature Statuses (/config/features)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureStatusPill("Subscriptions", featureConfig.subscriptions.state, Modifier.weight(1f))
            FeatureStatusPill("Points", featureConfig.points.state, Modifier.weight(1f))
            FeatureStatusPill("Daily Login", featureConfig.dailyLogin.state, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureStatusPill("Rewarded Ads", featureConfig.rewardedAds.state, Modifier.weight(1f))
            FeatureStatusPill("Reward Tasks", featureConfig.tasks.state, Modifier.weight(1f))
            FeatureStatusPill("Leaderboard", featureConfig.leaderboard.state, Modifier.weight(1f))
        }

        // Quick Pricing & Rules Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = MetricOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "أسعار استبدال الاشتراكات" else "Subscription Prices (Points)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    TextButton(onClick = { onNavigateToTab(2) }) {
                        Text(if (currentLang == AppLanguage.ARABIC) "تعديل" else "Manage", color = CineStreamRed, fontSize = 12.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val costs = economyConfig.redemptionCosts
                    PriceChip("1 Day", costs["pro_lite_1d"] ?: 50L, Modifier.weight(1f))
                    PriceChip("7 Days", costs["pro_lite_7d"] ?: 250L, Modifier.weight(1f))
                    PriceChip("10 Days", costs["pro_lite_10d"] ?: 350L, Modifier.weight(1f))
                    PriceChip("30 Days", costs["pro_30d"] ?: 1000L, Modifier.weight(1f))
                }
            }
        }

        // Daily Login Ladder Preview
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MetricGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "سلم تسجيل الدخول اليومي (7 أيام)" else "Daily Login Ladder (7 Days)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    TextButton(onClick = { onNavigateToTab(2) }) {
                        Text(if (currentLang == AppLanguage.ARABIC) "تعديل" else "Manage", color = CineStreamRed, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    economyConfig.dailyLoginRewards.take(7).forEachIndexed { index, pts ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("D${index + 1}", color = TextSecondary, fontSize = 10.sp)
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .background(MetricGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, MetricGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("$pts", color = MetricGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Rewarded Ads & Tasks Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
                modifier = Modifier.weight(1f).clickable { onNavigateToTab(2) }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (currentLang == AppLanguage.ARABIC) "إعلانات المكافآت" else "Rewarded Ads", color = TextSecondary, fontSize = 11.sp)
                    Text("${economyConfig.rewardedAdPoints} pts / ad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Cap: ${economyConfig.rewardedAdDailyCap}/day • ${economyConfig.rewardedAdCooldownSeconds}s", color = TextSecondary, fontSize = 10.5.sp)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
                modifier = Modifier.weight(1f).clickable { onNavigateToTab(3) }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (currentLang == AppLanguage.ARABIC) "مهام المكافآت النشطة" else "Active Tasks", color = TextSecondary, fontSize = 11.sp)
                    Text("$taskCount Tasks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Cycle: $leaderboardCycle", color = MetricPurple, fontSize = 10.5.sp)
                }
            }
        }

        // Economy Audit Trail Quick Action Card (Phase 04B Deliverable)
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToTab(6) }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MetricGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MetricGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "سجل العمليات والتدقيق الإداري" else "Administrative Audit Trail",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "متابعة سجل العمليات وتعديلات المشرفين المعتمدة" else "Track authoritative admin actions on economy & user points",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun FeatureStatusPill(name: String, state: FeatureState, modifier: Modifier = Modifier) {
    val color = when (state) {
        FeatureState.ACTIVE -> MetricGreen
        FeatureState.COMING_SOON -> MetricBlue
        FeatureState.DISABLED -> MetricRed
    }
    Box(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(name, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.name, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable
fun PriceChip(label: String, price: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextSecondary, fontSize = 10.sp)
            Text("$price pts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

// =====================================================================
// TAB 1: FEATURE CONTROL (/config/features)
// =====================================================================

@Composable
fun EconomyFeaturesTab(
    currentLang: AppLanguage,
    featureConfig: FeatureControlConfig,
    onUpdateFeature: (key: String, state: FeatureState, comingSoon: String?, disabled: String?) -> Unit
) {
    var pendingDisableKey by remember { mutableStateOf<String?>(null) }
    var pendingDisableLabel by remember { mutableStateOf("") }

    if (pendingDisableKey != null) {
        AlertDialog(
            onDismissRequest = { pendingDisableKey = null },
            title = {
                Text(
                    text = if (currentLang == AppLanguage.ARABIC) "تأكيد تعطيل الميزة" else "Confirm Feature Deactivation",
                    color = MetricRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (currentLang == AppLanguage.ARABIC)
                        "هل أنت متأكد من تعطيل ميزة ($pendingDisableLabel)؟ سيتم حظر جميع العمليات المتعلقة بها في تطبيق المستخدمين فوراً."
                        else "Are you sure you want to disable ($pendingDisableLabel)? All related operations will be blocked immediately for users.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = pendingDisableKey ?: return@Button
                        onUpdateFeature(key, FeatureState.DISABLED, null, null)
                        pendingDisableKey = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetricRed)
                ) {
                    Text(if (currentLang == AppLanguage.ARABIC) "تعطيل الميزة" else "Disable Feature")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisableKey = null }) {
                    Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    val features = listOf(
        Triple("subscriptions", if (currentLang == AppLanguage.ARABIC) "الاشتراكات بالنقاط (Subscriptions)" else "Subscriptions", featureConfig.subscriptions),
        Triple("points", if (currentLang == AppLanguage.ARABIC) "نظام المحفظة والنقاط (Points System)" else "Points Economy", featureConfig.points),
        Triple("dailyLogin", if (currentLang == AppLanguage.ARABIC) "تسجيل الدخول اليومي (Daily Login)" else "Daily Login", featureConfig.dailyLogin),
        Triple("rewardedAds", if (currentLang == AppLanguage.ARABIC) "إعلانات المكافآت (Rewarded Ads)" else "Rewarded Ads", featureConfig.rewardedAds),
        Triple("tasks", if (currentLang == AppLanguage.ARABIC) "مهام المكافآت (Reward Tasks)" else "Reward Tasks", featureConfig.tasks),
        Triple("leaderboard", if (currentLang == AppLanguage.ARABIC) "قائمة المتصدرين (Leaderboard)" else "Leaderboard", featureConfig.leaderboard)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(features) { (key, label, config) ->
            FeatureItemCard(
                currentLang = currentLang,
                featureKey = key,
                label = label,
                config = config,
                onStateSelected = { newState ->
                    if (newState == FeatureState.DISABLED && config.state != FeatureState.DISABLED) {
                        pendingDisableKey = key
                        pendingDisableLabel = label
                    } else {
                        onUpdateFeature(key, newState, config.comingSoonMessage, config.disabledMessage)
                    }
                }
            )
        }
    }
}

@Composable
fun FeatureItemCard(
    currentLang: AppLanguage,
    featureKey: String,
    label: String,
    config: FeatureItemConfig,
    onStateSelected: (FeatureState) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                val badgeColor = when (config.state) {
                    FeatureState.ACTIVE -> MetricGreen
                    FeatureState.COMING_SOON -> MetricBlue
                    FeatureState.DISABLED -> MetricRed
                }
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = config.state.name,
                        color = badgeColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = config.state == FeatureState.ACTIVE,
                    onClick = { onStateSelected(FeatureState.ACTIVE) },
                    label = { Text("ACTIVE", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MetricGreen,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = config.state == FeatureState.COMING_SOON,
                    onClick = { onStateSelected(FeatureState.COMING_SOON) },
                    label = { Text("SOON", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MetricBlue,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = config.state == FeatureState.DISABLED,
                    onClick = { onStateSelected(FeatureState.DISABLED) },
                    label = { Text("DISABLED", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MetricRed,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (config.state == FeatureState.COMING_SOON) {
                Text(
                    text = "Message: ${config.comingSoonMessage}",
                    fontSize = 11.sp,
                    color = MetricBlue
                )
            } else if (config.state == FeatureState.DISABLED) {
                Text(
                    text = "Message: ${config.disabledMessage}",
                    fontSize = 11.sp,
                    color = MetricRed
                )
            }
        }
    }
}

// =====================================================================
// TAB 2: PRICING & RULES (/config/economy)
// =====================================================================

@Composable
fun EconomyPricingRulesTab(
    currentLang: AppLanguage,
    economyConfig: EconomyConfig,
    onUpdatePrice: (sku: String, price: Long) -> Unit,
    onUpdateDaily: (List<Long>) -> Unit,
    onUpdateAds: (points: Long, dailyCap: Int, cooldownSeconds: Int) -> Unit
) {
    var editingSku by remember { mutableStateOf<String?>(null) }
    var editPriceText by remember { mutableStateOf("") }

    var isEditingDaily by remember { mutableStateOf(false) }
    val dailyList = remember(economyConfig.dailyLoginRewards) {
        val list = economyConfig.dailyLoginRewards.toMutableList()
        while (list.size < 7) list.add(10L * (list.size + 1))
        list.take(7).map { mutableStateOf(it.toString()) }
    }

    var isEditingAds by remember { mutableStateOf(false) }
    var adPointsText by remember(economyConfig.rewardedAdPoints) { mutableStateOf(economyConfig.rewardedAdPoints.toString()) }
    var adCapText by remember(economyConfig.rewardedAdDailyCap) { mutableStateOf(economyConfig.rewardedAdDailyCap.toString()) }
    var adCooldownText by remember(economyConfig.rewardedAdCooldownSeconds) { mutableStateOf(economyConfig.rewardedAdCooldownSeconds.toString()) }

    // Dialog for Price Edit
    if (editingSku != null) {
        val sku = editingSku ?: ""
        AlertDialog(
            onDismissRequest = { editingSku = null },
            title = {
                Text(
                    text = if (currentLang == AppLanguage.ARABIC) "تعديل سعر الباقة: $sku" else "Edit SKU Price: $sku",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "أدخل تكلفة النقاط المطلوبة للاستبدال (يجب أن تكون قيمة صحيحة أكبر من الصفر):"
                            else "Enter points required for redemption (must be positive integer):",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = editPriceText,
                        onValueChange = { editPriceText = it },
                        label = { Text("Points", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineStreamRed,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = editPriceText.toLongOrNull()
                        if (parsed != null && parsed > 0L) {
                            onUpdatePrice(sku, parsed)
                            editingSku = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineStreamRed)
                ) {
                    Text(if (currentLang == AppLanguage.ARABIC) "حفظ السعر" else "Save Price")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSku = null }) {
                    Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Canonical Subscription Pricing
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = MetricOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC) "أسعار استبدال الاشتراكات Canonical" else "Canonical Subscription Pricing",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }

                val skus = listOf(
                    Triple("pro_lite_1d", "PRO LITE (1 Day / 24h)", economyConfig.redemptionCosts["pro_lite_1d"] ?: 50L),
                    Triple("pro_lite_7d", "PRO LITE (7 Days)", economyConfig.redemptionCosts["pro_lite_7d"] ?: 250L),
                    Triple("pro_lite_10d", "PRO LITE (10 Days)", economyConfig.redemptionCosts["pro_lite_10d"] ?: 350L),
                    Triple("pro_30d", "PRO (30 Days)", economyConfig.redemptionCosts["pro_30d"] ?: 1000L)
                )

                skus.forEach { (sku, label, cost) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("SKU: $sku", color = TextSecondary, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$cost pts",
                                color = MetricOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    editingSku = sku
                                    editPriceText = cost.toString()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Section: Daily Login Ladder (7 Days)
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MetricGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "مكافآت تسجيل الدخول اليومي" else "Daily Login Rewards",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }

                    if (!isEditingDaily) {
                        TextButton(onClick = { isEditingDaily = true }) {
                            Text(if (currentLang == AppLanguage.ARABIC) "تعديل الكل" else "Edit Ladder", color = CineStreamRed)
                        }
                    } else {
                        Row {
                            TextButton(onClick = { isEditingDaily = false }) {
                                Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    val parsed = dailyList.mapNotNull { it.value.toLongOrNull() }
                                    if (parsed.size == 7 && parsed.all { it >= 0L }) {
                                        onUpdateDaily(parsed)
                                        isEditingDaily = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MetricGreen)
                            ) {
                                Text(if (currentLang == AppLanguage.ARABIC) "حفظ" else "Save")
                            }
                        }
                    }
                }

                if (!isEditingDaily) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        economyConfig.dailyLoginRewards.take(7).forEachIndexed { idx, amt ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Day ${idx + 1}", color = TextSecondary, fontSize = 10.5.sp)
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .background(MetricGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .border(1.dp, MetricGreen, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("$amt", color = MetricGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dailyList.forEachIndexed { idx, state ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Day ${idx + 1}:", color = Color.White, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = state.value,
                                    onValueChange = { state.value = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(100.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MetricGreen,
                                        unfocusedBorderColor = DarkCardBorder
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Rewarded Ads Config
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = MetricPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "إعدادات إعلانات المكافآت" else "Rewarded Ads Config",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }

                    if (!isEditingAds) {
                        TextButton(onClick = { isEditingAds = true }) {
                            Text(if (currentLang == AppLanguage.ARABIC) "تعديل" else "Edit", color = CineStreamRed)
                        }
                    } else {
                        Row {
                            TextButton(onClick = { isEditingAds = false }) {
                                Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    val pts = adPointsText.toLongOrNull() ?: 15L
                                    val cap = adCapText.toIntOrNull() ?: 5
                                    val cd = adCooldownText.toIntOrNull() ?: 300
                                    if (pts >= 0L && cap >= 0 && cd >= 0) {
                                        onUpdateAds(pts, cap, cd)
                                        isEditingAds = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MetricPurple)
                            ) {
                                Text(if (currentLang == AppLanguage.ARABIC) "حفظ" else "Save")
                            }
                        }
                    }
                }

                if (!isEditingAds) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f).background(DarkSurfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text("Reward / Ad", color = TextSecondary, fontSize = 10.5.sp)
                            Text("${economyConfig.rewardedAdPoints} pts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(modifier = Modifier.weight(1f).background(DarkSurfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text("Daily Cap", color = TextSecondary, fontSize = 10.5.sp)
                            Text("${economyConfig.rewardedAdDailyCap} ads/day", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(modifier = Modifier.weight(1f).background(DarkSurfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text("Cooldown", color = TextSecondary, fontSize = 10.5.sp)
                            Text("${economyConfig.rewardedAdCooldownSeconds}s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = adPointsText,
                            onValueChange = { adPointsText = it },
                            label = { Text("Reward Points Per Ad") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = adCapText,
                            onValueChange = { adCapText = it },
                            label = { Text("Daily Cap (Max ads per day)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = adCooldownText,
                            onValueChange = { adCooldownText = it },
                            label = { Text("Cooldown Seconds between ads") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// TAB 3: REWARD TASKS CRUD (/reward_tasks/{taskId})
// =====================================================================

@Composable
fun EconomyTasksTab(
    currentLang: AppLanguage,
    tasks: List<RewardTask>,
    onSaveTask: (task: RewardTask, isNew: Boolean) -> Unit,
    onToggleActive: (RewardTask) -> Unit,
    onDeleteTask: (taskId: String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<RewardTask?>(null) }
    var taskToDelete by remember { mutableStateOf<RewardTask?>(null) }

    // Dialog for Delete Confirmation
    if (taskToDelete != null) {
        val target = taskToDelete ?: return
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = {
                Text(
                    text = if (currentLang == AppLanguage.ARABIC) "حذف المهمة نهائياً؟" else "Delete Task Definition?",
                    color = MetricRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "هل أنت متأكد من حذف المهمة: \"${target.title}\" (${target.taskId})؟"
                            else "Are you sure you want to delete task \"${target.title}\" (${target.taskId})?",
                        color = Color.White
                    )
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "ملاحظة أمان: هذا الإجراء يحذف تعريف المهمة فقط من الكتالوج (/reward_tasks). لن يتم حذف أي مطالبات سابقة أو سجلات مالية للمستخدمين."
                            else "Safety Note: This deletes the task definition only. User claims and transaction history remain untouched.",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTask(target.taskId)
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetricRed)
                ) {
                    Text(if (currentLang == AppLanguage.ARABIC) "تأكيد الحذف" else "Confirm Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Dialog for Create or Edit
    if (showCreateDialog || taskToEdit != null) {
        val isNew = showCreateDialog
        val initial = taskToEdit ?: RewardTask(taskId = "task_${System.currentTimeMillis() % 100000}")
        TaskFormDialog(
            currentLang = currentLang,
            initialTask = initial,
            isNew = isNew,
            onDismiss = {
                showCreateDialog = false
                taskToEdit = null
            },
            onSave = { task ->
                onSaveTask(task, isNew)
                showCreateDialog = false
                taskToEdit = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentLang == AppLanguage.ARABIC) "كتالوج المهام النشطة (${tasks.size})" else "Active Tasks Catalog (${tasks.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CineStreamRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_task_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (currentLang == AppLanguage.ARABIC) "مهمة جديدة" else "New Task", fontSize = 12.sp)
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AssignmentLate, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (currentLang == AppLanguage.ARABIC) "لا توجد مهام مسجلة حالياً" else "No reward tasks found",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks) { task ->
                    TaskItemCard(
                        currentLang = currentLang,
                        task = task,
                        onEdit = { taskToEdit = task },
                        onToggleActive = { onToggleActive(task) },
                        onDelete = { taskToDelete = task }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskItemCard(
    currentLang: AppLanguage,
    task: RewardTask,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MetricOrange.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, MetricOrange, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("+${task.rewardPoints} pts", color = MetricOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(task.taskType, color = TextSecondary, fontSize = 10.sp)
                    }

                    val statusColor = if (task.isActive) MetricGreen else MetricRed
                    val statusText = if (task.isActive) "ACTIVE" else "DISABLED"
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row {
                    IconButton(onClick = onToggleActive, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (task.isActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Active",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MetricRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TaskFormDialog(
    currentLang: AppLanguage,
    initialTask: RewardTask,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (RewardTask) -> Unit
) {
    var taskId by remember { mutableStateOf(initialTask.taskId) }
    var title by remember { mutableStateOf(initialTask.title) }
    var description by remember { mutableStateOf(initialTask.description) }
    var pointsText by remember { mutableStateOf(initialTask.rewardPoints.toString()) }
    var taskType by remember { mutableStateOf(initialTask.taskType) }
    var actionUrl by remember { mutableStateOf(initialTask.actionUrl ?: "") }
    var isActive by remember { mutableStateOf(initialTask.isActive) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) (if (currentLang == AppLanguage.ARABIC) "إنشاء مهمة جديدة" else "Create Reward Task")
                       else (if (currentLang == AppLanguage.ARABIC) "تعديل المهمة" else "Edit Reward Task"),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    Text(validationError!!, color = MetricRed, fontSize = 11.5.sp)
                }

                OutlinedTextField(
                    value = taskId,
                    onValueChange = { taskId = it },
                    label = { Text("Task ID", color = TextSecondary) },
                    enabled = isNew,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it },
                    label = { Text("Reward Points *", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Task Type:", color = TextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskTypes.ALL.take(3).forEach { type ->
                        FilterChip(
                            selected = taskType == type,
                            onClick = { taskType = type },
                            label = { Text(type, fontSize = 9.5.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskTypes.ALL.drop(3).forEach { type ->
                        FilterChip(
                            selected = taskType == type,
                            onClick = { taskType = type },
                            label = { Text(type, fontSize = 9.5.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = actionUrl,
                    onValueChange = { actionUrl = it },
                    label = { Text("Action URL (Optional)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Is Active", color = Color.White, fontSize = 13.sp)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pts = pointsText.toLongOrNull()
                    if (pts == null || pts < 0L) {
                        validationError = "Reward points must be positive integer"
                        return@Button
                    }
                    val updated = initialTask.copy(
                        taskId = taskId.trim(),
                        title = title.trim(),
                        description = description.trim(),
                        rewardPoints = pts,
                        taskType = taskType,
                        actionUrl = actionUrl.trim().ifBlank { null },
                        isActive = isActive,
                        updatedAt = System.currentTimeMillis()
                    )
                    val errors = updated.validate(isNew = isNew)
                    if (errors.isNotEmpty()) {
                        validationError = errors.first()
                        return@Button
                    }
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CineStreamRed)
            ) {
                Text(if (currentLang == AppLanguage.ARABIC) "حفظ" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(14.dp)
    )
}

// =====================================================================
// TAB 4: USER POINTS & LEDGER VIEWER
// =====================================================================

@Composable
fun EconomyUserPointsTab(
    currentLang: AppLanguage,
    users: List<User>,
    selectedUser: User?,
    searchQuery: String,
    transactions: List<PointTransaction>,
    onSearchChange: (String) -> Unit,
    onSelectUser: (User?) -> Unit,
    onGrantPoints: (userId: String, amount: Long, reason: String) -> Unit,
    onRemovePoints: (userId: String, amount: Long, reason: String) -> Unit
) {
    var showGrantDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var adjustAmountText by remember { mutableStateOf("") }
    var adjustReasonText by remember { mutableStateOf("") }
    var adjustError by remember { mutableStateOf<String?>(null) }

    // Grant Points Dialog
    if (showGrantDialog && selectedUser != null) {
        val user = selectedUser
        val uid = user.uid.ifBlank { user.id }
        AlertDialog(
            onDismissRequest = { showGrantDialog = false },
            title = {
                Text(
                    text = if (currentLang == AppLanguage.ARABIC) "منح نقاط إدارية (ADMIN_GRANT)" else "Grant Points (ADMIN_GRANT)",
                    color = MetricGreen,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("User: ${user.displayName.ifBlank { user.email }}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Current Balance: ${user.pointsBalance} pts", color = TextSecondary, fontSize = 12.sp)

                    if (adjustError != null) Text(adjustError!!, color = MetricRed, fontSize = 11.5.sp)

                    OutlinedTextField(
                        value = adjustAmountText,
                        onValueChange = { adjustAmountText = it },
                        label = { Text("Points Amount to Add") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adjustReasonText,
                        onValueChange = { adjustReasonText = it },
                        label = { Text("Audit Reason *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = adjustAmountText.toLongOrNull()
                        if (amt == null || amt <= 0L) {
                            adjustError = "Amount must be greater than 0"
                            return@Button
                        }
                        if (adjustReasonText.trim().isBlank()) {
                            adjustError = "Reason is mandatory for audit logging"
                            return@Button
                        }
                        if (user.pointsBalance + amt > 1_000_000L) {
                            adjustError = "Resulting balance exceeds max ceiling of 1,000,000 points"
                            return@Button
                        }
                        onGrantPoints(uid, amt, adjustReasonText.trim())
                        showGrantDialog = false
                        adjustAmountText = ""
                        adjustReasonText = ""
                        adjustError = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetricGreen)
                ) {
                    Text(if (currentLang == AppLanguage.ARABIC) "تأكيد المنح" else "Confirm Grant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGrantDialog = false }) {
                    Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Remove Points Dialog (with confirmation warning)
    if (showRemoveDialog && selectedUser != null) {
        val user = selectedUser
        val uid = user.uid.ifBlank { user.id }
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = {
                Text(
                    text = if (currentLang == AppLanguage.ARABIC) "خصم نقاط إداري (ADMIN_ADJUSTMENT)" else "Deduct Points (ADMIN_ADJUSTMENT)",
                    color = MetricRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("User: ${user.displayName.ifBlank { user.email }}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Current Balance: ${user.pointsBalance} pts", color = TextSecondary, fontSize = 12.sp)

                    if (adjustError != null) Text(adjustError!!, color = MetricRed, fontSize = 11.5.sp)

                    OutlinedTextField(
                        value = adjustAmountText,
                        onValueChange = { adjustAmountText = it },
                        label = { Text("Points Amount to Deduct") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adjustReasonText,
                        onValueChange = { adjustReasonText = it },
                        label = { Text("Audit Reason *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = adjustAmountText.toLongOrNull()
                        if (amt == null || amt <= 0L) {
                            adjustError = "Amount must be greater than 0"
                            return@Button
                        }
                        if (adjustReasonText.trim().isBlank()) {
                            adjustError = "Reason is mandatory for audit logging"
                            return@Button
                        }
                        if (user.pointsBalance - amt < 0L) {
                            adjustError = "User balance cannot become negative (${user.pointsBalance - amt})"
                            return@Button
                        }
                        onRemovePoints(uid, amt, adjustReasonText.trim())
                        showRemoveDialog = false
                        adjustAmountText = ""
                        adjustReasonText = ""
                        adjustError = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetricRed)
                ) {
                    Text(if (currentLang == AppLanguage.ARABIC) "تأكيد الخصم" else "Confirm Deduction")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(if (currentLang == AppLanguage.ARABIC) "إلغاء" else "Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // User Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(if (currentLang == AppLanguage.ARABIC) "بحث بالبريد، الاسم، أو المعرف..." else "Search user by email, name, UID...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("user_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = CineStreamRed,
                unfocusedBorderColor = DarkCardBorder
            )
        )

        // Selected User Card OR User Picker Dropdown
        if (selectedUser == null) {
            Text(
                if (currentLang == AppLanguage.ARABIC) "اختر مستخدماً لإدارة رصيده واستعراض سجله:" else "Select a user to inspect wallet & ledger:",
                color = TextSecondary,
                fontSize = 12.sp
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(users) { u ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(8.dp))
                            .clickable { onSelectUser(u) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(u.displayName.ifBlank { u.email }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(u.email, color = TextSecondary, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("${u.pointsBalance} pts", color = MetricOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            val user = selectedUser
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(user.displayName.ifBlank { user.email }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("UID: ${user.uid.ifBlank { user.id }}", color = TextSecondary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onSelectUser(null) }) {
                            Text(if (currentLang == AppLanguage.ARABIC) "تغيير" else "Change", color = CineStreamRed, fontSize = 12.sp)
                        }
                    }

                    // Balance Breakdown
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f).background(DarkSurfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text(if (currentLang == AppLanguage.ARABIC) "الرصيد الحالي" else "Balance", color = TextSecondary, fontSize = 10.sp)
                            Text("${user.pointsBalance} pts", color = MetricOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f).background(DarkSurfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text(if (currentLang == AppLanguage.ARABIC) "إجمالي المكتسب" else "Total Earned", color = TextSecondary, fontSize = 10.sp)
                            Text("${user.totalPointsEarned} pts", color = MetricGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f).background(DarkSurfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text(if (currentLang == AppLanguage.ARABIC) "إجمالي المستهلك" else "Total Spent", color = TextSecondary, fontSize = 10.sp)
                            Text("${user.totalPointsSpent} pts", color = MetricRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Actions
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                adjustAmountText = ""
                                adjustReasonText = ""
                                adjustError = null
                                showGrantDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetricGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("grant_points_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (currentLang == AppLanguage.ARABIC) "منح نقاط" else "Grant Points", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                adjustAmountText = ""
                                adjustReasonText = ""
                                adjustError = null
                                showRemoveDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetricRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("remove_points_button")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (currentLang == AppLanguage.ARABIC) "خصم نقاط" else "Deduct Points", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Ledger Viewer
            Text(
                text = if (currentLang == AppLanguage.ARABIC) "سجل المعاملات المالي للمستخدم (Immutable Ledger):" else "User Transaction Ledger (Immutable):",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )

            if (transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (currentLang == AppLanguage.ARABIC) "لا توجد معاملات مسجلة لهذا المستخدم حتى الآن" else "No transactions recorded for this user",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(transactions) { tx ->
                        TransactionItemCard(tx)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItemCard(tx: PointTransaction) {
    val isPositive = tx.amount >= 0L
    val color = if (isPositive) MetricGreen else MetricRed
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(tx.type, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateFormat.format(Date(tx.createdAt)),
                    color = TextSecondary,
                    fontSize = 10.5.sp
                )
            }
            if (tx.description.isNotBlank()) {
                Text(
                    text = tx.description,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = "Balance: ${tx.balanceBefore} -> ${tx.balanceAfter}",
                color = TextSecondary,
                fontSize = 10.5.sp
            )
        }

        Text(
            text = (if (isPositive) "+${tx.amount}" else "${tx.amount}") + " pts",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
        )
    }
}

// =====================================================================
// TAB 5: WEEKLY LEADERBOARD (/leaderboard/weekly_current)
// =====================================================================

@Composable
fun EconomyLeaderboardTab(
    currentLang: AppLanguage,
    leaderboard: WeeklyLeaderboard
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Read-only Security Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(MetricPurple, MetricBlue))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MetricPurple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC) "عرض فقط — إدارة السيرفر الموثوق (READ ONLY)" else "READ ONLY — Managed by Cloudflare Worker",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = if (currentLang == AppLanguage.ARABIC)
                        "تتم إدارة دورة الترتيب الأسبوعي وتوزيع المكافآت والأرشفة تلقائياً عبر السيرفر الخارجي الموثوق عند نهاية كل أسبوع (الأحد 23:59 UTC)."
                        else "Weekly cycle standings and prize distribution are archived and settled automatically by the trusted backend cron.",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cycle: ${leaderboard.cycleId}", color = MetricPurple, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    if (leaderboard.updatedAt > 0L) {
                        Text("Updated: ${dateFormat.format(Date(leaderboard.updatedAt))}", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        if (leaderboard.rankings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (currentLang == AppLanguage.ARABIC) "لا توجد بيانات متصدرين للدورة الحالية" else "No leaderboard data published for current cycle",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(leaderboard.rankings) { entry ->
                    LeaderboardEntryCard(entry)
                }
            }
        }
    }
}

@Composable
fun LeaderboardEntryCard(entry: LeaderboardEntry) {
    val medalColor = when (entry.rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(10.dp))
            .border(1.dp, if (entry.rank <= 3) medalColor.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(medalColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, medalColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${entry.rank}",
                    color = medalColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = entry.displayName.ifBlank { "User ${entry.userId.take(8)}" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "UID: ${entry.userId.take(12)}...",
                    color = TextSecondary,
                    fontSize = 10.5.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .background(MetricOrange.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${entry.points} pts",
                color = MetricOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// =====================================================================
// TAB 6: ECONOMY AUDIT TRAIL (/auditLogs) — Phase 04B Deliverable
// =====================================================================

@Composable
fun EconomyAuditTrailTab(
    currentLang: AppLanguage,
    auditLogs: List<AuditLog>,
    currentFilter: String,
    onFilterChange: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Explanatory Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(MetricGreen, MetricBlue))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MetricGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC) "سجل التدقيق الإداري للاقتصاد (/auditLogs)" else "Authoritative Economy Audit Trail (/auditLogs)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
                Text(
                    text = if (currentLang == AppLanguage.ARABIC)
                        "يتم تسجيل وتوثيق جميع الإجراءات الإدارية المتعلقة بالاقتصاد، مفاتيح الميزات، أسعار الاشتراكات، المهام، وتعديلات نقاط المستخدمين تلقائياً وغير قابلة للحذف."
                        else "All authoritative administrative operations modifying feature flags, pricing, tasks, and points balances are immutably logged for forensic auditing.",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                Pair("ALL", if (currentLang == AppLanguage.ARABIC) "الكل" else "All"),
                Pair("POINTS", if (currentLang == AppLanguage.ARABIC) "النقاط" else "Points"),
                Pair("FEATURES", if (currentLang == AppLanguage.ARABIC) "الميزات" else "Features"),
                Pair("CONFIG", if (currentLang == AppLanguage.ARABIC) "الأسعار والقواعد" else "Pricing"),
                Pair("TASKS", if (currentLang == AppLanguage.ARABIC) "المهام" else "Tasks")
            )

            filters.forEach { (key, label) ->
                val isSelected = currentFilter == key
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) CineStreamRed else DarkSurface,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) CineStreamRed else DarkCardBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onFilterChange(key) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Audit Logs List
        if (auditLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC) "لا توجد سجلات تدقيق مطابقة لهذا الفلتر" else "No audit logs found matching filter",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(auditLogs, key = { it.id.ifBlank { "${it.createdAt}_${it.action}" } }) { log ->
                    EconomyAuditLogItem(log = log, currentLang = currentLang, dateFormat = dateFormat)
                }
            }
        }
    }
}

@Composable
fun EconomyAuditLogItem(
    log: AuditLog,
    currentLang: AppLanguage,
    dateFormat: SimpleDateFormat
) {
    val actionUpper = log.action.uppercase()
    val (actionColor, actionIcon) = when {
        actionUpper.contains("GRANT") -> Pair(MetricGreen, Icons.Default.AddCircle)
        actionUpper.contains("ADJUSTMENT") -> Pair(MetricOrange, Icons.Default.Tune)
        actionUpper.contains("FEATURE") -> Pair(MetricBlue, Icons.Default.ToggleOn)
        actionUpper.contains("CONFIG") || actionUpper.contains("ECONOMY") -> Pair(MetricPurple, Icons.Default.MonetizationOn)
        actionUpper.contains("DELETE") -> Pair(MetricRed, Icons.Default.Delete)
        actionUpper.contains("TASK") -> Pair(Color(0xFF00BCD4), Icons.Default.Assignment)
        else -> Pair(TextSecondary, Icons.Default.Security)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(actionColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(actionIcon, contentDescription = null, tint = actionColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(actionColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = log.action,
                            color = actionColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = if (log.createdAt > 0L) dateFormat.format(Date(log.createdAt)) else "",
                    color = TextSecondary,
                    fontSize = 10.5.sp
                )
            }

            if (log.details.isNotBlank()) {
                Text(
                    text = log.details,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Admin: ${log.adminEmail.ifBlank { log.actorUid.ifBlank { "System" } }}",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                if (log.targetId.isNotBlank()) {
                    Text(
                        text = "Target: ${log.targetType}:${log.targetId.take(12)}",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
