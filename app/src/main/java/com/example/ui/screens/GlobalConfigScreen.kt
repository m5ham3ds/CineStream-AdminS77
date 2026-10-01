package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.EconomyConfig
import com.example.models.FeatureControlConfig
import com.example.models.FeatureState
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.CineStreamLoadingButton
import com.example.ui.components.CineStreamRefreshButton
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.viewmodels.ConfigViewModel

@Composable
fun GlobalConfigScreen(
    viewModel: ConfigViewModel = viewModel()
) {
    val config by viewModel.config.collectAsState()
    val featureConfig by viewModel.featureConfig.collectAsState()
    val economyConfig by viewModel.economyConfig.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentLang by AppSettings.language.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    var maintenanceEnabled by remember(config.maintenanceEnabled) { mutableStateOf(config.maintenanceEnabled) }
    var maintenanceTitle by remember(config.maintenanceTitle) { mutableStateOf(config.maintenanceTitle) }
    var maintenanceMessage by remember(config.maintenanceMessage) { mutableStateOf(config.maintenanceMessage) }
    var minVersionCode by remember(config.minimumVersionCode) { mutableStateOf(config.minimumVersionCode.toString()) }

    var offlineDays by remember(config.defaultOfflineDays) { mutableIntStateOf(config.defaultOfflineDays) }
    var forcedAds by remember(config.defaultForcedAds) { mutableIntStateOf(config.defaultForcedAds) }
    var cloudName by remember(config.cloudinaryCloudName) { mutableStateOf(config.cloudinaryCloudName) }
    var uploadPreset by remember(config.cloudinaryUploadPreset) { mutableStateOf(config.cloudinaryUploadPreset) }

    var pendingMaintenanceTarget by remember { mutableStateOf<Boolean?>(null) }

    if (pendingMaintenanceTarget != null) {
        val target = pendingMaintenanceTarget!!
        AlertDialog(
            onDismissRequest = { pendingMaintenanceTarget = null },
            title = {
                Text(
                    text = if (target) AppStrings.enableMaintenanceConfirmTitle(currentLang) else AppStrings.disableMaintenanceConfirmTitle(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (target) AppStrings.enableMaintenanceConfirmMsg(currentLang) else AppStrings.disableMaintenanceConfirmMsg(currentLang),
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        val chosenState = target
                        pendingMaintenanceTarget = null
                        maintenanceEnabled = chosenState
                        viewModel.updateMaintenance(
                            enabled = chosenState,
                            title = maintenanceTitle,
                            message = maintenanceMessage,
                            minVersion = minVersionCode.toIntOrNull() ?: 1
                        )
                    },
                    containerColor = if (target) MetricRed else CineStreamRed,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.confirm(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingMaintenanceTarget = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
            // Header Title with Refresh Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.globalConfig(currentLang),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AppStrings.globalConfigSubtitle(currentLang),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                CineStreamRefreshButton(
                    isRefreshing = isRefreshing,
                    onClick = { viewModel.refresh() },
                    contentDescription = AppStrings.refresh(currentLang)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Maintenance Mode Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (maintenanceEnabled) MetricRedBg else DarkSurfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Engineering,
                                    contentDescription = null,
                                    tint = if (maintenanceEnabled) MetricRed else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    AppStrings.maintenanceSection(currentLang),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    AppStrings.maintenanceToggleDesc(currentLang),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = maintenanceEnabled,
                            onCheckedChange = { targetState ->
                                pendingMaintenanceTarget = targetState
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MetricRed
                            )
                        )
                    }

                    if (maintenanceEnabled) {
                        OutlinedTextField(
                            value = maintenanceTitle,
                            onValueChange = { maintenanceTitle = it },
                            label = { Text(AppStrings.maintenanceTitleField(currentLang), color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CineStreamRed,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = maintenanceMessage,
                            onValueChange = { maintenanceMessage = it },
                            label = { Text(AppStrings.maintenanceMessageField(currentLang), color = TextSecondary) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CineStreamRed,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )
                    }

                    OutlinedTextField(
                        value = minVersionCode,
                        onValueChange = { minVersionCode = it.filter { ch -> ch.isDigit() } },
                        label = { Text(AppStrings.minClientVersionField(currentLang), color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineStreamRed,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Security & DRM Policy Card
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
                        Icon(Icons.Default.Security, contentDescription = null, tint = CineStreamRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            AppStrings.securityDrmSection(currentLang),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Anti-Tamper Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(AppStrings.antiTamperToggle(currentLang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(AppStrings.antiTamperDesc(currentLang), color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = {},
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CineStreamRed
                            )
                        )
                    }

                    // Screen Recording & Screenshot Blocking
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(AppStrings.screenshotBlockToggle(currentLang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(AppStrings.screenshotBlockDesc(currentLang), color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = {},
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CineStreamRed
                            )
                        )
                    }

                    // Root Detection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(AppStrings.rootDetectionToggle(currentLang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(AppStrings.rootDetectionDesc(currentLang), color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = {},
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CineStreamRed
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Offline & Ads Policy Card
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
                        Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = MetricBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            AppStrings.offlinePolicySection(currentLang),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedTextField(
                        value = offlineDays.toString(),
                        onValueChange = { offlineDays = it.toIntOrNull() ?: offlineDays },
                        label = { Text(AppStrings.downloadExpiryField(currentLang), color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineStreamRed,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )

                    HorizontalDivider(color = DarkCardBorder)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = MetricPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            AppStrings.adPolicySection(currentLang),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedTextField(
                        value = forcedAds.toString(),
                        onValueChange = { forcedAds = it.toIntOrNull() ?: forcedAds },
                        label = { Text(AppStrings.adIntervalField(currentLang), color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineStreamRed,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Cloudinary Media Storage Card
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
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = CineStreamRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentLang == AppLanguage.ARABIC) "تخزين الوسائط (Cloudinary)" else "Cloudinary Media Storage",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = if (currentLang == AppLanguage.ARABIC) "النظام المعتمد لتخزين وتوزيع وسائط CineStream (الصور والخلفيات) بدلاً من Firebase Storage" else "Authoritative media storage and delivery engine for CineStream images and avatars",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = cloudName,
                        onValueChange = { cloudName = it },
                        label = { Text("Cloud Name", color = TextSecondary) },
                        placeholder = { Text("e.g. cinestream", color = TextSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineStreamRed,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )

                    OutlinedTextField(
                        value = uploadPreset,
                        onValueChange = { uploadPreset = it },
                        label = { Text("Upload Preset (Unsigned)", color = TextSecondary) },
                        placeholder = { Text("e.g. cinestream_unsigned", color = TextSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineStreamRed,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 5: Feature Control Center Card (/config/features) - Phase 03A
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
                        Icon(Icons.Default.ToggleOn, contentDescription = null, tint = MetricPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "مركز التحكم بالميزات والنظام" else "Feature Control Center",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "مفاتيح التعطيل والوضع القادم (/config/features)" else "Feature Flags & Kill Switches (/config/features)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    val featuresList = listOf(
                        Triple("subscriptions", if (currentLang == AppLanguage.ARABIC) "الاشتراكات (Subscriptions)" else "Subscriptions", featureConfig.subscriptions),
                        Triple("points", if (currentLang == AppLanguage.ARABIC) "نظام النقاط (Points System)" else "Points Economy", featureConfig.points),
                        Triple("dailyLogin", if (currentLang == AppLanguage.ARABIC) "تسجيل الدخول اليومي (Daily Login)" else "Daily Login Rewards", featureConfig.dailyLogin),
                        Triple("rewardedAds", if (currentLang == AppLanguage.ARABIC) "إعلانات المكافآت (Rewarded Ads)" else "Rewarded Ads", featureConfig.rewardedAds),
                        Triple("tasks", if (currentLang == AppLanguage.ARABIC) "مهام المكافآت (Reward Tasks)" else "Reward Tasks", featureConfig.tasks),
                        Triple("leaderboard", if (currentLang == AppLanguage.ARABIC) "قائمة المتصدرين (Leaderboard)" else "Leaderboard", featureConfig.leaderboard)
                    )

                    featuresList.forEach { (key, label, itemConfig) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                val badgeColor = when (itemConfig.state) {
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
                                        text = itemConfig.state.name,
                                        color = badgeColor,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = itemConfig.state == FeatureState.ACTIVE,
                                    onClick = { viewModel.updateFeature(key, FeatureState.ACTIVE) },
                                    label = { Text("ACTIVE", fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MetricGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = itemConfig.state == FeatureState.COMING_SOON,
                                    onClick = { viewModel.updateFeature(key, FeatureState.COMING_SOON) },
                                    label = { Text("COMING SOON", fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MetricBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = itemConfig.state == FeatureState.DISABLED,
                                    onClick = { viewModel.updateFeature(key, FeatureState.DISABLED) },
                                    label = { Text("DISABLED", fontSize = 10.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MetricRed,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 6: Points Economy & Redemption Pricing Card (/config/economy) - Phase 03A
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
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = MetricOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "تسعير استبدال الاشتراكات بالنقاط" else "Subscription Points Redemption",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "تكلفة النقاط لكل باقة اشتراك (/config/economy)" else "Points required per plan (/config/economy)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    val costs = economyConfig.redemptionCosts
                    val plans = listOf(
                        "pro_lite_1d" to "Pro Lite (1 Day / 24h)",
                        "pro_lite_7d" to "Pro Lite (7 Days)",
                        "pro_lite_10d" to "Pro Lite (10 Days)",
                        "pro_30d" to "Pro Standard (30 Days)"
                    )

                    plans.forEach { (planSku, label) ->
                        var costText by remember(costs[planSku]) { mutableStateOf((costs[planSku] ?: 100L).toString()) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(planSku, color = TextSecondary, fontSize = 10.5.sp)
                            }
                            OutlinedTextField(
                                value = costText,
                                onValueChange = {
                                    val filtered = it.filter { ch -> ch.isDigit() }
                                    costText = filtered
                                    val newCost = filtered.toLongOrNull()
                                    if (newCost != null) {
                                        val updatedMap = economyConfig.redemptionCosts.toMutableMap()
                                        updatedMap[planSku] = newCost
                                        viewModel.saveEconomy(economyConfig.copy(redemptionCosts = updatedMap))
                                    }
                                },
                                label = { Text("PTS", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }

                    // Economy Parameters Summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(if (currentLang == AppLanguage.ARABIC) "نقاط الإعلان" else "Pts / Ad", color = TextSecondary, fontSize = 11.sp)
                            Text("${economyConfig.rewardedAdPoints} PTS", color = MetricOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(if (currentLang == AppLanguage.ARABIC) "حد الإعلانات/يوم" else "Max Ads / Day", color = TextSecondary, fontSize = 11.sp)
                            Text("${economyConfig.rewardedAdDailyCap}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(if (currentLang == AppLanguage.ARABIC) "فترة التهدئة" else "Cooldown", color = TextSecondary, fontSize = 11.sp)
                            Text("${economyConfig.rewardedAdCooldownSeconds}s", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Policies Button with tactile animation & loading spinner
            CineStreamLoadingButton(
                onClick = {
                    viewModel.updateMaintenance(
                        enabled = maintenanceEnabled,
                        title = maintenanceTitle,
                        message = maintenanceMessage,
                        minVersion = minVersionCode.toIntOrNull() ?: 1
                    )
                    viewModel.updateDrmSettings(days = offlineDays, ads = forcedAds)
                    viewModel.updateCloudinaryConfig(cloudName = cloudName, uploadPreset = uploadPreset)
                },
                isLoading = isSaving,
                loadingText = AppStrings.saving(currentLang),
                leadingIcon = Icons.Default.Save,
                containerColor = CineStreamRed,
                shape = RoundedCornerShape(12.dp),
                text = AppStrings.saveGlobalPolicies(currentLang),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
}
