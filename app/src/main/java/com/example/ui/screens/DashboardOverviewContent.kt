package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.*
import com.example.state.AppLanguage
import com.example.state.AppStrings
import com.example.ui.components.CineStreamLoadingButton
import com.example.ui.components.CineStreamOutlinedLoadingButton
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase C6: Operational Dashboard & Analytics Content Composable.
 *
 * Provides a unified, high-polish observability console aggregating canonical Firestore data
 * across all CineStream administrative subsystems without external analytics SDKs.
 */
@Composable
fun DashboardOverviewContent(
    userMetricsState: DashboardSectionState<UserMetrics>,
    restrictionMetricsState: DashboardSectionState<FeatureRestrictionMetrics>,
    supportMetricsState: DashboardSectionState<SupportMetrics>,
    proRequestMetricsState: DashboardSectionState<ProRequestMetrics>,
    extensionMetricsState: DashboardSectionState<ManagedExtensionMetrics>,
    recentActivityState: DashboardSectionState<List<AuditLog>>,
    isRefreshing: Boolean,
    currentLang: AppLanguage,
    onRefresh: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToProRequests: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToAuditLogs: () -> Unit,
    onNavigateToEconomy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rotation by rememberInfiniteTransition(label = "refresh_rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_rotation_float"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
    ) {
        // --- Header Row with Refresh Action ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.dashboardOverviewTitle(currentLang),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AppStrings.dashboardOverviewSubtitle(currentLang),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Controlled Refresh Button with tactile feedback
                Box(
                    modifier = Modifier
                        .testTag("dashboard_refresh_button")
                        .size(44.dp)
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                        .bounceClick(scaleDown = 0.88f) { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = AppStrings.refreshMetrics(currentLang),
                        tint = if (isRefreshing) CineStreamRed else Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .then(if (isRefreshing) Modifier.rotate(rotation) else Modifier)
                    )
                }
            }
        }

        // --- Quick Jump Chips Row ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickNavChip(
                    label = AppStrings.users(currentLang),
                    icon = Icons.Default.Group,
                    color = MetricBlue,
                    onClick = onNavigateToUsers
                )
                QuickNavChip(
                    label = AppStrings.kpiSupport(currentLang),
                    icon = Icons.Default.SupportAgent,
                    color = CineStreamRed,
                    onClick = onNavigateToSupport
                )
                QuickNavChip(
                    label = AppStrings.kpiProRequests(currentLang),
                    icon = Icons.Default.WorkspacePremium,
                    color = MetricPurple,
                    onClick = onNavigateToProRequests
                )
                QuickNavChip(
                    label = AppStrings.kpiExtensions(currentLang),
                    icon = Icons.Default.Hub,
                    color = MetricOrange,
                    onClick = onNavigateToExtensions
                )
                QuickNavChip(
                    label = AppStrings.auditLogs(currentLang),
                    icon = Icons.Default.Security,
                    color = MetricGreen,
                    onClick = onNavigateToAuditLogs
                )
                QuickNavChip(
                    label = AppStrings.economy(currentLang),
                    icon = Icons.Default.MonetizationOn,
                    color = MetricOrange,
                    onClick = onNavigateToEconomy
                )
            }
        }

        // --- ECONOMY & POINTS CONTROL PLANE CARD (Phase 04B Deliverable) ---
        item {
            OperationalCard(
                title = AppStrings.kpiEconomy(currentLang),
                icon = Icons.Default.MonetizationOn,
                accentColor = MetricOrange,
                actionLabel = AppStrings.manage(currentLang),
                onAction = onNavigateToEconomy
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "التحكم المركزي في ميزات الاقتصاد، أسعار الاشتراكات بالنقاط، المهام، ورصيد المستخدمين."
                            else "Authoritative control plane for points economy, subscription pricing, tasks catalog & user wallets.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiTile(
                            label = if (currentLang == AppLanguage.ARABIC) "ميزات الاقتصاد" else "Economy Features",
                            value = "6 Configs",
                            accentColor = MetricGreen,
                            modifier = Modifier.weight(1f)
                        )
                        KpiTile(
                            label = if (currentLang == AppLanguage.ARABIC) "باقات النقاط" else "Points SKUs",
                            value = "4 Tiers",
                            accentColor = MetricOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CineStreamOutlinedLoadingButton(
                        text = if (currentLang == AppLanguage.ARABIC) "فتح وحدة إدارة الاقتصاد والنقاط" else "Open Economy Console",
                        leadingIcon = Icons.Default.AccountBalanceWallet,
                        onClick = onNavigateToEconomy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- 1. USERS & ACCOUNTS KPI CARD ---
        item {
            OperationalCard(
                title = AppStrings.kpiUsers(currentLang),
                icon = Icons.Default.Group,
                accentColor = MetricBlue,
                actionLabel = AppStrings.manage(currentLang),
                onAction = onNavigateToUsers
            ) {
                when (userMetricsState) {
                    is DashboardSectionState.Loading -> SectionLoadingIndicator()
                    is DashboardSectionState.Error -> SectionErrorNotice(userMetricsState.message, onRefresh, currentLang)
                    is DashboardSectionState.Success -> {
                        val m = userMetricsState.data
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelTotalUsers(currentLang),
                                    value = m.totalUsers.toString(),
                                    accentColor = MetricBlue,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_total_users")
                                )
                                KpiTile(
                                    label = AppStrings.labelActiveUsers(currentLang),
                                    value = m.activeUsers.toString(),
                                    accentColor = MetricGreen,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_active_users")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelInactiveUsers(currentLang),
                                    value = m.inactiveUsers.toString(),
                                    accentColor = TextSecondary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_inactive_users")
                                )
                                KpiTile(
                                    label = AppStrings.labelGlobalBans(currentLang),
                                    value = m.bannedUsers.toString(),
                                    accentColor = CineStreamRed,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_banned_users")
                                )
                            }

                            // Explicit breakdown separating Global Bans from Feature Restrictions (C1/C5.1 audit rule)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AppStrings.labelFeatureRestrictedOnly(currentLang),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${m.featureRestrictedUsers} (${AppStrings.labelTotalRestricted(currentLang)}: ${m.totalRestrictedUsers})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MetricOrange
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. SUBSCRIPTIONS & PLANS KPI CARD ---
        item {
            OperationalCard(
                title = AppStrings.kpiSubscriptions(currentLang),
                icon = Icons.Default.WorkspacePremium,
                accentColor = MetricPurple,
                actionLabel = AppStrings.manage(currentLang),
                onAction = onNavigateToUsers
            ) {
                when (userMetricsState) {
                    is DashboardSectionState.Loading -> SectionLoadingIndicator()
                    is DashboardSectionState.Error -> SectionErrorNotice(userMetricsState.message, onRefresh, currentLang)
                    is DashboardSectionState.Success -> {
                        val m = userMetricsState.data
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelPremiumUsers(currentLang),
                                    value = m.premiumUsers.toString(),
                                    accentColor = MetricPurple,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_premium_users")
                                )
                                KpiTile(
                                    label = AppStrings.labelFreeUsers(currentLang),
                                    value = m.freeUsers.toString(),
                                    accentColor = TextSecondary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_free_users")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelActivePremium(currentLang),
                                    value = m.activePremium.toString(),
                                    accentColor = MetricGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiTile(
                                    label = AppStrings.labelExpiredPremium(currentLang),
                                    value = m.expiredPremium.toString(),
                                    accentColor = CineStreamRed,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Subscription Tier Distribution Chips
                            if (m.tierBreakdown.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    m.tierBreakdown.entries.sortedByDescending { it.value }.forEach { (tier, count) ->
                                        Surface(
                                            color = DarkSurfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = tier.uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (tier == "free") TextSecondary else MetricPurple
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = ": $count",
                                                    fontSize = 10.sp,
                                                    color = Color.White
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

        // --- 3. SUPPORT DESK KPI CARD ---
        item {
            OperationalCard(
                title = AppStrings.kpiSupport(currentLang),
                icon = Icons.Default.SupportAgent,
                accentColor = CineStreamRed,
                actionLabel = AppStrings.manage(currentLang),
                onAction = onNavigateToSupport
            ) {
                when (supportMetricsState) {
                    is DashboardSectionState.Loading -> SectionLoadingIndicator()
                    is DashboardSectionState.Error -> SectionErrorNotice(supportMetricsState.message, onRefresh, currentLang)
                    is DashboardSectionState.Success -> {
                        val s = supportMetricsState.data
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KpiTile(
                                label = AppStrings.labelTotalConversations(currentLang),
                                value = s.totalConversations.toString(),
                                accentColor = Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_support_total")
                            )
                            KpiTile(
                                label = AppStrings.labelOpenConversations(currentLang),
                                value = s.openConversations.toString(),
                                accentColor = MetricOrange,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_support_open")
                            )
                            KpiTile(
                                label = AppStrings.labelUnreadMessages(currentLang),
                                value = s.unreadConversations.toString(),
                                accentColor = CineStreamRed,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_support_unread")
                            )
                        }
                    }
                }
            }
        }

        // --- 4. PRO REQUESTS QUEUE KPI CARD ---
        item {
            OperationalCard(
                title = AppStrings.kpiProRequests(currentLang),
                icon = Icons.Default.WorkspacePremium,
                accentColor = MetricPurple,
                actionLabel = AppStrings.manage(currentLang),
                onAction = onNavigateToProRequests
            ) {
                when (proRequestMetricsState) {
                    is DashboardSectionState.Loading -> SectionLoadingIndicator()
                    is DashboardSectionState.Error -> SectionErrorNotice(proRequestMetricsState.message, onRefresh, currentLang)
                    is DashboardSectionState.Success -> {
                        val p = proRequestMetricsState.data
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KpiTile(
                                label = AppStrings.labelPendingRequests(currentLang),
                                value = p.pendingRequests.toString(),
                                accentColor = MetricOrange,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_pending_pro_requests")
                            )
                            KpiTile(
                                label = AppStrings.labelApprovedRequests(currentLang),
                                value = p.approvedRequests.toString(),
                                accentColor = MetricGreen,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_approved_pro_requests")
                            )
                            KpiTile(
                                label = AppStrings.labelRejectedRequests(currentLang),
                                value = p.rejectedRequests.toString(),
                                accentColor = CineStreamRed,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dashboard_rejected_pro_requests")
                            )
                        }
                    }
                }
            }
        }

        // --- 5. MANAGED EXTENSIONS KPI CARD ---
        item {
            OperationalCard(
                title = AppStrings.kpiExtensions(currentLang),
                icon = Icons.Default.Hub,
                accentColor = MetricOrange,
                actionLabel = AppStrings.manage(currentLang),
                onAction = onNavigateToExtensions
            ) {
                when (extensionMetricsState) {
                    is DashboardSectionState.Loading -> SectionLoadingIndicator()
                    is DashboardSectionState.Error -> SectionErrorNotice(extensionMetricsState.message, onRefresh, currentLang)
                    is DashboardSectionState.Success -> {
                        val e = extensionMetricsState.data
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelTotalExtensions(currentLang),
                                    value = e.totalExtensions.toString(),
                                    accentColor = Color.White,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_extensions_total")
                                )
                                KpiTile(
                                    label = AppStrings.labelActiveExtensions(currentLang),
                                    value = e.activeExtensions.toString(),
                                    accentColor = MetricGreen,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_extensions_active")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelMaintenanceExtensions(currentLang),
                                    value = e.maintenanceExtensions.toString(),
                                    accentColor = MetricOrange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_extensions_maintenance")
                                )
                                KpiTile(
                                    label = AppStrings.labelDisabledExtensions(currentLang),
                                    value = e.disabledExtensions.toString(),
                                    accentColor = TextSecondary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_extensions_disabled")
                                )
                                KpiTile(
                                    label = AppStrings.labelDeprecatedExtensions(currentLang),
                                    value = e.deprecatedExtensions.toString(),
                                    accentColor = CineStreamRed,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dashboard_extensions_deprecated")
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 6. FEATURE RESTRICTIONS BREAKDOWN CARD ---
        item {
            OperationalCard(
                title = AppStrings.kpiFeatureRestrictions(currentLang),
                icon = Icons.Default.Block,
                accentColor = MetricOrange,
                actionLabel = null,
                onAction = null
            ) {
                when (restrictionMetricsState) {
                    is DashboardSectionState.Loading -> SectionLoadingIndicator()
                    is DashboardSectionState.Error -> SectionErrorNotice(restrictionMetricsState.message, onRefresh, currentLang)
                    is DashboardSectionState.Success -> {
                        val r = restrictionMetricsState.data
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelWatchRestricted(currentLang),
                                    value = r.watchRestricted.toString(),
                                    accentColor = if (r.watchRestricted > 0) MetricOrange else TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiTile(
                                    label = AppStrings.labelDownloadRestricted(currentLang),
                                    value = r.downloadRestricted.toString(),
                                    accentColor = if (r.downloadRestricted > 0) MetricOrange else TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiTile(
                                    label = AppStrings.labelChatRestricted(currentLang),
                                    value = r.chatRestricted.toString(),
                                    accentColor = if (r.chatRestricted > 0) MetricOrange else TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KpiTile(
                                    label = AppStrings.labelStoryRestricted(currentLang),
                                    value = r.storyRestricted.toString(),
                                    accentColor = if (r.storyRestricted > 0) MetricOrange else TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiTile(
                                    label = AppStrings.labelP2pRestricted(currentLang),
                                    value = r.p2pRestricted.toString(),
                                    accentColor = if (r.p2pRestricted > 0) MetricOrange else TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 7. RECENT ADMINISTRATIVE ACTIVITY (AUDIT LOGS FEED) ---
        item {
            OperationalCard(
                title = AppStrings.kpiRecentActivity(currentLang),
                icon = Icons.Default.Security,
                accentColor = MetricGreen,
                actionLabel = AppStrings.viewAllLogs(currentLang),
                onAction = onNavigateToAuditLogs,
                modifier = Modifier.testTag("dashboard_recent_activity")
            ) {
                when (recentActivityState) {
                    is DashboardSectionState.Loading -> SectionLoadingIndicator()
                    is DashboardSectionState.Error -> SectionErrorNotice(recentActivityState.message, onRefresh, currentLang)
                    is DashboardSectionState.Success -> {
                        val logs = recentActivityState.data
                        if (logs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = AppStrings.emptyAuditLogs(currentLang),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                logs.take(6).forEach { log ->
                                    RecentActivityItem(log = log)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard Operational Container Card
 */
@Composable
private fun OperationalCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (actionLabel != null && onAction != null) {
                    Text(
                        text = actionLabel,
                        fontSize = 12.sp,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onAction)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

/**
 * Minimalist KPI Tile with High Legibility
 */
@Composable
private fun KpiTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interactive Quick Navigation Chip
 */
@Composable
private fun QuickNavChip(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.bounceClick(scaleDown = 0.92f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

/**
 * Single Audit Activity Item within Dashboard Feed
 */
@Composable
private fun RecentActivityItem(log: AuditLog) {
    val actionColor = when {
        log.action.contains("BAN") -> CineStreamRed
        log.action.contains("SUBSCRIPTION") || log.action.contains("PRO") -> MetricPurple
        log.action.contains("ADMIN") -> MetricBlue
        log.action.contains("EXTENSION") -> MetricOrange
        else -> MetricGreen
    }

    val formattedTime = remember(log.createdAt) {
        if (log.createdAt > 0) {
            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(log.createdAt))
        } else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = actionColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = log.action,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = actionColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.details.ifBlank { "${log.action} on ${log.targetId}" },
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = log.adminEmail.ifBlank { log.adminUid },
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (formattedTime.isNotBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formattedTime,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SectionLoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = CineStreamRed,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun SectionErrorNotice(
    message: String,
    onRetry: () -> Unit,
    currentLang: AppLanguage
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CineStreamRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = message.ifBlank { AppStrings.failedToLoadSection(currentLang) },
            fontSize = 11.sp,
            color = CineStreamRed,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) {
            Text(
                text = AppStrings.retry(currentLang),
                fontSize = 11.sp,
                color = CineStreamRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
