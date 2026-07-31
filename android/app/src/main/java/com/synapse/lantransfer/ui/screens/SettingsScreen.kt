package com.synapse.lantransfer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.synapse.lantransfer.ui.components.GlassCard
import com.synapse.lantransfer.ui.components.PigeonLogo
import com.synapse.lantransfer.ui.screens.viewmodel.SettingsViewModel
import com.synapse.lantransfer.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val deviceNameState by viewModel.deviceName.collectAsState()
    val downloadDirState by viewModel.downloadDir.collectAsState()
    val autoAcceptState by viewModel.autoAccept.collectAsState()
    val themeModeState by viewModel.themeMode.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    // Local editable copies
    var deviceName by remember(deviceNameState) { mutableStateOf(deviceNameState) }
    var downloadDir by remember(downloadDirState) { mutableStateOf(downloadDirState) }
    var autoAccept by remember(autoAcceptState) { mutableStateOf(autoAcceptState) }
    var themeMode by remember(themeModeState) { mutableStateOf(themeModeState) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dirPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                downloadDir = uri.toString()
            } catch (e: Exception) {
                // Ignore if we can't take persistable permissions
            }
        }
    }

    val scrollState = rememberScrollState()

    val saving = saveState == SettingsViewModel.SaveState.SAVING
    val saved = saveState == SettingsViewModel.SaveState.SAVED

    val saveScale by animateFloatAsState(
        targetValue = if (saving) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "saveScale"
    )

    // Helper to format URI nicely
    fun formatDirName(dirValue: String): String {
        return if (dirValue.startsWith("content://")) {
            val uri = android.net.Uri.parse(dirValue)
            uri.lastPathSegment?.substringAfter(":") ?: "Selected Directory"
        } else {
            val parts = dirValue.split("/")
            if (parts.size >= 2) "${parts[parts.size - 2]}/${parts.last()}" else dirValue
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Header
        Text(
            text = "Settings",
            style = SynapseTypography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Configure Synapse to your preferences.",
            style = SynapseTypography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Settings Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "DEVICE",
                style = SynapseTypography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

                // Device Name
                SettingRow(
                    icon = Icons.Rounded.Smartphone,
                    label = "Device Name",
                    description = "How your device appears to peers"
                ) {
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { newValue: String -> deviceName = newValue },
                        modifier = Modifier.width(160.dp),
                        textStyle = SynapseTypography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            focusedBorderColor = Accent1,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Download Dir
                SettingRow(
                    icon = Icons.Rounded.Folder,
                    label = "Download Directory",
                    description = "Where received files are saved"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatDirName(downloadDir),
                            style = SynapseTypography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { dirPickerLauncher.launch(null) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Browse",
                                style = SynapseTypography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Auto accept
                SettingRow(
                    icon = Icons.Rounded.Security,
                    label = "Auto-Accept",
                    description = "Receive files without confirmation"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedToggle(
                            checked = autoAccept,
                            onCheckedChange = { autoAccept = it }
                        )
                        Text(
                            text = if (autoAccept) "On" else "Off",
                            style = SynapseTypography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Theme Mode
                SettingRow(
                    icon = Icons.Rounded.Palette,
                    label = "Theme Mode",
                    description = "App appearance preference"
                ) {
                    ThemeSelector(
                        selectedMode = themeMode,
                        onModeSelected = { themeMode = it }
                    )
                }
        }

        // About Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                PigeonLogo(logoSize = 60.dp, enableAnimation = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Synapse for Android",
                        style = SynapseTypography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version 1.0.0",
                        style = SynapseTypography.labelSmall,
                        color = Accent1,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                    Text(
                        text = "Secure peer-to-peer file transfer over your local network.",
                        style = SynapseTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp)
                .scale(saveScale),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        viewModel.saveSettings(deviceName, downloadDir, autoAccept, themeMode)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = if (saved) Brush.linearGradient(listOf(Success, Color(0xFF5A8A5A)))
                                else Brush.linearGradient(listOf(Accent1, Accent2)),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 40.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (saved) Icons.Rounded.Check
                                    else if (saving) Icons.Rounded.Autorenew
                                    else Icons.Rounded.Save,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (saved) "Saved!" else if (saving) "Saving..." else "Save Settings",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
                Text(
                    text = "Settings are applied immediately",
                    style = SynapseTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ThemeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf(
        "system" to "System",
        "light" to "Light",
        "dark" to "Dark"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = selectedMode == mode
            val selectionBgColor by animateColorAsState(
                targetValue = if (isSelected) Accent1 else Color.Transparent,
                animationSpec = tween(200),
                label = "selectionBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "textColor"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(selectionBgColor)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = SynapseTypography.labelSmall,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    label: String,
    description: String,
    control: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Accent1,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = SynapseTypography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = SynapseTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .padding(start = 48.dp)
        ) {
            control()
        }
    }
}

@Composable
fun AnimatedToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val knobOffset by animateFloatAsState(
        targetValue = if (checked) 22f else 2f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "knobOffset"
    )
    val bgColor by animateColorAsState(
        targetValue = if (checked) Accent1 else Color.White.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "bgColor"
    )
    val knobColor by animateColorAsState(
        targetValue = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(200),
        label = "knobColor"
    )

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bgColor)
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(13.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset.dp)
                .size(20.dp)
                .background(knobColor, CircleShape)
        )
    }
}
