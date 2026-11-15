package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalJarvisHudColors
import com.example.util.VoiceState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HolographicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hud = LocalJarvisHudColors.current
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radial = Brush.radialGradient(
                colors = listOf(
                    hud.arcCyan.copy(alpha = if (hud.isDark) 0.14f else 0.08f),
                    hud.glowBlue.copy(alpha = if (hud.isDark) 0.06f else 0.03f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.18f),
                radius = size.maxDimension * 0.72f
            )
            drawRect(brush = radial)

            val step = 44.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = hud.gridLine,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = hud.gridLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }
        content()
    }
}

@Composable
fun HudPanelCard(
    modifier: Modifier = Modifier,
    borderColor: Color = LocalJarvisHudColors.current.panelBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    val hud = LocalJarvisHudColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = hud.panelGlass,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun JarvisArcCore(
    voiceState: VoiceState,
    isThinking: Boolean,
    isApiConnected: Boolean,
    size: Dp = 196.dp,
    onCoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current
    val transition = rememberInfiniteTransition(label = "jarvis_core")

    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isThinking -> 2200
                    voiceState == VoiceState.LISTENING -> 3200
                    else -> 12000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_angle"
    )

    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isThinking -> 1600
                    voiceState == VoiceState.SPEAKING -> 2400
                    else -> 8000
                },
                easing = LinearEasing
            )
        ),
        label = "inner_angle"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState != VoiceState.IDLE || isThinking) 650 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val coreColor = when {
        voiceState == VoiceState.LISTENING -> hud.onlineGreen
        voiceState == VoiceState.WAKE_LISTENING -> hud.warnAmber
        isThinking -> hud.glowBlue
        isApiConnected -> hud.arcCyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val coreStatusText = when {
        isThinking -> "PROCESSING"
        voiceState == VoiceState.LISTENING -> "LISTENING"
        voiceState == VoiceState.WAKE_LISTENING -> "WAKE ARMED"
        voiceState == VoiceState.SPEAKING -> "SPEAKING"
        isApiConnected -> "CORE ONLINE"
        else -> "STANDBY"
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = "Activate JARVIS Voice Core",
                onClick = onCoreClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f

            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.34f * pulseScale),
                        coreColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = c,
                    radius = maxR
                ),
                radius = maxR,
                center = c
            )

            // Outer tick ring
            val tickRadius = maxR * 0.90f
            for (i in 0 until 36) {
                val deg = i * 10f + outerAngle * 0.2f
                val rad = deg * (PI / 180.0)
                val innerTick = if (i % 3 == 0) tickRadius - 10.dp.toPx() else tickRadius - 5.dp.toPx()
                val start = Offset(
                    c.x + (innerTick * cos(rad)).toFloat(),
                    c.y + (innerTick * sin(rad)).toFloat()
                )
                val end = Offset(
                    c.x + (tickRadius * cos(rad)).toFloat(),
                    c.y + (tickRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = coreColor.copy(alpha = if (i % 3 == 0) 0.75f else 0.3f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // Segmented outer orbital arcs
            rotate(degrees = outerAngle, pivot = c) {
                val r1 = maxR * 0.77f
                val arcSize1 = Size(r1 * 2f, r1 * 2f)
                val topLeft1 = Offset(c.x - r1, c.y - r1)
                for (startDeg in listOf(0f, 120f, 240f)) {
                    drawArc(
                        color = coreColor,
                        startAngle = startDeg,
                        sweepAngle = 82f,
                        useCenter = false,
                        topLeft = topLeft1,
                        size = arcSize1,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Counter-rotating inner telemetry ring
            rotate(degrees = innerAngle, pivot = c) {
                val r2 = maxR * 0.62f
                val arcSize2 = Size(r2 * 2f, r2 * 2f)
                val topLeft2 = Offset(c.x - r2, c.y - r2)
                for (startDeg in listOf(18f, 108f, 198f, 288f)) {
                    drawArc(
                        color = hud.glowBlue.copy(alpha = 0.9f),
                        startAngle = startDeg,
                        sweepAngle = 54f,
                        useCenter = false,
                        topLeft = topLeft2,
                        size = arcSize2,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Inner reactor glow circle
            val innerCoreR = maxR * 0.44f * pulseScale
            drawCircle(
                color = coreColor.copy(alpha = 0.16f),
                radius = innerCoreR,
                center = c
            )
            drawCircle(
                color = coreColor,
                radius = innerCoreR,
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "J.A.R.V.I.S",
                style = MaterialTheme.typography.titleLarge,
                color = coreColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = coreStatusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VoiceWaveformVisualizer(
    levels: List<Float>,
    voiceState: VoiceState,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    val barColor = when {
        voiceState == VoiceState.LISTENING -> hud.onlineGreen
        voiceState == VoiceState.SPEAKING -> hud.arcCyan
        isThinking -> hud.glowBlue
        voiceState == VoiceState.WAKE_LISTENING -> hud.warnAmber
        else -> hud.arcCyan.copy(alpha = 0.45f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        val barCount = levels.size.coerceAtLeast(16)
        val totalWidth = size.width
        val slotWidth = totalWidth / barCount
        val barWidth = (slotWidth * 0.54f).coerceAtLeast(3.dp.toPx())
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val baseLevel = levels.getOrElse(i) { 0.12f }
            val animatedLevel = when {
                voiceState == VoiceState.LISTENING -> baseLevel
                voiceState == VoiceState.SPEAKING || isThinking -> {
                    (0.28f + 0.58f * kotlin.math.abs(sin(phase + i * 0.45f))).coerceIn(0.15f, 1f)
                }
                else -> 0.12f + 0.08f * kotlin.math.abs(sin(phase * 0.5f + i * 0.35f))
            }
            val barH = (size.height * animatedLevel).coerceAtLeast(4.dp.toPx())
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun StatusIndicatorPill(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = hud.panelGlass,
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
