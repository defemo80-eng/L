package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AssistantStatus
import com.example.data.model.Emotion
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.MagentaGlow
import com.example.ui.theme.NeonPurple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun emotionAccentColor(emotion: Emotion): Color = when (emotion) {
    Emotion.ALEGRIA -> ElectricCyan
    Emotion.TRISTEZA -> Color(0xFF5C7AEA)
    Emotion.ENOJO_DIVERTIDO -> CoralAlert
    Emotion.SORPRESA -> AmberSpark
    Emotion.CURIOSIDAD -> NeonPurple
    Emotion.ENTUSIASMO -> MagentaGlow
    Emotion.TRANQUILIDAD -> EmeraldPulse
}

@Composable
fun DeiAiAvatar(
    emotion: Emotion,
    status: AssistantStatus,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 188.dp
) {
    val accentColor by animateColorAsState(
        targetValue = emotionAccentColor(emotion),
        animationSpec = tween(450),
        label = "emotion_accent"
    )
    val secondaryColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.ESCUCHANDO -> EmeraldPulse
            AssistantStatus.PROCESANDO -> NeonPurple
            AssistantStatus.HABLANDO -> ElectricCyan
            AssistantStatus.INACTIVO -> ElectricBlue
        },
        animationSpec = tween(350),
        label = "status_secondary"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "avatar_motion")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status == AssistantStatus.PROCESANDO) 2200 else 7500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )
    val speakWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speak_wave"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (status == AssistantStatus.ESCUCHANDO) rmsLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(stiffness = 400f),
        label = "rms_anim"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension * 0.34f
            val dynamicRadius = baseRadius * pulse + (animatedRms * baseRadius * 0.25f)

            // Outer ambient halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.34f),
                        secondaryColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = this.size.minDimension * 0.50f
                ),
                center = center,
                radius = this.size.minDimension * 0.50f
            )

            // Orbiting energy arc
            val orbitRadius = baseRadius * 1.24f + (animatedRms * 14f)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(accentColor, secondaryColor, Color.Transparent, accentColor),
                    center = center
                ),
                startAngle = rotation,
                sweepAngle = if (status == AssistantStatus.PROCESANDO) 240f else 150f,
                useCenter = false,
                topLeft = Offset(center.x - orbitRadius, center.y - orbitRadius),
                size = Size(orbitRadius * 2f, orbitRadius * 2f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )

            // Secondary counter-orbiting ring
            val innerOrbit = baseRadius * 1.12f
            drawArc(
                color = secondaryColor.copy(alpha = 0.55f),
                startAngle = -rotation * 1.3f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(center.x - innerOrbit, center.y - innerOrbit),
                size = Size(innerOrbit * 2f, innerOrbit * 2f),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            // Main core sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A2855),
                        DeepObsidian,
                        Color(0xFF040711)
                    ),
                    center = center,
                    radius = dynamicRadius
                ),
                center = center,
                radius = dynamicRadius
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(accentColor, secondaryColor)
                ),
                center = center,
                radius = dynamicRadius,
                style = Stroke(width = 4f)
            )

            // Floating emotion particles
            val particleCount = if (emotion == Emotion.ENTUSIASMO || emotion == Emotion.ALEGRIA) 6 else 4
            for (i in 0 until particleCount) {
                val angleRad = ((rotation + i * (360f / particleCount)) * PI / 180.0)
                val dist = baseRadius * 1.34f
                val px = center.x + (cos(angleRad) * dist).toFloat()
                val py = center.y + (sin(angleRad) * dist).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) accentColor else secondaryColor,
                    radius = if (status == AssistantStatus.HABLANDO) 4.5f else 3f,
                    center = Offset(px, py)
                )
            }

            // Expressive futuristic face inside core
            val eyeOffsetX = dynamicRadius * 0.34f
            val eyeY = center.y - dynamicRadius * 0.14f
            val eyeColor = accentColor

            when (emotion) {
                Emotion.ALEGRIA, Emotion.ENTUSIASMO -> {
                    // Happy bright eyes + upward arcs
                    val eyeHeight = if (emotion == Emotion.ENTUSIASMO) dynamicRadius * 0.20f else dynamicRadius * 0.16f
                    drawRoundRect(
                        color = eyeColor,
                        topLeft = Offset(center.x - eyeOffsetX - 10f, eyeY - eyeHeight / 2f),
                        size = Size(20f, eyeHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = eyeColor,
                        topLeft = Offset(center.x + eyeOffsetX - 10f, eyeY - eyeHeight / 2f),
                        size = Size(20f, eyeHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }

                Emotion.TRISTEZA -> {
                    // Droopy slanted soft eyes
                    drawLine(
                        color = eyeColor,
                        start = Offset(center.x - eyeOffsetX - 12f, eyeY + 5f),
                        end = Offset(center.x - eyeOffsetX + 10f, eyeY - 4f),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = eyeColor,
                        start = Offset(center.x + eyeOffsetX - 10f, eyeY - 4f),
                        end = Offset(center.x + eyeOffsetX + 12f, eyeY + 5f),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }

                Emotion.ENOJO_DIVERTIDO -> {
                    // Playful angled eyebrows + glowing eyes
                    drawLine(
                        color = eyeColor,
                        start = Offset(center.x - eyeOffsetX - 14f, eyeY - 14f),
                        end = Offset(center.x - eyeOffsetX + 12f, eyeY - 4f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = eyeColor,
                        start = Offset(center.x + eyeOffsetX - 12f, eyeY - 4f),
                        end = Offset(center.x + eyeOffsetX + 14f, eyeY - 14f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = eyeColor, radius = 8f, center = Offset(center.x - eyeOffsetX, eyeY + 4f))
                    drawCircle(color = eyeColor, radius = 8f, center = Offset(center.x + eyeOffsetX, eyeY + 4f))
                }

                Emotion.SORPRESA -> {
                    // Wide luminous circular eyes
                    drawCircle(
                        color = eyeColor,
                        radius = 12f,
                        center = Offset(center.x - eyeOffsetX, eyeY),
                        style = Stroke(width = 5f)
                    )
                    drawCircle(
                        color = eyeColor,
                        radius = 12f,
                        center = Offset(center.x + eyeOffsetX, eyeY),
                        style = Stroke(width = 5f)
                    )
                    drawCircle(color = eyeColor, radius = 4f, center = Offset(center.x - eyeOffsetX, eyeY))
                    drawCircle(color = eyeColor, radius = 4f, center = Offset(center.x + eyeOffsetX, eyeY))
                }

                Emotion.CURIOSIDAD -> {
                    // One larger inquisitive eye, one normal
                    drawCircle(color = eyeColor, radius = 12f, center = Offset(center.x - eyeOffsetX, eyeY - 2f))
                    drawCircle(color = eyeColor, radius = 8f, center = Offset(center.x + eyeOffsetX, eyeY + 2f))
                }

                Emotion.TRANQUILIDAD -> {
                    // Calm horizontal glowing eyes
                    drawLine(
                        color = eyeColor,
                        start = Offset(center.x - eyeOffsetX - 12f, eyeY),
                        end = Offset(center.x - eyeOffsetX + 12f, eyeY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = eyeColor,
                        start = Offset(center.x + eyeOffsetX - 12f, eyeY),
                        end = Offset(center.x + eyeOffsetX + 12f, eyeY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Mouth / Voice visualizer
            val mouthY = center.y + dynamicRadius * 0.25f
            if (status == AssistantStatus.HABLANDO || status == AssistantStatus.ESCUCHANDO) {
                val amp = if (status == AssistantStatus.ESCUCHANDO) {
                    (0.35f + animatedRms * 0.9f)
                } else {
                    (0.45f + speakWave * 0.7f)
                }
                val bars = 5
                val spacing = 13f
                val startX = center.x - ((bars - 1) * spacing) / 2f
                for (b in 0 until bars) {
                    val factor = 1f - (kotlin.math.abs(b - 2) * 0.22f)
                    val h = (12f + 22f * amp * factor)
                    drawLine(
                        color = secondaryColor,
                        start = Offset(startX + b * spacing, mouthY - h / 2f),
                        end = Offset(startX + b * spacing, mouthY + h / 2f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }
            } else {
                val mouthPath = Path()
                val mw = dynamicRadius * 0.34f
                when (emotion) {
                    Emotion.ALEGRIA, Emotion.ENTUSIASMO -> {
                        mouthPath.moveTo(center.x - mw, mouthY - 4f)
                        mouthPath.quadraticTo(center.x, mouthY + 18f, center.x + mw, mouthY - 4f)
                        drawPath(mouthPath, color = eyeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                    Emotion.TRISTEZA -> {
                        mouthPath.moveTo(center.x - mw * 0.8f, mouthY + 10f)
                        mouthPath.quadraticTo(center.x, mouthY - 8f, center.x + mw * 0.8f, mouthY + 10f)
                        drawPath(mouthPath, color = eyeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                    Emotion.ENOJO_DIVERTIDO -> {
                        mouthPath.moveTo(center.x - mw * 0.8f, mouthY + 2f)
                        mouthPath.quadraticTo(center.x - mw * 0.25f, mouthY - 8f, center.x, mouthY + 2f)
                        mouthPath.quadraticTo(center.x + mw * 0.25f, mouthY + 10f, center.x + mw * 0.8f, mouthY)
                        drawPath(mouthPath, color = eyeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                    Emotion.SORPRESA -> {
                        drawCircle(
                            color = eyeColor,
                            radius = 10f,
                            center = Offset(center.x, mouthY + 2f),
                            style = Stroke(width = 5f)
                        )
                    }
                    Emotion.CURIOSIDAD -> {
                        mouthPath.moveTo(center.x - mw * 0.6f, mouthY + 2f)
                        mouthPath.quadraticTo(center.x + mw * 0.1f, mouthY + 8f, center.x + mw * 0.7f, mouthY - 4f)
                        drawPath(mouthPath, color = eyeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                    Emotion.TRANQUILIDAD -> {
                        mouthPath.moveTo(center.x - mw * 0.7f, mouthY)
                        mouthPath.quadraticTo(center.x, mouthY + 9f, center.x + mw * 0.7f, mouthY)
                        drawPath(mouthPath, color = eyeColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                }
            }
        }

        // Subtle DeiAI mini badge at bottom-right of avatar
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, accentColor, CircleShape)
        ) {
            Image(
                painter = painterResource(R.drawable.deiai_logo),
                contentDescription = stringResource(R.string.deiai_logo_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PrivacyMicBadge(
    isMicActive: Boolean,
    isWakeServiceRunning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isMicActive && !isWakeServiceRunning) return

    val infiniteTransition = rememberInfiniteTransition(label = "mic_badge")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    val badgeColor = if (isMicActive) EmeraldPulse else ElectricCyan
    val labelText = if (isMicActive) {
        "Micrófono activo"
    } else {
        "Modo «Hola DeiAI» activo"
    }

    Surface(
        color = badgeColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = labelText,
                tint = badgeColor,
                modifier = Modifier
                    .size(16.dp)
                    .alpha(dotAlpha)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = badgeColor
            )
        }
    }
}
