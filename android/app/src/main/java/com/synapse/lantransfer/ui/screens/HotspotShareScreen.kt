package com.synapse.lantransfer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.synapse.lantransfer.data.model.SelectedFile
import com.synapse.lantransfer.data.service.TransferManager
import com.synapse.lantransfer.ui.components.GlassCard
import com.synapse.lantransfer.ui.theme.*
import com.synapse.lantransfer.util.HotspotManager
import com.synapse.lantransfer.util.QRCodeGenerator
import com.synapse.lantransfer.util.SimpleHttpFileServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two hosting modes available on this screen:
 *
 *  • [NEW_HOTSPOT]      — the normal path: Synapse creates its own LocalOnlyHotspot.
 *                          The user shares both the SSID/password and the file URL.
 *
 *  • [EXISTING_HOTSPOT] — triggered automatically when the user's Mobile Hotspot
 *                          (Wi-Fi tethering) is already on. No new hotspot is
 *                          created; Synapse just starts the HTTP server on the
 *                          existing tethering IP so any connected device can open
 *                          the URL in a browser and download files directly.
 */
private enum class HostingMode { NEW_HOTSPOT, EXISTING_HOTSPOT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HotspotShareScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transferManager = remember { TransferManager.getInstance(context) }
    val hotspotManager = remember { HotspotManager(context) }

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<List<SelectedFile>>(emptyList()) }

    var isHosting by remember { mutableStateOf(false) }
    var hostingMode by remember { mutableStateOf(HostingMode.NEW_HOTSPOT) }

    // NEW_HOTSPOT mode fields
    var hotspotSsid by remember { mutableStateOf<String?>(null) }
    var hotspotPassword by remember { mutableStateOf<String?>(null) }

    // Common session fields
    var activePort by remember { mutableStateOf<Int?>(null) }
    var localIp by remember { mutableStateOf<String?>(null) }

    var httpFileServer by remember { mutableStateOf<SimpleHttpFileServer?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showWifiQrDialog by remember { mutableStateOf(false) }

    // Toast-like copy feedback
    var showCopiedFeedback by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = selectedUris + uris
            scope.launch(Dispatchers.IO) {
                val resolved = uris.map { uri ->
                    var name = "File"
                    var size = 0L
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex)
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }
                    SelectedFile(name = name, size = size, uri = uri.toString())
                }
                withContext(Dispatchers.Main) {
                    selectedFiles = selectedFiles + resolved
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            isHosting = true
        } else {
            errorMessage = "Location permission is required to create a hotspot"
        }
    }

    // ── Main hosting state machine ───────────────────────────────────────────
    LaunchedEffect(isHosting) {
        if (isHosting) {
            errorMessage = null

            // Detect whether the user's Mobile Hotspot is already on
            val tetheringOn = hotspotManager.isTetheringActive()

            if (tetheringOn) {
                // ── Mode B: share over the existing Mobile Hotspot ──────────
                hostingMode = HostingMode.EXISTING_HOTSPOT

                scope.launch(Dispatchers.IO) {
                    try {
                        // Brief pause for any pending network settling
                        delay(500)

                        // 1. Get the phone's IP on the tethering network
                        val ip = hotspotManager.getTetheringIpAddress()
                        withContext(Dispatchers.Main) { localIp = ip }

                        // 2. Start TCP transfer server (for Synapse app receivers)
                        val port = transferManager.startSending(selectedUris, "Synapse Hotspot")
                        withContext(Dispatchers.Main) { activePort = port }

                        // 3. Start HTTP file server so any browser can download
                        val httpServer = SimpleHttpFileServer(context, selectedUris, 8080)
                        httpServer.start()
                        withContext(Dispatchers.Main) { httpFileServer = httpServer }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Failed to start file server: ${e.message}"
                            isHosting = false
                        }
                    }
                }
            } else {
                // ── Mode A: create a new LocalOnlyHotspot ───────────────────
                hostingMode = HostingMode.NEW_HOTSPOT

                // Ensure location permission (required by startLocalOnlyHotspot)
                val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasLocation) {
                    isHosting = false
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    return@LaunchedEffect
                }

                hotspotManager.startHotspot(
                    onStarted = { ssid, key ->
                        hotspotSsid = ssid
                        hotspotPassword = key

                        scope.launch(Dispatchers.IO) {
                            try {
                                // Wait for network interfaces to configure
                                delay(2000)

                                // 1. Resolve host IP on hotspot subnet
                                val ip = HotspotManager.getLocalIpAddress() ?: "192.168.43.1"
                                withContext(Dispatchers.Main) { localIp = ip }

                                // 2. Start fast Synapse TCP socket server
                                val port = transferManager.startSending(selectedUris, "Synapse Hotspot")
                                withContext(Dispatchers.Main) { activePort = port }

                                // 3. Start standard web browser file server on port 8080
                                val httpServer = SimpleHttpFileServer(context, selectedUris, 8080)
                                httpServer.start()
                                withContext(Dispatchers.Main) { httpFileServer = httpServer }

                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Failed starting server: ${e.message}"
                                    isHosting = false
                                    hotspotManager.stopHotspot()
                                }
                            }
                        }
                    },
                    onFailed = { err ->
                        errorMessage = err
                        isHosting = false
                    }
                )
            }
        } else {
            // ── Clean up session ─────────────────────────────────────────────
            hotspotManager.stopHotspot()
            transferManager.stopSending()
            httpFileServer?.stop()
            httpFileServer = null
            hotspotSsid = null
            hotspotPassword = null
            activePort = null
            localIp = null
            showWifiQrDialog = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hotspotManager.stopHotspot()
            transferManager.stopSending()
            httpFileServer?.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        // ── Top Title Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isHosting) isHosting = false
                    onBack()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = if (isDarkTheme) TextPrimaryDark else TextPrimary
                )
            }

            Text(
                text = "Hotspot Direct Share",
                style = SynapseTypography.displayMedium.copy(fontSize = 24.sp),
                color = if (isDarkTheme) TextPrimaryDark else TextPrimary,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        // ── Error Banner ──
        AnimatedVisibility(visible = errorMessage != null) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Error, contentDescription = null, tint = Danger)
                    Text(
                        text = errorMessage ?: "",
                        style = SynapseTypography.bodyMedium,
                        color = Danger
                    )
                }
            }
        }

        // ── Copied feedback toast ──
        AnimatedVisibility(
            visible = showCopiedFeedback,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Accent1.copy(alpha = 0.15f), Accent2.copy(alpha = 0.15f)))
                    )
                    .border(1.dp, Accent1.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Accent1, modifier = Modifier.size(16.dp))
                    Text("URL copied to clipboard!", style = SynapseTypography.labelMedium, color = Accent1)
                }
            }
        }

        // ── Screen Body ──
        Box(modifier = Modifier.weight(1f)) {
            if (!isHosting) {
                // ── State: File selection ────────────────────────────────────
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Step 1: Select files to share",
                        style = SynapseTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    ) {
                        if (selectedFiles.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { filePicker.launch("*/*") },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No files selected",
                                    style = SynapseTypography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap to browse and select files to share",
                                    style = SynapseTypography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(selectedFiles) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.InsertDriveFile,
                                            contentDescription = null,
                                            tint = Accent1,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = file.name,
                                                style = SynapseTypography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = com.synapse.lantransfer.util.formatBytes(file.size),
                                                style = SynapseTypography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val idx = selectedFiles.indexOf(file)
                                                if (idx != -1) {
                                                    selectedFiles = selectedFiles.filterIndexed { i, _ -> i != idx }
                                                    selectedUris = selectedUris.filterIndexed { i, _ -> i != idx }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove",
                                                tint = Danger,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text("Select Files", style = SynapseTypography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { isHosting = true },
                            enabled = selectedFiles.isNotEmpty(),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent1,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Wifi,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Start Hosting", style = SynapseTypography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                // ── State: Hosting active ────────────────────────────────────
                if (localIp == null) {
                    // Loading spinner while things start up
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Accent1)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (hostingMode == HostingMode.EXISTING_HOTSPOT)
                                "Starting file server on your hotspot network..."
                            else
                                "Configuring offline hotspot...",
                            style = SynapseTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val browserUrl = "http://$localIp:8080"

                    when (hostingMode) {

                        // ── Mode B UI: existing Mobile Hotspot ───────────────
                        HostingMode.EXISTING_HOTSPOT -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Banner: hotspot already active
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color(0xFF0EA5E9).copy(alpha = 0.18f),
                                                        Color(0xFF6366F1).copy(alpha = 0.18f)
                                                    )
                                                )
                                            )
                                            .border(
                                                1.dp,
                                                Color(0xFF38BDF8).copy(alpha = 0.35f),
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Accent1.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Wifi,
                                                    contentDescription = null,
                                                    tint = Accent1,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Mobile Hotspot Detected",
                                                    style = SynapseTypography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Files are served directly to all connected devices — no setup needed!",
                                                    style = SynapseTypography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // Main instruction
                                item {
                                    Text(
                                        text = "Open this URL in a browser on your connected PC or device:",
                                        style = SynapseTypography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Big tappable URL card
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        Accent1.copy(alpha = 0.12f),
                                                        Accent2.copy(alpha = 0.12f)
                                                    )
                                                )
                                            )
                                            .border(
                                                1.5.dp,
                                                Brush.horizontalGradient(listOf(Accent1.copy(alpha = 0.5f), Accent2.copy(alpha = 0.5f))),
                                                RoundedCornerShape(24.dp)
                                            )
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Synapse URL", browserUrl))
                                                scope.launch {
                                                    showCopiedFeedback = true
                                                    delay(2000)
                                                    showCopiedFeedback = false
                                                }
                                            }
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = browserUrl,
                                                style = SynapseTypography.displayMedium.copy(fontSize = 22.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = Accent1,
                                                textAlign = TextAlign.Center
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.ContentCopy,
                                                    contentDescription = "Copy URL",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Tap to copy",
                                                    style = SynapseTypography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // QR code for convenience
                                item {
                                    val qrBitmap = remember(browserUrl) { QRCodeGenerator.generate(browserUrl) }
                                    if (qrBitmap != null) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "Or scan this QR code with any device:",
                                                style = SynapseTypography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(Color.White)
                                                    .padding(16.dp)
                                            ) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = qrBitmap.asImageBitmap(),
                                                    contentDescription = "QR Code for $browserUrl",
                                                    modifier = Modifier.size(180.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // How-to steps
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "How to download on your computer:",
                                                style = SynapseTypography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            HowToStep(number = "1", text = "Make sure your computer is connected to your phone's Mobile Hotspot Wi-Fi")
                                            HowToStep(number = "2", text = "Open any browser (Chrome, Firefox, Edge, Safari…)")
                                            HowToStep(number = "3", text = "Type the URL above in the address bar and press Enter")
                                            HowToStep(number = "4", text = "Click Download next to each file")
                                        }
                                    }
                                }

                                // Stop button
                                item {
                                    Button(
                                        onClick = { isHosting = false },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Danger,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(vertical = 14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Text("Stop Sharing", style = SynapseTypography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // ── Mode A UI: new LocalOnlyHotspot ──────────────────
                        HostingMode.NEW_HOTSPOT -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Text(
                                        text = "Step 2: Connect receiver to this Wi-Fi Network",
                                        style = SynapseTypography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Wi-Fi credentials card
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                                            .clickable { showWifiQrDialog = true }
                                            .padding(20.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Wi-Fi SSID:", style = SynapseTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(hotspotSsid ?: "", style = SynapseTypography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Password:", style = SynapseTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(hotspotPassword ?: "", style = SynapseTypography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.QrCode, contentDescription = "QR Code", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Tap to show Wi-Fi QR Code", style = SynapseTypography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }

                                    // Wi-Fi QR Dialog
                                    if (showWifiQrDialog) {
                                        val wifiQrContent = remember(hotspotSsid, hotspotPassword) {
                                            val ssid = hotspotSsid ?: ""
                                            val password = hotspotPassword ?: ""
                                            fun escape(s: String) = s.replace("\\", "\\\\")
                                                .replace(";", "\\;")
                                                .replace(",", "\\,")
                                                .replace(":", "\\:")
                                                .replace("\"", "\\\"")
                                            "WIFI:S:${escape(ssid)};T:WPA;P:${escape(password)};;"
                                        }
                                        val qrBitmap = remember(wifiQrContent) { QRCodeGenerator.generate(wifiQrContent) }

                                        AlertDialog(
                                            onDismissRequest = { showWifiQrDialog = false },
                                            title = { Text("Wi-Fi Hotspot Connection", style = SynapseTypography.displayMedium, color = MaterialTheme.colorScheme.onSurface) },
                                            text = {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "Scan this QR code with your other device's camera or system Wi-Fi scanner to connect automatically.",
                                                        style = SynapseTypography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(bottom = 16.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    if (qrBitmap != null) {
                                                        androidx.compose.foundation.Image(
                                                            bitmap = qrBitmap.asImageBitmap(),
                                                            contentDescription = "Wi-Fi QR Code",
                                                            modifier = Modifier
                                                                .size(240.dp)
                                                                .clip(RoundedCornerShape(16.dp))
                                                                .background(Color.White)
                                                                .padding(12.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                                            .padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(text = "SSID: ${hotspotSsid}", style = SynapseTypography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                                        Text(text = "Password: ${hotspotPassword}", style = SynapseTypography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { showWifiQrDialog = false }) {
                                                    Text("Close", color = Accent1)
                                                }
                                            },
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    }
                                }

                                item {
                                    Text(
                                        text = "Step 3: Receiver accesses shared files",
                                        style = SynapseTypography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                }

                                // Synapse QR code
                                item {
                                    val synapseUri = "synapse://$localIp:${activePort ?: 40889}"
                                    val qrBitmap = remember(synapseUri) { QRCodeGenerator.generate(synapseUri) }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = "Scan from Synapse app receiver:", style = SynapseTypography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                                            if (qrBitmap != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = qrBitmap.asImageBitmap(),
                                                    contentDescription = "QR Code",
                                                    modifier = Modifier
                                                        .size(200.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(Color.White)
                                                        .padding(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Browser URL card
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Synapse URL", browserUrl))
                                                scope.launch {
                                                    showCopiedFeedback = true
                                                    delay(2000)
                                                    showCopiedFeedback = false
                                                }
                                            }
                                            .padding(20.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "Or visit in ANY browser (iPhone, PC, etc):",
                                                style = SynapseTypography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            Text(
                                                text = browserUrl,
                                                style = SynapseTypography.titleMedium.copy(fontSize = 18.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = Accent1,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Tap to copy", style = SynapseTypography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                // Stop button
                                item {
                                    Button(
                                        onClick = { isHosting = false },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Danger,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(vertical = 14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Text("Stop Hosting", style = SynapseTypography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Small helper composable ──────────────────────────────────────────────────

@Composable
private fun HowToStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Accent1.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = SynapseTypography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Accent1
            )
        }
        Text(
            text = text,
            style = SynapseTypography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
