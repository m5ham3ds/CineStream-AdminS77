package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.ManagedExtension
import com.example.models.ManagedExtensionStatus
import com.example.models.SearchOrderCategory
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.CineStreamLoadingButton
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.validation.SearchOrderValidator
import com.example.viewmodels.SearchOrderViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOrderScreen(
    onBackClick: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    viewModel: SearchOrderViewModel = viewModel()
) {
    val currentConfig by viewModel.currentOrderConfig.collectAsState()
    val availableExtensions by viewModel.availableExtensionsMap.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentLang by AppSettings.language.collectAsState()

    var categoryFilter by remember { mutableStateOf<SearchOrderCategory?>(null) }
    var addingToCategory by remember { mutableStateOf<SearchOrderCategory?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground,
        floatingActionButton = {
            if (isDirty) {
                FloatingActionButton(
                    onClick = { viewModel.save() },
                    containerColor = MetricGreen,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.bounceClick(scaleDown = 0.92f) { viewModel.save() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = AppStrings.saveSearchOrder(currentLang))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppStrings.saveSearchOrder(currentLang), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        AdaptiveScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            val categoriesToShow = if (categoryFilter != null) listOf(categoryFilter!!) else SearchOrderCategory.values().toList()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp)
            ) {
                item(key = "header") {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.searchOrder(currentLang),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = AppStrings.searchOrderSubtitle(currentLang),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.RestartAlt,
                                    contentDescription = AppStrings.resetOrderDefaults(currentLang),
                                    tint = WarningOrange
                                )
                            }
                            if (isDirty) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.discardChanges() },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Undo,
                                        contentDescription = AppStrings.discardChanges(currentLang),
                                        tint = MetricRed
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "tabs") {
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
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onNavigateToExtensions() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Hub, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(AppStrings.tabExtensions(currentLang), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(CineStreamRed, RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sort, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(AppStrings.tabSearchOrder(currentLang), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item(key = "banner") {
                    // Informative Architecture Banner
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
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MetricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = AppStrings.searchOrderBanner(currentLang),
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                item(key = "category_chips") {
                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null },
                            label = { Text("ALL (3 SECTIONS)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineStreamRed,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            )
                        )

                        SearchOrderCategory.values().forEach { cat ->
                            val count = currentConfig.getOrderForCategory(cat).size
                            FilterChip(
                                selected = categoryFilter == cat,
                                onClick = { categoryFilter = cat },
                                label = {
                                    Text(
                                        "${if (currentLang == AppLanguage.ARABIC) cat.labelAr else cat.labelEn} ($count)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (cat) {
                                        SearchOrderCategory.MOVIE -> MetricBlue
                                        SearchOrderCategory.TV -> MetricPurple
                                        SearchOrderCategory.ANIME -> MetricGreen
                                    },
                                    selectedLabelColor = Color.White,
                                    containerColor = DarkSurface,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                categoriesToShow.forEach { cat ->
                    item(key = "section_${cat.name}") {
                        SearchOrderSectionContainer(
                            category = cat,
                            currentLang = currentLang,
                            orderedIds = currentConfig.getOrderForCategory(cat),
                            availableExtensions = availableExtensions,
                            onAddClick = { addingToCategory = cat },
                            onMoveUp = { index -> viewModel.moveUp(cat, index) },
                            onMoveDown = { index -> viewModel.moveDown(cat, index) },
                            onRemove = { index -> viewModel.remove(cat, index) }
                        )
                    }
                }
            }
        }
    }

    // Add Extension Selection Dialog
    addingToCategory?.let { cat ->
        val currentList = currentConfig.getOrderForCategory(cat)
        val eligibleCandidates = availableExtensions.values.filter { ext ->
            SearchOrderValidator.isExtensionEligibleForCategory(ext, cat) && !currentList.contains(ext.extensionId)
        }.sortedByDescending { it.priority }

        AlertDialog(
            onDismissRequest = { addingToCategory = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (cat) {
                            SearchOrderCategory.MOVIE -> Icons.Default.Movie
                            SearchOrderCategory.TV -> Icons.Default.Tv
                            SearchOrderCategory.ANIME -> Icons.Default.Animation
                        },
                        contentDescription = null,
                        tint = when (cat) {
                            SearchOrderCategory.MOVIE -> MetricBlue
                            SearchOrderCategory.TV -> MetricPurple
                            SearchOrderCategory.ANIME -> MetricGreen
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${AppStrings.addExtensionToOrder(currentLang)}: ${if (currentLang == AppLanguage.ARABIC) cat.labelAr else cat.labelEn}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                if (eligibleCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = AppStrings.allEligibleScrapersAdded(currentLang),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = AppStrings.selectScraperToAdd(currentLang),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        eligibleCandidates.forEach { ext ->
                            val isExtDisabled = !ext.isOperational
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                                    .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.add(cat, ext.extensionId)
                                        addingToCategory = null
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = ext.name.ifBlank { ext.extensionId },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
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
                                                    color = MetricPurple,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Priority: #${ext.priority} • ${ext.contentTypes.joinToString()}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )

                                        if (isExtDisabled) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = AppStrings.disabledAtRuntimeNotice(currentLang),
                                                color = WarningOrange,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Icon(
                                        Icons.Default.AddCircle,
                                        contentDescription = "Add",
                                        tint = MetricGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { addingToCategory = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Reset Defaults Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = AppStrings.resetOrderDefaults(currentLang),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (currentLang == AppLanguage.ARABIC)
                        "هل تريد استعادة ترتيب البحث الافتراضي لجميع الأقسام بناءً على الكواشط المسجلة وقدراتها؟ لن يتم الحفظ في Firebase حتى تنقر على حفظ."
                    else
                        "Reset search order for all categories based on registered scrapers and their priorities? Changes won't be saved to Firebase until you tap Save.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                CineStreamLoadingButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    },
                    containerColor = WarningOrange,
                    shape = RoundedCornerShape(10.dp),
                    text = AppStrings.confirm(currentLang)
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun SearchOrderSectionContainer(
    category: SearchOrderCategory,
    currentLang: AppLanguage,
    orderedIds: List<String>,
    availableExtensions: Map<String, ManagedExtension>,
    onAddClick: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    val categoryColor = when (category) {
        SearchOrderCategory.MOVIE -> MetricBlue
        SearchOrderCategory.TV -> MetricPurple
        SearchOrderCategory.ANIME -> MetricGreen
    }

    val categoryIcon = when (category) {
        SearchOrderCategory.MOVIE -> Icons.Default.Movie
        SearchOrderCategory.TV -> Icons.Default.Tv
        SearchOrderCategory.ANIME -> Icons.Default.Animation
    }

    val title = when (category) {
        SearchOrderCategory.MOVIE -> AppStrings.movieSearchOrder(currentLang)
        SearchOrderCategory.TV -> AppStrings.tvSearchOrder(currentLang)
        SearchOrderCategory.ANIME -> AppStrings.animeSearchOrder(currentLang)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(categoryColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(categoryIcon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${orderedIds.size} ${if (currentLang == AppLanguage.ARABIC) "كواشط مرتبة" else "configured scrapers"}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Add button for this section
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppStrings.add(currentLang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = DarkCardBorder)

            if (orderedIds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlaylistRemove, contentDescription = null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(AppStrings.noExtensionsInOrder(currentLang), color = TextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    orderedIds.forEachIndexed { index, id ->
                        val ext = availableExtensions[id]
                        SearchOrderItemRow(
                            rank = index + 1,
                            totalCount = orderedIds.size,
                            extensionId = id,
                            extension = ext,
                            categoryColor = categoryColor,
                            currentLang = currentLang,
                            onMoveUp = { onMoveUp(index) },
                            onMoveDown = { onMoveDown(index) },
                            onRemove = { onRemove(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchOrderItemRow(
    rank: Int,
    totalCount: Int,
    extensionId: String,
    extension: ManagedExtension?,
    categoryColor: Color,
    currentLang: AppLanguage,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val isTop3 = rank <= 3
    val rankBadgeBg = if (isTop3) categoryColor else DarkSurfaceVariant
    val rankBadgeText = if (isTop3) Color.White else TextSecondary

    val isOperational = extension?.isOperational ?: false
    val isRegistered = extension != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131720), RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (!isRegistered) MetricRed.copy(alpha = 0.6f) else if (!isOperational) WarningOrange.copy(alpha = 0.4f) else DarkCardBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number Circle + Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(rankBadgeBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        color = rankBadgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = extension?.name?.ifBlank { extensionId } ?: extensionId,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E2330), RoundedCornerShape(6.dp))
                                .border(0.5.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = extension?.scraperKey ?: extensionId,
                                color = MetricPurple,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Status and warnings
                    if (!isRegistered) {
                        Text(
                            text = "UNKNOWN (Not in registry)",
                            color = MetricRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (!isOperational) {
                        val status = extension?.statusEnum ?: ManagedExtensionStatus.DISABLED
                        Text(
                            text = "${status.name} • ${AppStrings.disabledAtRuntimeNotice(currentLang)}",
                            color = WarningOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Priority: #${extension?.priority ?: 100} • ${extension?.baseUrl ?: ""}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Reorder Controls: Move Up, Move Down, Remove
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Move Up
                IconButton(
                    onClick = onMoveUp,
                    enabled = rank > 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = if (rank > 1) Color.White else TextSecondary.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Move Down
                IconButton(
                    onClick = onMoveDown,
                    enabled = rank < totalCount,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = if (rank < totalCount) Color.White else TextSecondary.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Remove from order
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MetricRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
