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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.ProRequest
import com.example.models.ProRequestStatus
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.CineStreamRefreshButton
import com.example.ui.theme.*
import com.example.viewmodels.ProRequestsViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProRequestsScreen(
    onBackClick: () -> Unit = {},
    viewModel: ProRequestsViewModel = viewModel()
) {
    val requests by viewModel.filteredRequests.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val approvedCount by viewModel.approvedCount.collectAsState()
    val rejectedCount by viewModel.rejectedCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isPerformingAction by viewModel.isPerformingAction.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentLang by AppSettings.language.collectAsState()

    var requestToApprove by remember { mutableStateOf<ProRequest?>(null) }
    var requestToReject by remember { mutableStateOf<ProRequest?>(null) }
    var detailRequest by remember { mutableStateOf<ProRequest?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    var adminNote by remember { mutableStateOf("") }

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.proRequests(currentLang),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = AppStrings.proRequestsSubtitle(currentLang),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPerformingAction) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .testTag("pro_requests_action_indicator"),
                            color = CineStreamRed,
                            strokeWidth = 2.dp
                        )
                    }

                    CineStreamRefreshButton(
                        isRefreshing = isRefreshing,
                        onClick = { viewModel.refresh() },
                        contentDescription = AppStrings.refresh(currentLang)
                    )
                }
            }

                Spacer(modifier = Modifier.height(14.dp))

                // Metric Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProMetricPill(
                        label = AppStrings.metricTotal(currentLang),
                        value = totalCount.toString(),
                        color = MetricBlue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_pill_total")
                    )
                    ProMetricPill(
                        label = AppStrings.metricPending(currentLang),
                        value = pendingCount.toString(),
                        color = WarningOrange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_pill_pending")
                    )
                    ProMetricPill(
                        label = AppStrings.metricApproved(currentLang),
                        value = approvedCount.toString(),
                        color = MetricGreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_pill_approved")
                    )
                    ProMetricPill(
                        label = AppStrings.metricRejected(currentLang),
                        value = rejectedCount.toString(),
                        color = MetricRed,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_pill_rejected")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pro_requests_search_input"),
                    placeholder = {
                        Text(
                            text = AppStrings.proSearchPlaceholder(currentLang),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = AppStrings.search(currentLang),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onSearchQueryChanged("") },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = AppStrings.reset(currentLang),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
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
                        label = { Text(AppStrings.proFilterAll(totalCount, currentLang), fontSize = 11.sp) },
                        modifier = Modifier.testTag("filter_chip_all"),
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

                    ProRequestStatus.values().forEach { st ->
                        val count = when (st) {
                            ProRequestStatus.PENDING -> pendingCount
                            ProRequestStatus.APPROVED -> approvedCount
                            ProRequestStatus.REJECTED -> rejectedCount
                        }
                        val statusLabel = when (st) {
                            ProRequestStatus.PENDING -> AppStrings.proRequestStatusPending(currentLang)
                            ProRequestStatus.APPROVED -> AppStrings.proRequestStatusApproved(currentLang)
                            ProRequestStatus.REJECTED -> AppStrings.proRequestStatusRejected(currentLang)
                        }
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { viewModel.setStatusFilter(if (statusFilter == st) null else st) },
                            label = { Text("$statusLabel ($count)", fontSize = 11.sp) },
                            modifier = Modifier.testTag("filter_chip_${st.name.lowercase()}"),
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

                // Request List
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pro_requests_loading"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CineStreamRed)
                    }
                } else if (requests.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("pro_requests_empty_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CardMembership,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = AppStrings.noProRequests(currentLang),
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pro_requests_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(requests, key = { it.requestId }) { req ->
                            ProRequestCardItem(
                                request = req,
                                currentLang = currentLang,
                                isActionInProgress = isPerformingAction,
                                onClick = { detailRequest = req },
                                onApprove = { requestToApprove = req },
                                onReject = {
                                    rejectionReason = ""
                                    adminNote = ""
                                    requestToReject = req
                                }
                            )
                        }
                    }
                }
            }
        }

    // Approve Confirmation Dialog
    requestToApprove?.let { req ->
        AlertDialog(
            onDismissRequest = { if (!isPerformingAction) requestToApprove = null },
            title = {
                Text(
                    text = AppStrings.confirmApprovalTitle(currentLang),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = AppStrings.confirmApprovalMsg(
                            req.userEmail.ifBlank { req.userId },
                            req.requestedPlan.uppercase(),
                            currentLang
                        ),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = adminNote,
                        onValueChange = { adminNote = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("approve_admin_note_input"),
                        placeholder = {
                            Text(
                                text = AppStrings.adminNoteLabel(currentLang),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedBorderColor = CineStreamRed,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveRequest(req.requestId, adminNote.ifBlank { null }) {
                            requestToApprove = null
                        }
                    },
                    enabled = !isPerformingAction,
                    modifier = Modifier.testTag("confirm_approve_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MetricGreen)
                ) {
                    Text(
                        text = AppStrings.approve(currentLang),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { requestToApprove = null },
                    enabled = !isPerformingAction,
                    modifier = Modifier.testTag("cancel_approve_button")
                ) {
                    Text(text = AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Reject Dialog
    requestToReject?.let { req ->
        AlertDialog(
            onDismissRequest = { if (!isPerformingAction) requestToReject = null },
            title = {
                Text(
                    text = AppStrings.reject(currentLang),
                    fontWeight = FontWeight.Bold,
                    color = MetricRed
                )
            },
            text = {
                Column {
                    Text(
                        text = "${AppStrings.labelUser(currentLang)}: ${req.userEmail.ifBlank { req.userId }} (${req.requestedPlan.uppercase()})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reject_reason_input"),
                        placeholder = {
                            Text(
                                text = AppStrings.rejectionReasonPrompt(currentLang),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedBorderColor = MetricRed,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = adminNote,
                        onValueChange = { adminNote = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reject_admin_note_input"),
                        placeholder = {
                            Text(
                                text = AppStrings.adminNoteLabel(currentLang),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedBorderColor = MetricRed,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectionReason.isNotBlank()) {
                            viewModel.rejectRequest(req.requestId, rejectionReason, adminNote.ifBlank { null }) {
                                requestToReject = null
                            }
                        }
                    },
                    enabled = !isPerformingAction && rejectionReason.isNotBlank(),
                    modifier = Modifier.testTag("confirm_reject_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MetricRed)
                ) {
                    Text(
                        text = AppStrings.reject(currentLang),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { requestToReject = null },
                    enabled = !isPerformingAction,
                    modifier = Modifier.testTag("cancel_reject_button")
                ) {
                    Text(text = AppStrings.cancel(currentLang), color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Detail Dialog (Distinct User Data, Request Data, Status, Review Information)
    detailRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { detailRequest = null },
            title = {
                Text(
                    text = AppStrings.requestDetails(currentLang),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // USER DATA
                    DetailSectionHeader(AppStrings.sectionUserInfo(currentLang))
                    DetailRow(AppStrings.labelUser(currentLang), req.userName.ifBlank { req.userEmail })
                    DetailRow(AppStrings.labelEmail(currentLang), req.userEmail)
                    DetailRow(AppStrings.labelUid(currentLang), req.userId)

                    HorizontalDivider(color = DarkCardBorder, modifier = Modifier.padding(vertical = 4.dp))

                    // REQUEST DATA
                    DetailSectionHeader(AppStrings.sectionRequestInfo(currentLang))
                    DetailRow(AppStrings.requestedPlan(currentLang), req.requestedPlan.uppercase())
                    DetailRow(AppStrings.requestedDuration(currentLang), req.requestedDuration)
                    if (!req.paymentMethod.isNullOrBlank()) {
                        DetailRow(AppStrings.paymentMethod(currentLang), req.paymentMethod)
                    }
                    if (!req.paymentProofUrl.isNullOrBlank()) {
                        DetailRow(AppStrings.paymentProof(currentLang), req.paymentProofUrl)
                    }
                    if (req.userNote.isNotBlank()) {
                        DetailRow(AppStrings.userNote(currentLang), req.userNote)
                    }

                    HorizontalDivider(color = DarkCardBorder, modifier = Modifier.padding(vertical = 4.dp))

                    // STATUS & REVIEW INFORMATION
                    DetailSectionHeader(AppStrings.sectionReviewInfo(currentLang))
                    val statusText = when (req.statusEnum) {
                        ProRequestStatus.PENDING -> AppStrings.proRequestStatusPending(currentLang)
                        ProRequestStatus.APPROVED -> AppStrings.proRequestStatusApproved(currentLang)
                        ProRequestStatus.REJECTED -> AppStrings.proRequestStatusRejected(currentLang)
                    }
                    DetailRow(AppStrings.labelStatus(currentLang), statusText)

                    if (req.reviewedByEmail != null) {
                        DetailRow(AppStrings.labelReviewedBy(currentLang), req.reviewedByEmail)
                    }
                    if (req.reviewedAt != null && req.reviewedAt > 0L) {
                        val df = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                        DetailRow(AppStrings.labelReviewedAt(currentLang), df.format(Date(req.reviewedAt)))
                    }
                    if (!req.rejectionReason.isNullOrBlank()) {
                        DetailRow(AppStrings.rejectionReasonLabel(currentLang), req.rejectionReason)
                    }
                    if (!req.adminNote.isNullOrBlank()) {
                        DetailRow(AppStrings.adminNoteLabel(currentLang), req.adminNote)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { detailRequest = null },
                    modifier = Modifier.testTag("close_detail_button")
                ) {
                    Text(text = AppStrings.close(currentLang), color = Color.White)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun DetailSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CineStreamRed,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun ProRequestCardItem(
    request: ProRequest,
    currentLang: AppLanguage,
    isActionInProgress: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val statusColor = when (request.statusEnum) {
        ProRequestStatus.PENDING -> WarningOrange
        ProRequestStatus.APPROVED -> MetricGreen
        ProRequestStatus.REJECTED -> MetricRed
    }

    val formattedDate = remember(request.createdAt) {
        if (request.createdAt <= 0L) ""
        else {
            val df = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            df.format(Date(request.createdAt))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("pro_request_card_${request.requestId}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (request.isPending) WarningOrange.copy(alpha = 0.5f) else DarkCardBorder,
                    DarkCardBorder
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Avatar, User, Plan Badge, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (request.userName.ifBlank { request.userEmail }).take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.userEmail.ifBlank { request.userName.ifBlank { "${AppStrings.labelUser(currentLang)} ${request.userId.take(6)}" } },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${request.requestedPlan.uppercase()} • ${request.requestedDuration}",
                        color = WarningOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(statusColor.copy(alpha = 0.5f), statusColor.copy(alpha = 0.5f)))
                    )
                ) {
                    val statusBadgeText = when (request.statusEnum) {
                        ProRequestStatus.PENDING -> AppStrings.proRequestStatusPending(currentLang)
                        ProRequestStatus.APPROVED -> AppStrings.proRequestStatusApproved(currentLang)
                        ProRequestStatus.REJECTED -> AppStrings.proRequestStatusRejected(currentLang)
                    }
                    Text(
                        text = statusBadgeText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User Note snippet or Payment Info
            if (request.userNote.isNotBlank()) {
                Text(
                    text = "\"${request.userNote}\"",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Footer Row: Date & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (formattedDate.isNotBlank()) {
                    Text(text = formattedDate, color = TextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                if (request.isPending) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onReject,
                            enabled = !isActionInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = MetricRed.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("card_reject_button_${request.requestId}")
                        ) {
                            Text(
                                text = AppStrings.reject(currentLang),
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = onApprove,
                            enabled = !isActionInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = MetricGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("card_approve_button_${request.requestId}")
                        ) {
                            Text(
                                text = AppStrings.approve(currentLang),
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (request.isApproved) {
                    Text(
                        text = AppStrings.statusApprovedText(currentLang),
                        color = MetricGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (request.isRejected) {
                    Text(
                        text = AppStrings.statusRejectedText(request.rejectionReason.orEmpty(), currentLang),
                        color = MetricRed,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder))
        )
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
