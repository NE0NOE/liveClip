package com.example.clipsync.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.clipsync.data.SyncMode

@Composable
fun VoiceDictationDialog(
    state: ClipSyncUiState,
    onDismiss: () -> Unit,
    onToggleListening: () -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    onClearSession: () -> Unit,
    onSendEnter: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D16).copy(alpha = 0.98f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header with Close and Mode Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isLive = state.serverConfig.syncMode == SyncMode.LIVE_TYPING
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isLive) Color(0xFFF59E0B).copy(alpha = 0.18f) else Color(0xFF3B82F6).copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = if (isLive) Icons.Default.Bolt else Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = if (isLive) Color(0xFFF59E0B) else Color(0xFF60A5FA),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isLive) "⚡ En Vivo a PC" else "📋 Portapapeles PC",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isLive) Color(0xFFF59E0B) else Color(0xFF60A5FA)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Continuous Listening Switch Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Escucha continua (Anti-pausas)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            Text(
                                text = "No se apaga si haces pausas para pensar",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = state.isVoiceContinuous,
                            onCheckedChange = onToggleContinuous,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.35f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Center Mic Pulse Visualizer (Inspired by YouTube Voice Search)
                VoiceMicVisualizer(
                    isListening = state.isVoiceListening,
                    voiceRms = state.voiceRms,
                    onToggle = onToggleListening
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status Message
                Text(
                    text = state.voiceStatusMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (state.isVoiceListening) Color(0xFF34D399) else Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Live Transcription Box (No keyboard opens)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2A))
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState)
                    ) {
                        val sessionText = state.voiceSessionText
                        val partialText = state.voicePartialText

                        if (sessionText.isBlank() && partialText.isBlank()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (state.isVoiceListening) "Habla ahora...\nLo que digas se escribirá en tiempo real en tu PC" else "Toca el micrófono para comenzar a dictar",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.35f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Committed text
                            if (sessionText.isNotBlank()) {
                                Text(
                                    text = sessionText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = Color.White
                                )
                            }

                            // Ongoing real-time utterance
                            if (partialText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = partialText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF60A5FA)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clear Session
                    OutlinedButton(
                        onClick = onClearSession,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = state.voiceSessionText.isNotBlank() || state.voicePartialText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Borrar", maxLines = 1)
                    }

                    // Send Enter to PC
                    OutlinedButton(
                        onClick = onSendEnter,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardReturn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enviar Enter", maxLines = 1)
                    }

                    // Done & Keep in editor
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Listo",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceMicVisualizer(
    isListening: Boolean,
    voiceRms: Float,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Pulse animation based on voice volume level
    val animatedScale by animateFloatAsState(
        targetValue = if (isListening) (1f + voiceRms * 0.45f).coerceIn(1f, 1.45f) else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "micScale"
    )

    // Gentle infinite pulse when listening even without speaking
    val infiniteTransition = rememberInfiniteTransition(label = "haloTransition")
    val idleHaloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleHalo"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(140.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            )
    ) {
        if (isListening) {
            // Outer Halo Ring (Reactive to speech RMS)
            Box(
                modifier = Modifier
                    .size(125.dp)
                    .scale(if (voiceRms > 0.05f) animatedScale * 1.15f else idleHaloScale)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.18f))
            )

            // Middle Halo Ring
            Box(
                modifier = Modifier
                    .size(105.dp)
                    .scale(animatedScale)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.32f))
            )
        }

        // Main Center Button
        val mainGradient = if (isListening) {
            Brush.linearGradient(
                colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626))
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
            )
        }

        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(mainGradient)
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isListening) "Pausar dictado" else "Iniciar dictado",
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}
