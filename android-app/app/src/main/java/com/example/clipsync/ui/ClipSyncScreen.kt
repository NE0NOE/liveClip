package com.example.clipsync.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.clipsync.data.SavedComputer
import com.example.clipsync.data.ServerConfig
import com.example.clipsync.data.SyncMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipSyncScreen(
    viewModel: ClipSyncViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Keep screen on while voice listening is active so device doesn't sleep
    DisposableEffect(state.isVoiceListening) {
        if (state.isVoiceListening) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Audio recording permission launcher for voice dictation
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceListening()
        } else {
            Toast.makeText(context, "Se requiere permiso de micrófono para dictar", Toast.LENGTH_LONG).show()
        }
    }

    val requestVoiceDictation = {
        if (state.isVoiceListening) {
            viewModel.stopVoiceListening()
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startVoiceListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleBackgroundService(true)
            Toast.makeText(context, "Servicio en segundo plano activado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Se requiere permiso para mostrar botones en notificaciones", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBarSection(
                state = state,
                onQrClick = { viewModel.openQrScanner(true) },
                onVoiceClick = { requestVoiceDictation() },
                onRefreshClick = { viewModel.checkConnection() },
                onHistoryClick = { viewModel.openHistory(true) },
                onSettingsClick = { viewModel.openSettings(true) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Mode Tabs: Clipboard vs Live PC Keystroke Typing
            ModeSelectorTabs(
                currentMode = state.serverConfig.syncMode,
                onSelectMode = { viewModel.setSyncMode(it) }
            )

            // Background Service active banner
            AnimatedVisibility(visible = state.isServiceRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Segundo plano activo en barra de notificaciones",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF34D399)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Editor Card: Frosted Obsidian Glass Container
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.4f),
                        spotColor = Color(0xFF38BDF8).copy(alpha = 0.15f)
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F172A)
                ),
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    // Editor Top Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val wordCount = if (state.text.isBlank()) 0 else state.text.trim().split("\\s+".toRegex()).size
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${state.text.length} caracteres • $wordCount palabras",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF94A3B8)
                            )
                        }

                        // Status chip
                        SyncStatusIndicator(state = state)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Text Field
                    val placeholderText = if (state.serverConfig.syncMode == SyncMode.LIVE_TYPING) {
                        "⚡ Modo Escritura en Vivo activo:\n\nCada letra que escribas o dictes en el celular se escribirá automáticamente en tiempo real en la ventana o campo activo de tu PC."
                    } else {
                        "📋 Modo Portapapeles activo:\n\nTodo lo que escribas o pegues aquí se sincroniza automáticamente al portapapeles de tu PC para que hagas Ctrl+V."
                    }

                    OutlinedTextField(
                        value = state.text,
                        onValueChange = { viewModel.onTextChanged(it) },
                        placeholder = {
                            Text(
                                text = placeholderText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF64748B)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8).copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFF131C2E).copy(alpha = 0.7f),
                            unfocusedContainerColor = Color(0xFF131C2E).copy(alpha = 0.45f),
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC)
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Bar: Dynamic Island morphing transition
                    AnimatedContent(
                        targetState = state.isVoiceListening,
                        transitionSpec = {
                            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                             scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(120)) +
                                scaleOut(targetScale = 0.94f, animationSpec = tween(120))
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
                        label = "DynamicIslandVoiceTransition"
                    ) { isListening ->
                        if (isListening) {
                            DynamicIslandVoiceBar(
                                state = state,
                                onEnterClick = { viewModel.sendVoiceEnter() },
                                onDoneClick = { viewModel.stopVoiceListening() }
                            )
                        } else {
                        // Normal 4-Button Deck (Elevated Aesthetic)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Pegar del Celular (Primary Gradient)
                            Button(
                                onClick = {
                                    val ok = viewModel.pasteFromLocalClipboard(context)
                                    if (ok) {
                                        Toast.makeText(context, "Pegado y sincronizado al PC", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(46.dp)
                                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB)
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Pegar",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }

                            // 2. Dictar por Voz (Vibrant Coral/Red)
                            Button(
                                onClick = requestVoiceDictation,
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(46.dp)
                                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626)
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Dictar",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }

                            // 3. Copiar al celular (Frosted Glass)
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("AutoClip", state.text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copiado al celular", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.07f)
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                enabled = state.text.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Copiar", maxLines = 1, color = Color(0xFFF8FAFC), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
                            }

                            // 4. Limpiar (Frosted Glass)
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearEditor()
                                    Toast.makeText(context, "Texto limpiado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.07f)
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                enabled = state.text.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFF87171)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Limpiar", maxLines = 1, color = Color(0xFFF8FAFC), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer info
            Text(
                text = state.lastSyncMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    // QR Scanner Dialog
    if (state.isQrScannerOpen) {
        QrScannerDialog(
            onDismiss = { viewModel.openQrScanner(false) },
            onQrScanned = { qrResult ->
                val ok = viewModel.handleQrScanned(qrResult)
                if (ok) {
                    Toast.makeText(context, "¡Conectado exitosamente por QR!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Código QR no válido para AutoClip", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Settings Dialog
    if (state.isSettingsOpen) {
        SettingsDialog(
            config = state.serverConfig,
            serverStatus = state.serverStatus,
            savedComputers = state.savedComputers,
            isServiceRunning = state.isServiceRunning,
            onDismiss = { viewModel.openSettings(false) },
            onSave = { newConfig ->
                viewModel.updateServerConfig(newConfig)
                viewModel.openSettings(false)
            },
            onTestConnection = { viewModel.checkConnection() },
            onScanQr = {
                viewModel.openSettings(false)
                viewModel.openQrScanner(true)
            },
            onSelectComputer = { comp ->
                viewModel.selectSavedComputer(comp)
            },
            onDeleteComputer = { id ->
                viewModel.deleteSavedComputer(id)
            },
            onToggleService = { enable ->
                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.toggleBackgroundService(true)
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    viewModel.toggleBackgroundService(enable)
                }
            }
        )
    }

    // History Sheet
    if (state.isHistoryOpen) {
        HistoryBottomSheet(
            history = state.history,
            onDismiss = { viewModel.openHistory(false) },
            onSelectClip = { clipText ->
                viewModel.restoreClip(clipText)
                viewModel.openHistory(false)
                Toast.makeText(context, "Texto restaurado y sincronizado", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ModeSelectorTabs(
    currentMode: SyncMode,
    onSelectMode: (SyncMode) -> Unit
) {
    val isLive = currentMode == SyncMode.LIVE_TYPING
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .shadow(4.dp, RoundedCornerShape(23.dp), ambientColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(23.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Portapapeles (Ctrl+V)
            val tab1Modifier = if (!isLive) {
                Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF38BDF8))))
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .then(tab1Modifier)
                    .clickable { onSelectMode(SyncMode.CLIPBOARD) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (!isLive) Color.White else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Portapapeles",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (!isLive) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (!isLive) Color.White else Color(0xFF94A3B8)
                    )
                }
            }

            // Tab 2: Escritura en Vivo (Direct Type)
            val tab2Modifier = if (isLive) {
                Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFA855F7))))
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .then(tab2Modifier)
                    .clickable { onSelectMode(SyncMode.LIVE_TYPING) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isLive) Color.White else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "En Vivo ⚡",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isLive) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isLive) Color.White else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun TopAppBarSection(
    state: ClipSyncUiState,
    onQrClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand & Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = "AutoClip",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color(0xFFF8FAFC)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (state.serverStatus.isOnline) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Text(
                        text = if (state.serverStatus.isOnline) "PC: ${state.serverConfig.host}" else "Sin conexión al PC",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (state.serverStatus.isOnline) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                }
            }
        }

        // Glass Action Pills Bar
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = if (state.isVoiceListening) {
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    } else {
                        Modifier.size(34.dp)
                    }
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Dictado por Voz",
                        tint = if (state.isVoiceListening) Color.White else Color(0xFFF43F5E),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onQrClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear QR", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRefreshClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onHistoryClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.History, contentDescription = "Historial", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SyncStatusIndicator(state: ClipSyncUiState) {
    val (color, text) = when (state.syncState) {
        SyncState.SYNCING -> Pair(Color(0xFFFBBF24), "Enviando...")
        SyncState.SUCCESS -> Pair(Color(0xFF10B981), if (state.serverConfig.syncMode == SyncMode.LIVE_TYPING) "En Vivo ⚡" else "Sincronizado")
        SyncState.ERROR -> Pair(Color(0xFFEF4444), "Error de red")
        SyncState.IDLE -> if (state.serverStatus.isOnline) {
            Pair(Color(0xFF38BDF8), "Listo")
        } else {
            Pair(Color(0xFFEF4444), "Desconectado")
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    config: ServerConfig,
    serverStatus: com.example.clipsync.data.ServerStatus,
    savedComputers: List<SavedComputer>,
    isServiceRunning: Boolean,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
    onTestConnection: () -> Unit,
    onScanQr: () -> Unit,
    onSelectComputer: (SavedComputer) -> Unit,
    onDeleteComputer: (String) -> Unit,
    onToggleService: (Boolean) -> Unit
) {
    var host by remember { mutableStateOf(config.host) }
    var portText by remember { mutableStateOf(config.port.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Laptop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ajustes & Conexión PC")
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // QR Scanner CTA button
                item {
                    Button(
                        onClick = onScanQr,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escanear Código QR de la PC")
                    }
                }

                // Background Service Switch
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Servicio en Segundo Plano",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Botones en barra de notificaciones para pegar y enviar al instante",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isServiceRunning,
                                onCheckedChange = { onToggleService(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // IP and Port Inputs
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Configuración Manual de Conexión:", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it.trim() },
                            label = { Text("IP de la Computadora") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Puerto del Servidor") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Test Connection Status
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (serverStatus.isOnline) "🟢 Conexión exitosa" else "🔴 ${serverStatus.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (serverStatus.isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onTestConnection) {
                            Text("Probar")
                        }
                    }
                }

                // Saved Computers List
                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Mis Computadoras Guardadas:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }

                items(savedComputers, key = { it.id }) { comp ->
                    val isSelected = comp.host == host && comp.port == portText.toIntOrNull()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                host = comp.host
                                portText = comp.port.toString()
                                onSelectComputer(comp)
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Computer,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(comp.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("${comp.host}:${comp.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            IconButton(onClick = { onDeleteComputer(comp.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Eliminar", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 8765
                    onSave(config.copy(host = host, port = port))
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    history: List<ClipItem>,
    onDismiss: () -> Unit,
    onSelectClip: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Historial Reciente Enviado a la PC",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Toca cualquier texto para volver a enviarlo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aún no hay textos en el historial", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(history, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectClip(item.text) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${item.length} caracteres • ${item.formattedTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DynamicIslandVoiceBar(
    state: ClipSyncUiState,
    onEnterClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    val rms = state.voiceRms.coerceIn(0f, 1f)

    val micScale by animateFloatAsState(
        targetValue = (1f + rms * 0.35f).coerceIn(1f, 1.35f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "islandMicScale"
    )

    // Dynamic wave bars for sound equalizer effect
    val bar1Height by animateDpAsState(
        targetValue = (5 + rms * 14 * 0.7f).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "b1"
    )
    val bar2Height by animateDpAsState(
        targetValue = (8 + rms * 20 * 1.3f).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "b2"
    )
    val bar3Height by animateDpAsState(
        targetValue = (6 + rms * 17 * 1.0f).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "b3"
    )
    val bar4Height by animateDpAsState(
        targetValue = (9 + rms * 22 * 1.4f).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "b4"
    )
    val bar5Height by animateDpAsState(
        targetValue = (5 + rms * 13 * 0.8f).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "b5"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color(0xFFEF4444).copy(alpha = 0.35f),
                spotColor = Color(0xFF38BDF8).copy(alpha = 0.45f)
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        ),
        border = BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFEF4444).copy(alpha = 0.85f),
                    Color(0xFFA855F7).copy(alpha = 0.75f),
                    Color(0xFF38BDF8).copy(alpha = 0.85f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Glowing Mic and Dynamic Sound Waveform
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing Mic Circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFF991B1B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Micrófono",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Waveform Equalizer + Status
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🎙️ ESCUCHANDO",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFFF87171)
                        )

                        // Equalizer bars
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            modifier = Modifier.height(18.dp)
                        ) {
                            Box(modifier = Modifier.width(3.dp).height(bar1Height).clip(RoundedCornerShape(2.dp)).background(Color(0xFF38BDF8)))
                            Box(modifier = Modifier.width(3.dp).height(bar2Height).clip(RoundedCornerShape(2.dp)).background(Color(0xFFA855F7)))
                            Box(modifier = Modifier.width(3.dp).height(bar3Height).clip(RoundedCornerShape(2.dp)).background(Color(0xFF34D399)))
                            Box(modifier = Modifier.width(3.dp).height(bar4Height).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF43F5E)))
                            Box(modifier = Modifier.width(3.dp).height(bar5Height).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFBBF24)))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (state.voicePartialText.isNotBlank()) {
                            "“${state.voicePartialText}”"
                        } else {
                            "Habla con libertad, no se cortará"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (state.voicePartialText.isNotBlank()) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = if (state.voicePartialText.isNotBlank()) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Actions (Enter & Done)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Enter button (Sends newline to PC)
                IconButton(
                    onClick = onEnterClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "Enviar Enter",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Done Button (Emerald Pill)
                Button(
                    onClick = onDoneClick,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Listo",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

