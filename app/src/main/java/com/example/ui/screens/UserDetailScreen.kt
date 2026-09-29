package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.SubscriptionState
import com.example.models.User
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.CineStreamLoadingButton
import com.example.ui.components.CineStreamRefreshButton
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.viewmodels.UserDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserDetailScreen(
    userId: String,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentLang by AppSettings.language.collectAsState()

    // Dialog States
    var showRoleConfirmDialog by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }
    var showUnbanConfirmDialog by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var showRevokeSubDialog by remember { mutableStateOf(false) }

    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UserDetailViewModel(userId) as T
        }
    }
    val viewModel: UserDetailViewModel = viewModel(key = userId, factory = factory)
    val user by viewModel.user.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pendingOperations by viewModel.pendingOperations.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Role Change Confirmation Dialog
    if (showRoleConfirmDialog && user != null) {
        val u = user!!
        val willBeAdmin = u.role != "admin"
        AlertDialog(
            onDismissRequest = { showRoleConfirmDialog = false },
            title = {
                Text(
                    text = AppStrings.roleAndSubscription(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (willBeAdmin) AppStrings.grantAdminToggle(currentLang) else AppStrings.isAdministrator(currentLang),
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        showRoleConfirmDialog = false
                        viewModel.toggleRole()
                    },
                    containerColor = if (willBeAdmin) CineStreamRed else MetricPurple,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.confirm(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { showRoleConfirmDialog = false }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Account Ban Dialog
    if (showBanDialog && user != null) {
        BanAccountDialog(
            currentLang = currentLang,
            onDismiss = { showBanDialog = false },
            onConfirm = { reason, expiresAt ->
                showBanDialog = false
                viewModel.banAccount(reason, expiresAt)
            }
        )
    }

    // Unban Confirmation Dialog
    if (showUnbanConfirmDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showUnbanConfirmDialog = false },
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
                        "هل أنت متأكد من رغبتك في رفع الحظر عن هذا المستخدم واستعادة كافة الصلاحيات؟"
                    else
                        "Are you sure you want to lift the suspension for this user and restore all feature permissions?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        showUnbanConfirmDialog = false
                        viewModel.unbanAccount()
                    },
                    containerColor = MetricGreen,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.confirm(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { showUnbanConfirmDialog = false }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Subscription Upgrade / Extension Dialog
    if (showSubscriptionDialog && user != null) {
        SubscriptionManagementDialog(
            currentLang = currentLang,
            currentUser = user!!,
            onDismiss = { showSubscriptionDialog = false },
            onConfirm = { tier, durationDays ->
                showSubscriptionDialog = false
                viewModel.grantSubscription(tier, durationDays)
            }
        )
    }

    // Subscription Revocation Dialog
    if (showRevokeSubDialog && user != null) {
        AlertDialog(
            onDismissRequest = { showRevokeSubDialog = false },
            title = {
                Text(
                    AppStrings.revokeSubBtn(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (currentLang == AppLanguage.ARABIC)
                        "هل أنت متأكد من رغبتك في إلغاء اشتراك هذا المستخدم وإعادته فورياً إلى الخطة المجانية؟"
                    else
                        "Are you sure you want to revoke this user's subscription and revert them to the Free plan immediately?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        showRevokeSubDialog = false
                        viewModel.revokeSubscription()
                    },
                    containerColor = CineStreamRed,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.confirm(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { showRevokeSubDialog = false }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    AdaptiveScreenContainer(
        modifier = Modifier.fillMaxSize()
    ) {
        if (user == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CineStreamRed)
            }
        } else {
            val u = user!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                    // Top Navigation & Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = AppStrings.userDetailTitle(currentLang),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = AppStrings.userDetailSubtitle(currentLang),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        // Status Badges & Refresh Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CineStreamRefreshButton(
                                isRefreshing = isRefreshing,
                                onClick = { viewModel.refresh() },
                                contentDescription = AppStrings.refresh(currentLang)
                            )

                            if (u.hasPendingSync || pendingOperations.isNotEmpty()) {
                                StatusBadge(
                                    text = if (currentLang == AppLanguage.ARABIC) "⏳ قيد المزامنة" else "⏳ SYNC PENDING",
                                    bg = WarningOrange.copy(alpha = 0.2f),
                                    border = WarningOrange,
                                    textColor = WarningOrange
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            // Account Ban Badge
                            when {
                                u.isAccountBanned -> {
                                    StatusBadge(
                                        text = "BANNED",
                                        bg = CineStreamRed.copy(alpha = 0.2f),
                                        border = CineStreamRed,
                                        textColor = CineStreamRed
                                    )
                                }
                                u.isBanExpired -> {
                                    StatusBadge(
                                        text = "BAN EXPIRED",
                                        bg = WarningOrange.copy(alpha = 0.2f),
                                        border = WarningOrange,
                                        textColor = WarningOrange
                                    )
                                }
                                else -> {
                                    StatusBadge(
                                        text = "GOOD STANDING",
                                        bg = MetricGreenBg.copy(alpha = 0.3f),
                                        border = MetricGreen,
                                        textColor = MetricGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Subscription Badge
                            when (u.subscriptionState) {
                                SubscriptionState.ACTIVE_PRO -> {
                                    StatusBadge(
                                        text = u.subscriptionTier.uppercase(),
                                        bg = MetricPurpleBg.copy(alpha = 0.5f),
                                        border = MetricPurple,
                                        textColor = MetricPurple
                                    )
                                }
                                SubscriptionState.EXPIRED_PRO -> {
                                    StatusBadge(
                                        text = "EXPIRED PRO",
                                        bg = WarningOrange.copy(alpha = 0.2f),
                                        border = WarningOrange,
                                        textColor = WarningOrange
                                    )
                                }
                                SubscriptionState.FREE -> {
                                    StatusBadge(
                                        text = "FREE",
                                        bg = DarkSurfaceVariant,
                                        border = DarkCardBorder,
                                        textColor = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Account & Profile Details Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF202532), CircleShape)
                                        .border(1.dp, if (u.isSubscriptionActive) MetricPurple else DarkCardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (u.photoUrl.isNotBlank()) {
                                        coil.compose.AsyncImage(
                                            model = u.photoUrl,
                                            contentDescription = "User Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = (u.username.firstOrNull() ?: 'U').uppercaseChar().toString(),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = u.username.ifBlank { "User" },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                        if (u.role == "admin") {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(CineStreamRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .border(0.5.dp, CineStreamRed, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("ADMIN", color = CineStreamRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(
                                        text = u.email,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = DarkCardBorder)

                            // UID Row with Copy
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(AppStrings.uidField(currentLang), color = TextSecondary, fontSize = 11.sp)
                                    Text(u.id, color = Color.White, fontSize = 12.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("copy_uid_button")
                                        .bounceClick(scaleDown = 0.85f) {
                                            clipboardManager.setText(AnnotatedString(u.id))
                                            Toast.makeText(context, AppStrings.copied(currentLang), Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = AppStrings.copy(currentLang), tint = CineStreamRed, modifier = Modifier.size(16.dp))
                                }
                            }

                            // Created Date & Last Login
                            val createdDateStr = if (u.createdAt > 0L) {
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(u.createdAt))
                            } else "-"
                            val loginDateStr = if (u.lastLoginTimestamp > 0L) {
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(u.lastLoginTimestamp))
                            } else AppStrings.neverLoggedIn(currentLang)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(AppStrings.createdAtField(currentLang), color = TextSecondary, fontSize = 12.sp)
                                Text(createdDateStr, color = Color.White, fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (currentLang == AppLanguage.ARABIC) "آخر تسجيل دخول" else "Last Active", color = TextSecondary, fontSize = 12.sp)
                                Text(loginDateStr, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Account Security & Global Ban Card (Phase C1 Deliverable)
                    AccountSecurityBanCard(
                        user = u,
                        currentLang = currentLang,
                        onBanClick = { showBanDialog = true },
                        onUnbanClick = { showUnbanConfirmDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Subscription & Pro Tier Management Card (Phase C1 Deliverable)
                    SubscriptionManagementCard(
                        user = u,
                        currentLang = currentLang,
                        onManageClick = { showSubscriptionDialog = true },
                        onRevokeClick = { showRevokeSubDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Feature Restrictions Card (Selective fine-grained access)
                    FeatureRestrictionsCard(
                        user = u,
                        currentLang = currentLang,
                        pendingOperations = pendingOperations,
                        onToggle = { feature, allowed ->
                            viewModel.togglePermission(feature, allowed)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Administrator Privileges Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = CineStreamRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    AppStrings.isAdministrator(currentLang),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (u.role == "admin") "Full Admin Portal Access (/admins authorized)" else "Standard User Access (Client only)",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = u.role == "admin",
                                    onCheckedChange = { showRoleConfirmDialog = true },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = CineStreamRed
                                    ),
                                    modifier = Modifier.testTag("admin_role_switch")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
}

@Composable
private fun StatusBadge(text: String, bg: Color, border: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AccountSecurityBanCard(
    user: User,
    currentLang: AppLanguage,
    onBanClick: () -> Unit,
    onUnbanClick: () -> Unit
) {
    val isBanned = user.isAccountBanned
    val isExpired = user.isBanExpired

    val cardBg = when {
        isBanned -> CineStreamRed.copy(alpha = 0.12f)
        isExpired -> WarningOrange.copy(alpha = 0.12f)
        else -> DarkSurface
    }
    val cardBorder = when {
        isBanned -> CineStreamRed
        isExpired -> WarningOrange
        else -> DarkCardBorder
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(cardBorder, cardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBanned || isExpired) Icons.Default.Block else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isBanned) CineStreamRed else if (isExpired) WarningOrange else MetricGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        AppStrings.accountSecurityTitle(currentLang),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Action button
                if (isBanned || isExpired) {
                    Button(
                        onClick = onUnbanClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MetricGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("unban_account_button")
                    ) {
                        Text(AppStrings.unbanAccountBtn(currentLang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onBanClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CineStreamRed),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(CineStreamRed, CineStreamRed))),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("ban_account_button")
                    ) {
                        Text(AppStrings.banAccountBtn(currentLang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status Description
            when {
                isBanned -> {
                    Text(
                        AppStrings.accountBannedDesc(currentLang),
                        color = CineStreamRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!user.banReason.isNullOrBlank()) {
                        Text(
                            "${AppStrings.banReasonLabel(currentLang)}: ${user.banReason}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    val expiryText = if (user.banExpiresAt != null) {
                        val dateFormatted = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(user.banExpiresAt))
                        "${AppStrings.banExpiresAtLabel(currentLang)} $dateFormatted"
                    } else {
                        AppStrings.banPermanentNotice(currentLang)
                    }
                    Text(expiryText, color = TextSecondary, fontSize = 11.sp)
                }
                isExpired -> {
                    Text(
                        AppStrings.banExpiredDesc(currentLang),
                        color = WarningOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!user.banReason.isNullOrBlank()) {
                        Text(
                            "${AppStrings.banReasonLabel(currentLang)}: ${user.banReason}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (user.banExpiresAt != null) {
                        val dateFormatted = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(user.banExpiresAt))
                        Text(
                            "${if (currentLang == AppLanguage.ARABIC) "انتهت الصلاحية في:" else "Expired on:"} $dateFormatted",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                else -> {
                    Text(
                        AppStrings.accountGoodStanding(currentLang),
                        color = MetricGreen,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionManagementCard(
    user: User,
    currentLang: AppLanguage,
    onManageClick: () -> Unit,
    onRevokeClick: () -> Unit
) {
    val state = user.subscriptionState
    val cardBg = when (state) {
        SubscriptionState.ACTIVE_PRO -> MetricPurpleBg.copy(alpha = 0.25f)
        SubscriptionState.EXPIRED_PRO -> WarningOrange.copy(alpha = 0.12f)
        SubscriptionState.FREE -> DarkSurface
    }
    val cardBorder = when (state) {
        SubscriptionState.ACTIVE_PRO -> MetricPurple
        SubscriptionState.EXPIRED_PRO -> WarningOrange
        SubscriptionState.FREE -> DarkCardBorder
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(cardBorder, cardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Icon + Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                (if (state == SubscriptionState.ACTIVE_PRO) MetricPurple else if (state == SubscriptionState.EXPIRED_PRO) WarningOrange else TextSecondary).copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = if (state == SubscriptionState.ACTIVE_PRO) MetricPurple else if (state == SubscriptionState.EXPIRED_PRO) WarningOrange else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = AppStrings.subscriptionSectionTitle(currentLang),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "إدارة باقة الاشتراك والترقية" else "PRO / VIP Subscription Control",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                // Current Tier Badge
                when (state) {
                    SubscriptionState.ACTIVE_PRO -> {
                        Box(
                            modifier = Modifier
                                .background(MetricPurple.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .border(1.dp, MetricPurple, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "VIP / PRO",
                                color = MetricPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    SubscriptionState.EXPIRED_PRO -> {
                        Box(
                            modifier = Modifier
                                .background(WarningOrange.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .border(1.dp, WarningOrange, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "اشتراك منتهي" else "EXPIRED",
                                color = WarningOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    SubscriptionState.FREE -> {
                        Box(
                            modifier = Modifier
                                .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "حساب مجاني" else "FREE TIER",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = cardBorder.copy(alpha = 0.3f), thickness = 0.8.dp)

            // Info Details Section
            when (state) {
                SubscriptionState.ACTIVE_PRO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "حالة الاشتراك:" else "Status:",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${AppStrings.activeProBadge(currentLang)} (${user.subscriptionTier.uppercase()})",
                                color = MetricPurple,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val expiryStr = if (user.subscriptionExpiresAt != null) {
                            val dateFormatted = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(user.subscriptionExpiresAt))
                            dateFormatted
                        } else {
                            if (currentLang == AppLanguage.ARABIC) "مدى الحياة (دائم)" else "Lifetime (Permanent)"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "تاريخ الانتهاء:" else "Expires At:",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = expiryStr,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                SubscriptionState.EXPIRED_PRO -> {
                    val dateFormatted = if (user.subscriptionExpiresAt != null) {
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(user.subscriptionExpiresAt))
                    } else "-"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "انتهى في:" else "Expired On:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = dateFormatted,
                            color = WarningOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                SubscriptionState.FREE -> {
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "المستخدم مسجل في الخطة المجانية القياسية دون ميزات VIP."
                        else
                            "User is currently on the standard Free plan without VIP features.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Action Buttons Row (Clear, well-proportioned layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Manage / Upgrade Button
                Button(
                    onClick = onManageClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MetricPurple),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("subscription_manage_button")
                ) {
                    Icon(
                        imageVector = if (state == SubscriptionState.ACTIVE_PRO) Icons.Default.Edit else Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state == SubscriptionState.ACTIVE_PRO)
                            (if (currentLang == AppLanguage.ARABIC) "تمديد أو تعديل الاشتراك" else "Extend / Modify")
                        else
                            (if (currentLang == AppLanguage.ARABIC) "ترقية وتفعيل PRO" else "Upgrade to PRO"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // If currently Pro, provide Revoke Subscription button
                if (state == SubscriptionState.ACTIVE_PRO) {
                    OutlinedButton(
                        onClick = onRevokeClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CineStreamRed),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(listOf(CineStreamRed.copy(alpha = 0.7f), CineStreamRed.copy(alpha = 0.7f)))
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("subscription_revoke_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Revoke",
                            tint = CineStreamRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "إلغاء الاشتراك" else "Revoke",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CineStreamRed,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRestrictionsCard(
    user: User,
    currentLang: AppLanguage,
    pendingOperations: Set<String> = emptySet(),
    onToggle: (String, Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = CineStreamRed)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        AppStrings.featureRestrictionsSection(currentLang),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        if (currentLang == AppLanguage.ARABIC) "تعديل الصلاحيات الفردية دون إيقاف الحساب كاملاً" else "Fine-grained feature control without suspending account",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Checks ONLY if THIS specific permission has an in-flight operation
            fun isFeaturePending(vararg keys: String): Boolean {
                val userPrefix = "user_${user.id}"
                return pendingOperations.any { op ->
                    op.contains(userPrefix) && keys.any { op.contains(it, ignoreCase = true) }
                }
            }

            // 1. Watching & Streaming
            PermissionToggleRow(
                title = AppStrings.canWatch(currentLang),
                description = AppStrings.canWatchDesc(currentLang),
                icon = Icons.Default.PlayCircle,
                iconTint = MetricGreen,
                isEnabled = user.isWatchAllowed,
                isPendingSync = isFeaturePending("canWatch", "watchBan", "watch"),
                currentLang = currentLang,
                onToggle = { onToggle("watch", user.isWatchAllowed) },
                tag = "feature_toggle_watch"
            )

            // 2. Offline Downloads
            PermissionToggleRow(
                title = AppStrings.canDownload(currentLang),
                description = AppStrings.canDownloadDesc(currentLang),
                icon = Icons.Default.FileDownload,
                iconTint = MetricBlue,
                isEnabled = user.isDownloadAllowed,
                isPendingSync = isFeaturePending("canDownload", "downloadBan", "download"),
                currentLang = currentLang,
                onToggle = { onToggle("download", user.isDownloadAllowed) },
                tag = "feature_toggle_download"
            )

            // 3. Chat & Community
            PermissionToggleRow(
                title = AppStrings.canChat(currentLang),
                description = AppStrings.canChatDesc(currentLang),
                icon = Icons.Default.Chat,
                iconTint = WarningOrange,
                isEnabled = user.isChatAllowed,
                isPendingSync = isFeaturePending("canChat", "chatBan", "chat"),
                currentLang = currentLang,
                onToggle = { onToggle("chat", user.isChatAllowed) },
                tag = "feature_toggle_chat"
            )

            // 4. Stories & Clips
            PermissionToggleRow(
                title = AppStrings.canStory(currentLang),
                description = AppStrings.canStoryDesc(currentLang),
                icon = Icons.Default.CameraAlt,
                iconTint = MetricPurple,
                isEnabled = user.isStoryAllowed,
                isPendingSync = isFeaturePending("canStory", "storyBan", "story"),
                currentLang = currentLang,
                onToggle = { onToggle("story", user.isStoryAllowed) },
                tag = "feature_toggle_story"
            )

            // 5. P2P Sharing
            PermissionToggleRow(
                title = AppStrings.canP2P(currentLang),
                description = AppStrings.canP2PDesc(currentLang),
                icon = Icons.Default.Share,
                iconTint = MetricGreen,
                isEnabled = user.isP2PAllowed,
                isPendingSync = isFeaturePending("canP2P", "p2pBan", "p2p"),
                currentLang = currentLang,
                onToggle = { onToggle("p2p", user.isP2PAllowed) },
                tag = "feature_toggle_p2p"
            )

            // 6. Media Comments
            PermissionToggleRow(
                title = AppStrings.canComment(currentLang),
                description = AppStrings.canCommentDesc(currentLang),
                icon = Icons.Default.Comment,
                iconTint = MetricBlue,
                isEnabled = user.isCommentAllowed,
                isPendingSync = isFeaturePending("canComment", "comment"),
                currentLang = currentLang,
                onToggle = { onToggle("comment", user.isCommentAllowed) },
                tag = "feature_toggle_comment"
            )

            // 7. Content Upload
            PermissionToggleRow(
                title = AppStrings.canUpload(currentLang),
                description = AppStrings.canUploadDesc(currentLang),
                icon = Icons.Default.CloudUpload,
                iconTint = MetricPurple,
                isEnabled = user.isUploadAllowed,
                isPendingSync = isFeaturePending("canUpload", "upload"),
                currentLang = currentLang,
                onToggle = { onToggle("upload", user.isUploadAllowed) },
                tag = "feature_toggle_upload"
            )

            // 8. Media Requests
            PermissionToggleRow(
                title = AppStrings.canRequest(currentLang),
                description = AppStrings.canRequestDesc(currentLang),
                icon = Icons.Default.LiveTv,
                iconTint = WarningOrange,
                isEnabled = user.isRequestAllowed,
                isPendingSync = isFeaturePending("canRequest", "request"),
                currentLang = currentLang,
                onToggle = { onToggle("request", user.isRequestAllowed) },
                tag = "feature_toggle_request"
            )
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isEnabled: Boolean,
    isPendingSync: Boolean = false,
    currentLang: AppLanguage = AppLanguage.ARABIC,
    onToggle: () -> Unit,
    tag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isPendingSync) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(WarningOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, WarningOrange.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(9.dp),
                            strokeWidth = 1.2.dp,
                            color = WarningOrange
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "جاري المزامنة..." else "Syncing...",
                            color = WarningOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            enabled = !isPendingSync,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MetricGreen,
                disabledCheckedTrackColor = MetricGreen.copy(alpha = 0.5f),
                disabledUncheckedTrackColor = DarkSurfaceVariant
            ),
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BanAccountDialog(
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, expiresAt: Long?) -> Unit
) {
    var selectedDurationIndex by remember { mutableStateOf(0) } // 0: 24h, 1: 3d, 2: 7d, 3: 30d, 4: Permanent, 5: Custom
    var customDaysText by remember { mutableStateOf("14") }
    var reasonText by remember { mutableStateOf("") }

    val presetReasons = listOf(
        AppStrings.banReasonViolation(currentLang),
        AppStrings.banReasonAbuse(currentLang),
        AppStrings.banReasonSuspicious(currentLang),
        AppStrings.banReasonCopyright(currentLang)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(AppStrings.banAccountBtn(currentLang), color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(AppStrings.banDurationLabel(currentLang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                // Duration Selector
                val durations = listOf(
                    AppStrings.ban24h(currentLang) to (1L * 24 * 60 * 60 * 1000),
                    AppStrings.ban3Days(currentLang) to (3L * 24 * 60 * 60 * 1000),
                    AppStrings.ban7Days(currentLang) to (7L * 24 * 60 * 60 * 1000),
                    AppStrings.ban30Days(currentLang) to (30L * 24 * 60 * 60 * 1000),
                    AppStrings.permanentBan(currentLang) to null,
                    AppStrings.customDays(currentLang) to -1L
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durations.forEachIndexed { index, (label, _) ->
                        FilterChip(
                            selected = selectedDurationIndex == index,
                            onClick = { selectedDurationIndex = index },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineStreamRed,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                if (selectedDurationIndex == 5) {
                    OutlinedTextField(
                        value = customDaysText,
                        onValueChange = { customDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(AppStrings.customDays(currentLang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(AppStrings.banReasonLabel(currentLang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                // Reason preset chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetReasons.forEach { preset ->
                        FilterChip(
                            selected = reasonText == preset,
                            onClick = { reasonText = preset },
                            label = { Text(preset, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineStreamRed.copy(alpha = 0.5f),
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    placeholder = { Text(AppStrings.banReasonLabel(currentLang), color = TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            CineStreamLoadingButton(
                onClick = {
                    val expiresAt = when (selectedDurationIndex) {
                        0 -> System.currentTimeMillis() + 1L * 24 * 60 * 60 * 1000
                        1 -> System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000
                        2 -> System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                        3 -> System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                        4 -> null // Permanent
                        5 -> {
                            val days = customDaysText.toLongOrNull() ?: 1L
                            System.currentTimeMillis() + days * 24 * 60 * 60 * 1000
                        }
                        else -> null
                    }
                    onConfirm(reasonText.ifBlank { "Banned by administrator" }, expiresAt)
                },
                containerColor = CineStreamRed,
                shape = RoundedCornerShape(10.dp),
                text = AppStrings.confirm(currentLang)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel(currentLang), color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionManagementDialog(
    currentLang: AppLanguage,
    currentUser: User,
    onDismiss: () -> Unit,
    onConfirm: (tier: String, durationDays: Int?) -> Unit
) {
    var selectedTier by remember { mutableStateOf(if (currentUser.subscriptionTier == "vip") "vip" else "pro") }
    var selectedDurationIndex by remember { mutableStateOf(0) } // 0: 30d, 1: 90d, 2: 180d, 3: 365d, 4: Lifetime, 5: Custom
    var customDaysText by remember { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(AppStrings.grantOrExtendSub(currentLang), color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(AppStrings.selectTier(currentLang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = selectedTier == "pro",
                        onClick = { selectedTier = "pro" },
                        label = { Text("PRO TIER", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MetricPurple,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = selectedTier == "vip",
                        onClick = { selectedTier = "vip" },
                        label = { Text("VIP TIER", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MetricPurple,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(AppStrings.banDurationLabel(currentLang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                val durationOptions = listOf(
                    AppStrings.sub1Month(currentLang) to 30,
                    AppStrings.sub3Months(currentLang) to 90,
                    AppStrings.sub6Months(currentLang) to 180,
                    AppStrings.sub1Year(currentLang) to 365,
                    AppStrings.subLifetime(currentLang) to null,
                    AppStrings.customDays(currentLang) to -1
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durationOptions.forEachIndexed { index, (label, _) ->
                        FilterChip(
                            selected = selectedDurationIndex == index,
                            onClick = { selectedDurationIndex = index },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MetricPurple,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                if (selectedDurationIndex == 5) {
                    OutlinedTextField(
                        value = customDaysText,
                        onValueChange = { customDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(AppStrings.customDays(currentLang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            CineStreamLoadingButton(
                onClick = {
                    val durationDays: Int? = when (selectedDurationIndex) {
                        0 -> 30
                        1 -> 90
                        2 -> 180
                        3 -> 365
                        4 -> null // Lifetime
                        5 -> customDaysText.toIntOrNull() ?: 30
                        else -> 30
                    }
                    onConfirm(selectedTier, durationDays)
                },
                containerColor = MetricPurple,
                shape = RoundedCornerShape(10.dp),
                text = AppStrings.confirm(currentLang)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel(currentLang), color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}
