package com.example.ui.screens

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.ManagedExtension
import com.example.models.ManagedExtensionCapabilities
import com.example.models.ManagedExtensionContentTypes
import com.example.models.ManagedExtensionStatus
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.CineStreamLoadingButton
import com.example.ui.components.CineStreamRefreshButton
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.viewmodels.ManagedExtensionsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManagedExtensionsScreen(
    onBackClick: () -> Unit = {},
    onNavigateToSearchOrder: () -> Unit = {},
    viewModel: ManagedExtensionsViewModel = viewModel()
) {
    val extensions by viewModel.filteredExtensions.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()
    val maintenanceCount by viewModel.maintenanceCount.collectAsState()
    val disabledCount by viewModel.disabledCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentLang by AppSettings.language.collectAsState()

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingExtension by remember { mutableStateOf<ManagedExtension?>(null) }
    var extensionToDelete by remember { mutableStateOf<ManagedExtension?>(null) }
    var extensionToDeprecate by remember { mutableStateOf<ManagedExtension?>(null) }
    var domainRotationExtension by remember { mutableStateOf<ManagedExtension?>(null) }
    var priorityEditExtension by remember { mutableStateOf<ManagedExtension?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingExtension = null
                    showEditorDialog = true
                },
                containerColor = CineStreamRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = AppStrings.addManagedExtension(currentLang))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.addManagedExtension(currentLang), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) { paddingValues ->
        AdaptiveScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.managedExtensions(currentLang),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = AppStrings.managedExtensionsSubtitle(currentLang),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.seedDefaultScrapers() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = if (currentLang == AppLanguage.ARABIC) "مزامنة المصادر الافتراضية" else "Sync Default Scrapers",
                                tint = MetricPurple
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        CineStreamRefreshButton(
                            isRefreshing = isRefreshing,
                            onClick = { viewModel.refresh() },
                            contentDescription = AppStrings.refresh(currentLang)
                        )
                    }
                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Pill Tabs: Switch between Catalog & Search Order
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(CineStreamRed, RoundedCornerShape(10.dp))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.tabExtensions(currentLang), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onNavigateToSearchOrder() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sort, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.tabSearchOrder(currentLang), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Informational Architecture Banner (Pure Config Guarantee)
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MetricGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.managedExtensionsBanner(currentLang),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metric Summary Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricSummaryPill(
                        label = "Total",
                        value = totalCount.toString(),
                        color = MetricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryPill(
                        label = "Active",
                        value = activeCount.toString(),
                        color = MetricGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryPill(
                        label = "Maint.",
                        value = maintenanceCount.toString(),
                        color = WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSummaryPill(
                        label = "Disabled",
                        value = disabledCount.toString(),
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (currentLang == AppLanguage.ARABIC) "بحث بالاسم، مفتاح الكاشط، أو الرابط..." else "Search by name, scraperKey, or URL...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
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

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CineStreamRed,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        )
                    )
                    ManagedExtensionStatus.values().forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { viewModel.setStatusFilter(st) },
                            label = { Text(st.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (st) {
                                    ManagedExtensionStatus.ACTIVE -> MetricGreen
                                    ManagedExtensionStatus.MAINTENANCE -> WarningOrange
                                    ManagedExtensionStatus.DISABLED -> TextSecondary
                                    ManagedExtensionStatus.DEPRECATED -> MetricRed
                                },
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Extension List
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CineStreamRed)
                    }
                } else if (extensions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ExtensionOff,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC) "لا توجد مصادر كشط معروضة حالياً" else "No managed extensions found",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (currentLang == AppLanguage.ARABIC)
                                    "يمكنك استيراد ومزامنة المصادر الأساسية (QFilm, WitAnime, EgyDead, Akwam) بنقرة واحدة:"
                                    else "Populate standard CineStream scrapers (QFilm, WitAnime, EgyDead, Akwam) with one tap:",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.seedDefaultScrapers() },
                                colors = ButtonDefaults.buttonColors(containerColor = CineStreamRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (currentLang == AppLanguage.ARABIC) "مزامنة المصادر الافتراضية الآن" else "Sync Default Scrapers Now",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(extensions, key = { it.extensionId }) { ext ->
                            ManagedExtensionCard(
                                ext = ext,
                                onEdit = {
                                    editingExtension = ext
                                    showEditorDialog = true
                                },
                                onRotateDomain = {
                                    domainRotationExtension = ext
                                },
                                onQuickToggleEnabled = { isEnabled ->
                                    val newStatus = if (isEnabled) ManagedExtensionStatus.ACTIVE else ManagedExtensionStatus.DISABLED
                                    viewModel.updateStatus(ext.extensionId, newStatus)
                                },
                                onStatusChange = { newStatus ->
                                    viewModel.updateStatus(ext.extensionId, newStatus)
                                },
                                onPriorityChange = { newPriority ->
                                    viewModel.updatePriority(ext.extensionId, newPriority)
                                },
                                onEditPriority = {
                                    priorityEditExtension = ext
                                },
                                onDeprecate = { extensionToDeprecate = ext },
                                onDelete = { extensionToDelete = ext }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showEditorDialog) {
        ManagedExtensionEditorDialog(
            currentLang = currentLang,
            extension = editingExtension,
            onDismiss = {
                showEditorDialog = false
                editingExtension = null
            },
            onSave = { savedExt, isNew ->
                viewModel.saveExtension(savedExt, isNew) {
                    showEditorDialog = false
                    editingExtension = null
                }
            }
        )
    }

    // Deprecate Confirmation Dialog
    extensionToDeprecate?.let { ext ->
        AlertDialog(
            onDismissRequest = { extensionToDeprecate = null },
            title = {
                Text(
                    AppStrings.confirmDeprecateTitle(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    AppStrings.confirmDeprecateMsg(ext.name, currentLang),
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        viewModel.deprecateExtension(ext.extensionId)
                        extensionToDeprecate = null
                    },
                    containerColor = WarningOrange,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.confirm(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { extensionToDeprecate = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Delete Confirmation Dialog (Guards destructive deletion)
    extensionToDelete?.let { ext ->
        AlertDialog(
            onDismissRequest = { extensionToDelete = null },
            title = {
                Text(
                    AppStrings.deleteManagedExtension(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (currentLang == AppLanguage.ARABIC)
                        "هل أنت متأكد من حذف تعريف المصدر '${ext.name}' نهائياً من Firestore؟ يُفضل استخدام الإحالة للتقاعد (DEPRECATED)."
                    else
                        "Are you sure you want to permanently delete '${ext.name}' from /managed_extensions? Deprecation is strongly recommended instead of hard deletion.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        viewModel.deleteExtension(ext.extensionId)
                        extensionToDelete = null
                    },
                    containerColor = MetricRed,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.delete(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { extensionToDelete = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Dynamic Domain Rotation Dialog
    domainRotationExtension?.let { ext ->
        var domainUrl by remember(ext) { mutableStateOf(ext.baseUrl) }
        var domainError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { domainRotationExtension = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MetricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (currentLang == AppLanguage.ARABIC) "تدوير النطاق (Domain Rotation)" else "Dynamic Domain Rotation",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "تعديل الرابط الأساسي لكاشط ${ext.name}. سيعمل تطبيق المستخدم فورياً بالرابط الجديد دون الحاجة لتحديث التطبيق:"
                        else
                            "Update Base URL for ${ext.name}. All user apps will immediately adopt the new domain without an update:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = domainUrl,
                        onValueChange = {
                            domainUrl = it.trim()
                            domainError = null
                        },
                        label = { Text("Base URL (HTTPS)") },
                        singleLine = true,
                        isError = domainError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (domainError != null) {
                        Text(domainError!!, color = MetricRed, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val err = com.example.validation.ManagedExtensionValidator.validateHttpsUrl(domainUrl, isBaseUrl = true)
                        if (err != null) {
                            domainError = err
                        } else {
                            viewModel.updateBaseUrl(ext.extensionId, domainUrl)
                            domainRotationExtension = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineStreamRed)
                ) {
                    Text(AppStrings.save(currentLang))
                }
            },
            dismissButton = {
                TextButton(onClick = { domainRotationExtension = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Priority Scheduling Dialog
    priorityEditExtension?.let { ext ->
        var priorityValue by remember(ext) { mutableStateOf(ext.priority.toString()) }

        AlertDialog(
            onDismissRequest = { priorityEditExtension = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MetricGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (currentLang == AppLanguage.ARABIC) "تعديل أولوية التشغيل" else "Priority Scheduling",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (currentLang == AppLanguage.ARABIC)
                            "تحديد ترتيب تشغيل ${ext.name}. الرقم الأعلى يبدأ أولاً في تطبيق المشاهدة:"
                        else
                            "Set priority for ${ext.name}. Higher number runs first in media playback:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = priorityValue,
                        onValueChange = { priorityValue = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Priority (e.g. 120)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = priorityValue.toIntOrNull() ?: 100
                        viewModel.updatePriority(ext.extensionId, p)
                        priorityEditExtension = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetricGreen)
                ) {
                    Text(AppStrings.save(currentLang))
                }
            },
            dismissButton = {
                TextButton(onClick = { priorityEditExtension = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
}

@Composable
private fun MetricSummaryPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(10.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagedExtensionCard(
    ext: ManagedExtension,
    onEdit: () -> Unit,
    onRotateDomain: () -> Unit = {},
    onQuickToggleEnabled: (Boolean) -> Unit = {},
    onStatusChange: (ManagedExtensionStatus) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onEditPriority: () -> Unit = {},
    onDeprecate: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    val statusColor = when (ext.statusEnum) {
        ManagedExtensionStatus.ACTIVE -> MetricGreen
        ManagedExtensionStatus.MAINTENANCE -> WarningOrange
        ManagedExtensionStatus.DISABLED -> TextSecondary
        ManagedExtensionStatus.DEPRECATED -> MetricRed
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Name + scraperKey + Priority + Status Badge + Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = ext.name.ifBlank { ext.extensionId },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E2330), RoundedCornerShape(6.dp))
                            .border(0.5.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ext.scraperKey,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetricPurple,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(MetricBlue.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .clickable { onEditPriority() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#${ext.priority}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetricBlue
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Priority",
                                tint = MetricBlue,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Kill Switch Toggle
                    Switch(
                        checked = ext.isOperational,
                        onCheckedChange = { onQuickToggleEnabled(it) },
                        modifier = Modifier.scale(0.72f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MetricGreen,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = DarkCardBorder
                        )
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                            .border(1.dp, statusColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = ext.statusEnum.name,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Definition", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MetricBlue) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rotate Domain / URL", color = MetricBlue) },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = MetricBlue) },
                                onClick = {
                                    showMenu = false
                                    onRotateDomain()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set Priority", color = MetricGreen) },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = MetricGreen) },
                                onClick = {
                                    showMenu = false
                                    onEditPriority()
                                }
                            )
                            HorizontalDivider(color = DarkCardBorder)
                            DropdownMenuItem(
                                text = { Text("Set ACTIVE", color = MetricGreen) },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MetricGreen) },
                                onClick = {
                                    showMenu = false
                                    onStatusChange(ManagedExtensionStatus.ACTIVE)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set MAINTENANCE", color = WarningOrange) },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = WarningOrange) },
                                onClick = {
                                    showMenu = false
                                    onStatusChange(ManagedExtensionStatus.MAINTENANCE)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set DISABLED (Kill Switch)", color = TextSecondary) },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = TextSecondary) },
                                onClick = {
                                    showMenu = false
                                    onStatusChange(ManagedExtensionStatus.DISABLED)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("DEPRECATE (Retain)", color = WarningOrange) },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, tint = WarningOrange) },
                                onClick = {
                                    showMenu = false
                                    onDeprecate()
                                }
                            )
                            HorizontalDivider(color = DarkCardBorder)
                            DropdownMenuItem(
                                text = { Text("Delete (Destructive)", color = MetricRed) },
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

            // Description if present
            if (ext.description.isNotBlank()) {
                Text(
                    text = ext.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }

            // Base URL Row with Copy & Quick Rotate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131720), RoundedCornerShape(8.dp))
                    .border(0.5.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, contentDescription = "HTTPS", tint = MetricGreen, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ext.baseUrl,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .bounceClick(scaleDown = 0.85f) { onRotateDomain() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Rotate Domain", tint = MetricBlue, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .bounceClick(scaleDown = 0.85f) {
                                clipboardManager.setText(AnnotatedString(ext.baseUrl))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Badges: Content Types
            if (ext.contentTypes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ext.contentTypes.forEach { type ->
                        Box(
                            modifier = Modifier
                                .background(MetricPurpleBg.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(type, color = MetricPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Badges: Capabilities
            if (ext.capabilities.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ext.capabilities.forEach { cap ->
                        Box(
                            modifier = Modifier
                                .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                                .border(0.5.dp, DarkCardBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(cap, color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }

            // Footer Version Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Min App: v${ext.minAppVersionCode} • API: v${ext.runtimeApiVersion} • Def: v${ext.definitionVersion}",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                Text(
                    text = "ID: ${ext.extensionId}",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagedExtensionEditorDialog(
    currentLang: AppLanguage,
    extension: ManagedExtension?,
    onDismiss: () -> Unit,
    onSave: (ManagedExtension, Boolean) -> Unit
) {
    val isNew = extension == null

    var extensionId by remember { mutableStateOf(extension?.extensionId ?: "") }
    var scraperKey by remember { mutableStateOf(extension?.scraperKey ?: "") }
    var name by remember { mutableStateOf(extension?.name ?: "") }
    var description by remember { mutableStateOf(extension?.description ?: "") }
    var baseUrl by remember { mutableStateOf(extension?.baseUrl ?: "https://") }
    var searchUrl by remember { mutableStateOf(extension?.searchUrl ?: "") }
    var runtimeApiVersion by remember { mutableStateOf(extension?.runtimeApiVersion?.toString() ?: "1") }
    var definitionVersion by remember { mutableStateOf(extension?.definitionVersion?.toString() ?: "1") }
    var minAppVersionCode by remember { mutableStateOf(extension?.minAppVersionCode?.toString() ?: "1") }
    var priority by remember { mutableStateOf(extension?.priority?.toString() ?: "100") }
    var selectedStatus by remember { mutableStateOf(extension?.statusEnum ?: ManagedExtensionStatus.ACTIVE) }
    var selectedCapabilities by remember { mutableStateOf(extension?.capabilities?.toSet() ?: setOf("SEARCH", "DETAILS", "EPISODES", "VIDEO_EXTRACTION")) }
    var selectedContentTypes by remember { mutableStateOf(extension?.contentTypes?.toSet() ?: setOf("MOVIE", "SERIES")) }
    var enabled by remember { mutableStateOf(extension?.enabled ?: true) }

    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) AppStrings.addManagedExtension(currentLang) else AppStrings.editManagedExtension(currentLang),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MetricRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Extension ID
                OutlinedTextField(
                    value = extensionId,
                    onValueChange = { if (isNew) extensionId = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                    label = { Text("Extension ID (e.g. arabseed)") },
                    enabled = isNew,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Scraper Key (Must match bundled Kotlin class)
                OutlinedTextField(
                    value = scraperKey,
                    onValueChange = { scraperKey = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }.lowercase() },
                    label = { Text("Scraper Key (e.g. qfilm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Display Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it.trim() },
                    label = { Text("Base URL (HTTPS only)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Search URL
                OutlinedTextField(
                    value = searchUrl,
                    onValueChange = { searchUrl = it.trim() },
                    label = { Text("Search URL (e.g. /search?q=%s)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority & Min App Version & Runtime API Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Priority") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minAppVersionCode,
                        onValueChange = { minAppVersionCode = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Min App Ver") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = runtimeApiVersion,
                        onValueChange = { runtimeApiVersion = it.filter { ch -> ch.isDigit() } },
                        label = { Text("API Ver") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Lifecycle Status Selector
                Text("Lifecycle Status", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ManagedExtensionStatus.values().forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.name, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineStreamRed,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                // Content Types Selection
                Text("Content Types", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ManagedExtensionContentTypes.ALL.forEach { ct ->
                        val isSelected = ct in selectedContentTypes
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedContentTypes = if (isSelected) selectedContentTypes - ct else selectedContentTypes + ct
                            },
                            label = { Text(ct, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MetricPurple,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                // Capabilities Selection
                Text("Capabilities", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ManagedExtensionCapabilities.ALL.forEach { cap ->
                        val isSelected = cap in selectedCapabilities
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCapabilities = if (isSelected) selectedCapabilities - cap else selectedCapabilities + cap
                            },
                            label = { Text(cap, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MetricGreen,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                // Enabled Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Global Enable Toggle", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MetricGreen)
                    )
                }
            }
        },
        confirmButton = {
            CineStreamLoadingButton(
                onClick = {
                    val candidate = ManagedExtension(
                        extensionId = extensionId.trim(),
                        scraperKey = scraperKey.trim().lowercase(),
                        name = name.trim(),
                        description = description.trim(),
                        baseUrl = baseUrl.trim(),
                        searchUrl = searchUrl.trim(),
                        runtimeApiVersion = runtimeApiVersion.toIntOrNull() ?: 1,
                        definitionVersion = definitionVersion.toIntOrNull() ?: 1,
                        minAppVersionCode = minAppVersionCode.toIntOrNull() ?: 1,
                        priority = priority.toIntOrNull() ?: 100,
                        status = selectedStatus.name,
                        capabilities = selectedCapabilities.toList(),
                        contentTypes = selectedContentTypes.toList(),
                        enabled = enabled,
                        createdAt = extension?.createdAt ?: 0L
                    )

                    val errors = com.example.validation.ManagedExtensionValidator.validate(candidate)
                    if (errors.isNotEmpty()) {
                        validationError = errors.first()
                        return@CineStreamLoadingButton
                    }
                    validationError = null
                    onSave(candidate, isNew)
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
