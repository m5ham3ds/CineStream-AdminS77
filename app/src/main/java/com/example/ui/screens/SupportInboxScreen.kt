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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.SupportConversation
import com.example.models.SupportConversationStatus
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.CineStreamRefreshButton
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.viewmodels.SupportViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportInboxScreen(
    onBackClick: () -> Unit = {},
    onConversationClick: (String) -> Unit = {},
    viewModel: SupportViewModel = viewModel()
) {
    val conversations by viewModel.filteredConversations.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val openCount by viewModel.openCount.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val resolvedCount by viewModel.resolvedCount.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentLang by AppSettings.language.collectAsState()

    var conversationToDelete by remember { mutableStateOf<SupportConversation?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    AdaptiveScreenContainer(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.supportInbox(currentLang),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = AppStrings.supportInboxSubtitle(currentLang),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                CineStreamRefreshButton(
                    isRefreshing = isRefreshing,
                    onClick = { viewModel.refresh() },
                    contentDescription = AppStrings.refresh(currentLang)
                )
            }

                Spacer(modifier = Modifier.height(14.dp))

                // Metric Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SupportMetricPill(
                        label = "Total",
                        value = totalCount.toString(),
                        color = MetricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    SupportMetricPill(
                        label = "Open",
                        value = openCount.toString(),
                        color = MetricGreen,
                        modifier = Modifier.weight(1f)
                    )
                    SupportMetricPill(
                        label = "Pending",
                        value = pendingCount.toString(),
                        color = WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                    SupportMetricPill(
                        label = "Unread",
                        value = unreadCount.toString(),
                        color = CineStreamRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (currentLang == AppLanguage.ARABIC) "بحث بالبريد، الموضوع، أو نص الرسالة..." else "Search by user, subject, or message...",
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

                // Status Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("ALL (${totalCount})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CineStreamRed,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = statusFilter == null,
                            borderColor = DarkCardBorder,
                            selectedBorderColor = CineStreamRed
                        )
                    )

                    SupportConversationStatus.values().forEach { st ->
                        val count = when (st) {
                            SupportConversationStatus.OPEN -> openCount
                            SupportConversationStatus.PENDING -> pendingCount
                            SupportConversationStatus.RESOLVED -> resolvedCount
                            SupportConversationStatus.CLOSED -> totalCount - openCount - pendingCount - resolvedCount
                        }
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { viewModel.setStatusFilter(if (statusFilter == st) null else st) },
                            label = { Text("${st.name} ($count)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineStreamRed,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = statusFilter == st,
                                borderColor = DarkCardBorder,
                                selectedBorderColor = CineStreamRed
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Conversations List
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CineStreamRed)
                    }
                } else if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = AppStrings.noSupportConversations(currentLang),
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
                        items(conversations, key = { it.conversationId }) { conv ->
                            ConversationCardItem(
                                conversation = conv,
                                currentLang = currentLang,
                                onClick = { onConversationClick(conv.conversationId) },
                                onStatusChange = { newStatus -> viewModel.updateStatus(conv.conversationId, newStatus) },
                                onDelete = { conversationToDelete = conv }
                            )
                        }
                    }
                }
            }
        }

    // Delete Confirmation Dialog
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text(AppStrings.deleteConversationConfirm(currentLang), fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text(AppStrings.deleteConversationPrompt(currentLang), color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConversation(conv.conversationId)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetricRed)
                ) {
                    Text(AppStrings.confirm(currentLang), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text(AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ConversationCardItem(
    conversation: SupportConversation,
    currentLang: AppLanguage,
    onClick: () -> Unit,
    onStatusChange: (SupportConversationStatus) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val statusColor = when (conversation.statusEnum) {
        SupportConversationStatus.OPEN -> MetricGreen
        SupportConversationStatus.PENDING -> WarningOrange
        SupportConversationStatus.RESOLVED -> MetricPurple
        SupportConversationStatus.CLOSED -> TextSecondary
    }

    val formattedTime = remember(conversation.lastMessageAt) {
        if (conversation.lastMessageAt <= 0L) ""
        else {
            val df = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            df.format(Date(conversation.lastMessageAt))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (conversation.unreadByAdmin) CineStreamRed.copy(alpha = 0.8f) else DarkCardBorder,
                    DarkCardBorder
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: User Avatar, Email/Name, Status Badge, Unread Dot, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (conversation.userName.ifBlank { conversation.userEmail }).take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation.userName.ifBlank { conversation.userEmail.ifBlank { "User ${conversation.userId.take(6)}" } },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (conversation.userEmail.isNotBlank() && conversation.userName.isNotBlank()) {
                        Text(
                            text = conversation.userEmail,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Badge
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(statusColor.copy(alpha = 0.5f), statusColor.copy(alpha = 0.5f))))
                ) {
                    Text(
                        text = conversation.statusEnum.name,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                if (conversation.unreadByAdmin) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(CineStreamRed)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(DarkSurfaceVariant)
                    ) {
                        SupportConversationStatus.values().forEach { st ->
                            if (st != conversation.statusEnum) {
                                DropdownMenuItem(
                                    text = { Text("Set ${st.name}", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        menuExpanded = false
                                        onStatusChange(st)
                                    }
                                )
                            }
                        }
                        HorizontalDivider(color = DarkCardBorder)
                        DropdownMenuItem(
                            text = { Text("Delete Thread", color = MetricRed, fontSize = 12.sp) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subject
            if (conversation.subject.isNotBlank()) {
                Text(
                    text = conversation.subject,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Last message preview & timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (conversation.lastMessage.isBlank()) "No messages yet" else conversation.lastMessage,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (formattedTime.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 10.sp, color = TextSecondary)
        }
    }
}
