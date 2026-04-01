package com.looktube.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
private const val LOOKTUBE_LAUNCH_INTRO_DURATION_MS = 3_600L
private const val LOOKTUBE_LAUNCH_INTRO_ENTER_DURATION_MS = 550
private const val LOOKTUBE_LAUNCH_INTRO_EXIT_DURATION_MS = 320

@Composable
internal fun LookTubeLaunchIntroOverlay(
    quote: LaunchIntroQuote,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    shouldAutoDismiss: Boolean = true,
) {
    var animateIn by remember { mutableStateOf(false) }
    var animateOut by remember { mutableStateOf(false) }
    var dismissHandled by remember { mutableStateOf(false) }
    val brandScale by animateFloatAsState(
        targetValue = when {
            animateOut -> 0.98f
            animateIn -> 1f
            else -> 0.88f
        },
        animationSpec = tween(
            durationMillis = if (animateOut) {
                LOOKTUBE_LAUNCH_INTRO_EXIT_DURATION_MS
            } else {
                LOOKTUBE_LAUNCH_INTRO_ENTER_DURATION_MS
            },
            easing = FastOutSlowInEasing,
        ),
        label = "launchIntroScale",
    )
    val brandAlpha by animateFloatAsState(
        targetValue = when {
            animateOut -> 0f
            animateIn -> 1f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (animateOut) LOOKTUBE_LAUNCH_INTRO_EXIT_DURATION_MS else 400,
            easing = FastOutSlowInEasing,
        ),
        label = "launchIntroAlpha",
    )
    val brandOffsetY by animateDpAsState(
        targetValue = when {
            animateOut -> (-10).dp
            animateIn -> 0.dp
            else -> 20.dp
        },
        animationSpec = tween(
            durationMillis = if (animateOut) {
                LOOKTUBE_LAUNCH_INTRO_EXIT_DURATION_MS
            } else {
                LOOKTUBE_LAUNCH_INTRO_ENTER_DURATION_MS
            },
            easing = FastOutSlowInEasing,
        ),
        label = "launchIntroOffset",
    )

    LaunchedEffect(Unit) {
        animateIn = true
    }
    LaunchedEffect(shouldAutoDismiss) {
        if (shouldAutoDismiss) {
            delay(LOOKTUBE_LAUNCH_INTRO_DURATION_MS)
            if (!dismissHandled) {
                animateOut = true
                delay(LOOKTUBE_LAUNCH_INTRO_EXIT_DURATION_MS.toLong())
                if (!dismissHandled) {
                    dismissHandled = true
                    onDismiss()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    ),
                ),
            )
            .pointerInput(onDismiss) {
                detectTapGestures(
                    onTap = {
                        if (!dismissHandled) {
                            dismissHandled = true
                            onDismiss()
                        }
                    },
                )
            }
            .padding(24.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = brandOffsetY),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 10.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LookTubeBrandMark(
                    modifier = Modifier.size(104.dp * brandScale),
                    alpha = brandAlpha,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "LookTube",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = brandAlpha),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f * brandAlpha),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "“${quote.text}”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = brandAlpha),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "— ${quote.speaker}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = brandAlpha),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = quote.sourceTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = brandAlpha),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Text(
            text = "Tap anywhere to skip",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f * brandAlpha),
        )
    }
}

@Composable
private fun LookTubeBrandMark(
    modifier: Modifier = Modifier,
    alpha: Float,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = null,
            modifier = Modifier.size(58.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha),
        )
    }
}
