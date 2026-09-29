package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.state.AppLanguage
import com.example.state.AppSettings
import com.example.state.AppStrings
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.PrimaryBlue

/**
 * CompositionLocal providing current WindowSizeClass across the application
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass?> { null }

/**
 * Universal tactile bounce modifier that provides spring scale-down on press,
 * haptic click feedback, and bounded Material ripple.
 */
@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    val base = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)

    return if (onClick != null) {
        base.clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            enabled = enabled,
            onClick = {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {}
                onClick()
            }
        )
    } else {
        base
    }
}

/**
 * High-performance interactive Action Button with:
 * 1. Tactile spring scale bounce on press
 * 2. Material ripple indication
 * 3. Smooth animated transition from Text/Icon into a CircularProgressIndicator
 * 4. Automatic duplicate-click prevention during loading
 */
@Composable
fun CineStreamLoadingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = PrimaryBlue,
    contentColor: Color = Color.White,
    loadingIndicatorColor: Color = Color.White,
    border: BorderStroke? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
    leadingIcon: ImageVector? = null,
    loadingText: String? = null,
    text: String
) {
    val haptic = LocalHapticFeedback.current
    val currentLang by AppSettings.language.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonBounceScale"
    )

    Button(
        onClick = {
            if (!isLoading && enabled) {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {}
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.55f),
            disabledContentColor = contentColor.copy(alpha = 0.75f)
        ),
        border = border,
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)))
                    .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)))
            },
            label = "buttonLoadingTransition"
        ) { loadingState ->
            if (loadingState) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = loadingIndicatorColor,
                        strokeWidth = 2.4.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = loadingText ?: AppStrings.processing(currentLang),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = loadingIndicatorColor
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Outlined variant of CineStreamLoadingButton with tactile bounce and loading transition
 */
@Composable
fun CineStreamOutlinedLoadingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color = Color.White,
    borderColor: Color = DarkCardBorder,
    loadingIndicatorColor: Color = contentColor,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    leadingIcon: ImageVector? = null,
    loadingText: String? = null,
    text: String
) {
    val haptic = LocalHapticFeedback.current
    val currentLang by AppSettings.language.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "outlinedButtonBounceScale"
    )

    OutlinedButton(
        onClick = {
            if (!isLoading && enabled) {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {}
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.4f)),
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f))
                    .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.85f))
            },
            label = "outlinedButtonLoadingTransition"
        ) { loadingState ->
            if (loadingState) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = loadingIndicatorColor,
                        strokeWidth = 2.2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = loadingText ?: AppStrings.processing(currentLang),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = loadingIndicatorColor
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Adaptive layout screen wrapper that calculates the screen size class
 * and introduces comfortable safe paddings, centering wide content on tablets and foldables.
 * On compact mobile devices, defaults to zero extra container margin to prevent double-padding.
 */
@Composable
fun AdaptiveScreenContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 860.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val horizontalPadding = when (windowSizeClass?.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 32.dp
        WindowWidthSizeClass.Medium -> 16.dp
        else -> 0.dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Real-time Network Status Banner.
 * Displays subtle, non-intrusive status when network is offline, weak, or syncing pending writes.
 */
@Composable
fun NetworkStatusBar(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val networkMonitor = remember { com.example.network.NetworkMonitor.getInstance(context) }
    val syncManager = remember { com.example.network.ResilientSyncManager.getInstance(context) }
    val networkStatus by networkMonitor.status.collectAsState()
    val pendingOps by syncManager.pendingOperations.collectAsState()
    val currentLang by AppSettings.language.collectAsState()

    var showRestoredBanner by remember { mutableStateOf(false) }
    var previousStatus by remember { mutableStateOf(networkStatus) }

    LaunchedEffect(networkStatus) {
        if (previousStatus != com.example.network.NetworkStatus.ONLINE && networkStatus == com.example.network.NetworkStatus.ONLINE) {
            showRestoredBanner = true
            kotlinx.coroutines.delay(3000L)
            showRestoredBanner = false
        }
        previousStatus = networkStatus
    }

    val visible = networkStatus != com.example.network.NetworkStatus.ONLINE || showRestoredBanner || pendingOps.isNotEmpty()

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
        exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)),
        modifier = modifier.fillMaxWidth()
    ) {
        val bannerData = when {
            pendingOps.isNotEmpty() && networkStatus == com.example.network.NetworkStatus.ONLINE -> NetworkBannerData(
                bgColor = com.example.ui.theme.MetricBlue,
                icon = androidx.compose.material.icons.Icons.Default.Refresh,
                message = if (currentLang == AppLanguage.ARABIC) "جاري مزامنة ${pendingOps.size} طلبات في الخلفية مع الخادم..." else "Synchronizing ${pendingOps.size} operations with cloud server..."
            )
            showRestoredBanner -> NetworkBannerData(
                bgColor = com.example.ui.theme.MetricGreen,
                icon = androidx.compose.material.icons.Icons.Default.CheckCircle,
                message = AppStrings.connectionRestoredBanner(currentLang)
            )
            networkStatus == com.example.network.NetworkStatus.OFFLINE -> NetworkBannerData(
                bgColor = com.example.ui.theme.CineStreamRed,
                icon = androidx.compose.material.icons.Icons.Default.Warning,
                message = if (pendingOps.isNotEmpty()) {
                    if (currentLang == AppLanguage.ARABIC) "لا يوجد اتصال بالإنترنت (${pendingOps.size} طلبات محفوظة في وضع الانتظار)" else "Offline mode (${pendingOps.size} operations queued safely)"
                } else AppStrings.offlineBanner(currentLang)
            )
            else -> NetworkBannerData(
                bgColor = com.example.ui.theme.WarningOrange,
                icon = androidx.compose.material.icons.Icons.Default.Info,
                message = if (pendingOps.isNotEmpty()) {
                    if (currentLang == AppLanguage.ARABIC) "الاتصال ضعيف (${pendingOps.size} طلبات قيد الانتظار للاستقرار)" else "Weak connection (${pendingOps.size} operations queued for stable network)"
                } else AppStrings.weakConnectionBanner(currentLang)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerData.bgColor.copy(alpha = 0.95f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = bannerData.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = bannerData.message,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
            }
        }
    }
}

private data class NetworkBannerData(
    val bgColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val message: String
)

/**
 * Polished CineStream Refresh Action Button with continuous rotation animation during active refresh.
 */
@Composable
fun CineStreamRefreshButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Refresh",
    size: Dp = 40.dp
) {
    val rotation by rememberInfiniteTransition(label = "cs_refresh_rot").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cs_refresh_rot_float"
    )

    Box(
        modifier = modifier
            .size(size)
            .background(com.example.ui.theme.DarkSurfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
            .bounceClick(scaleDown = 0.88f, shape = RoundedCornerShape(10.dp)) {
                if (!isRefreshing) onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = contentDescription,
            tint = if (isRefreshing) com.example.ui.theme.CineStreamRed else Color.White,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer(rotationZ = if (isRefreshing) rotation else 0f)
        )
    }
}

