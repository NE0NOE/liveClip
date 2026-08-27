package com.example.clipsync.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Segundo plano activo en barra de notificaciones",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Editor Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Editor Top Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val wordCount = if (state.text.isBlank()) 0 else state.text.trim().split("\\s+".toRegex()).size
                        Text(
                            text = "${state.text.length} caracteres • $wordCount palabras",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Status chip
                        SyncStatusIndicator(state = state)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Action Bar inside Card: Balanced 3-button layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Pegar del Celular (Primary Action)
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
                                .weight(1.25f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pegar",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }

                        // 2. Copiar al celular
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AutoClip", state.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copiado al celular", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = state.text.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar", maxLines = 1)
                        }

                        // 3. Limpiar
                        OutlinedButton(
                            onClick = {
                                viewModel.clearEditor()
                                Toast.makeText(context, "Texto limpiado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = state.text.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", maxLines = 1)
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
    val modes = listOf(
        Pair(SyncMode.CLIPBOARD, "📋 Portapapeles (Ctrl+V)"),
        Pair(SyncMode.LIVE_TYPING, "⚡ Escritura en Vivo")
    )
    val selectedIndex = if (currentMode == SyncMode.CLIPBOARD) 0 else 1

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        modes.forEachIndexed { index, (mode, title) ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelectMode(mode) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
fun TopAppBarSection(
    state: ClipSyncUiState,
    onQrClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = "AutoClip Pro",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = if (state.serverStatus.isOnline) "PC: ${state.serverConfig.host}" else "Sin conexión al PC",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.serverStatus.isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onQrClick) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear QR", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRefreshClick) {
                Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
            }
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Default.History, contentDescription = "Historial")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
            }
        }
    }
}

@Composable
fun SyncStatusIndicator(state: ClipSyncUiState) {
    val (color, text) = when (state.syncState) {
        SyncState.SYNCING -> Pair(Color(0xFFF59E0B), "Enviando...")
        SyncState.SUCCESS -> Pair(Color(0xFF10B981), if (state.serverConfig.syncMode == SyncMode.LIVE_TYPING) "En Vivo ⚡" else "Sincronizado")
        SyncState.ERROR -> Pair(MaterialTheme.colorScheme.error, "Error de red")
        SyncState.IDLE -> if (state.serverStatus.isOnline) {
            Pair(Color(0xFF10B981), "Listo")
        } else {
            Pair(MaterialTheme.colorScheme.error, "Desconectado")
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
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
