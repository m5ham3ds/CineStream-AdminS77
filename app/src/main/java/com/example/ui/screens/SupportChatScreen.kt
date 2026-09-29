package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.models.SupportConversationStatus
import com.example.models.SupportMessage
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.components.AdaptiveScreenContainer
import com.example.ui.components.bounceClick
import com.example.ui.theme.*
import com.example.viewmodels.SupportViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SupportChatScreen(
    conversationId: String,
    onBackClick: () -> Unit = {},
    viewModel: SupportViewModel = viewModel()
) {
    val currentLang by AppSettings.language.collectAsState()
    val conversation by viewModel.selectedConversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var replyText by remember { mutableStateOf("") }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(conversationId) {
        viewModel.selectConversation(conversationId)
    }

    DisposableEffect(conversationId) {
        onDispose {
            viewModel.clearSelectedConversation()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val currentStatus = conversation?.statusEnum ?: SupportConversationStatus.OPEN
    val statusColor = when (currentStatus) {
        SupportConversationStatus.OPEN -> MetricGreen
        SupportConversationStatus.PENDING -> WarningOrange
        SupportConversationStatus.RESOLVED -> MetricPurple
        SupportConversationStatus.CLOSED -> TextSecondary
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            text = (conversation?.userName?.ifBlank { conversation?.userEmail } ?: "U").take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conversation?.userEmail?.ifBlank { conversation?.userName } ?: "Support Thread",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (conversation?.subject?.isNotBlank() == true) {
                            Text(
                                text = conversation?.subject.orEmpty(),
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Dropdown Menu
                    Box {
                        Surface(
                            color = statusColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(listOf(statusColor.copy(alpha = 0.5f), statusColor.copy(alpha = 0.5f)))
                            ),
                            modifier = Modifier.bounceClick(scaleDown = 0.9f) {
                                statusMenuExpanded = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentStatus.name,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = statusMenuExpanded,
                            onDismissRequest = { statusMenuExpanded = false },
                            modifier = Modifier.background(DarkSurfaceVariant)
                        ) {
                            SupportConversationStatus.values().forEach { st ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = st.name,
                                            color = if (st == currentStatus) CineStreamRed else Color.White,
                                            fontWeight = if (st == currentStatus) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        statusMenuExpanded = false
                                        viewModel.updateStatus(conversationId, st)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Reply Composer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(1.dp, DarkCardBorder)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = AppStrings.typeReplyHint(currentLang),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (replyText.isNotBlank() && !isSending) {
                                viewModel.sendReply(conversationId, replyText) {
                                    replyText = ""
                                }
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedBorderColor = CineStreamRed,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank() && !isSending) {
                                viewModel.sendReply(conversationId, replyText) {
                                    replyText = ""
                                }
                            }
                        },
                        enabled = replyText.isNotBlank() && !isSending,
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (replyText.isNotBlank() && !isSending) CineStreamRed else DarkSurfaceVariant,
                                CircleShape
                            )
                            .border(1.dp, DarkCardBorder, CircleShape)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = AppStrings.sendReplyButton(currentLang),
                                tint = if (replyText.isNotBlank()) Color.White else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        AdaptiveScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = AppStrings.noMessagesYet(currentLang),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.messageId }) { msg ->
                        SupportMessageBubble(
                            message = msg,
                            currentLang = currentLang
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportMessageBubble(
    message: SupportMessage,
    currentLang: AppLanguage
) {
    val isAdmin = message.isAdminMessage
    val formattedTime = remember(message.timestamp) {
        if (message.timestamp <= 0L) ""
        else {
            val df = SimpleDateFormat("HH:mm", Locale.getDefault())
            df.format(Date(message.timestamp))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isAdmin) Alignment.End else Alignment.Start
    ) {
        // Role & Author Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isAdmin) AppStrings.supportAdminBadge(currentLang) else (message.senderEmail.ifBlank { AppStrings.supportUserBadge(currentLang) }),
                color = if (isAdmin) CineStreamRed else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (formattedTime.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formattedTime,
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
        }

        // Message Bubble Card
        Card(
            modifier = Modifier.widthIn(max = 310.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAdmin) Color(0xFF2A1519) else DarkSurface
            ),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isAdmin) 14.dp else 2.dp,
                bottomEnd = if (isAdmin) 2.dp else 14.dp
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(
                        if (isAdmin) CineStreamRed.copy(alpha = 0.4f) else DarkCardBorder,
                        DarkCardBorder
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
