package com.myuptm.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.myuptm.viewmodel.AttendanceCheckInViewModel
import com.myuptm.viewmodel.AttendanceCheckInViewModel.CheckInUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.ui.platform.LocalLocale

// Sprint 13: the attendance tab IS the scanner. The camera preview is embedded
// (CameraX + ML Kit) and starts the moment the student opens the tab in QR
// mode — no separate zxing Activity, the app never leaves the screen. The
// backend check-in path (Sprint 11) is unchanged: payload/typed PIN →
// AttendanceCheckInViewModel.verify against the 5-minute Firestore session.
//
// Sprint 13 light-mode cleanup: the immersive treatment (dark scrim, white
// title, dark toggle/strip pills) exists ONLY to stay readable over a live
// camera feed — so it is applied only when the feed is on screen. Every
// non-camera state (PIN entry, permission panel, verified panel) renders a
// themed header on the plain surface instead, which removes the "floating
// shadow" effect seen in light mode.
@Composable
fun AttendanceScreen() {
    var isQrMode by remember { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val checkInViewModel: AttendanceCheckInViewModel = viewModel()
    val uiState by checkInViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission lives HERE (not in the stage) because the header styling and
    // even the status-bar icon colour depend on whether the feed is really on.
    var permissionState by remember { mutableStateOf(PermissionState.PENDING) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }
    // Auto-open (owner decision, Sprint 12): the camera permission is requested
    // the moment the student lands on the tab so the live preview starts with
    // zero taps. Persisted grant is reused; a denial falls to the retry panel.
    LaunchedEffect(Unit) {
        permissionState = if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) PermissionState.GRANTED else PermissionState.PENDING
        if (permissionState == PermissionState.PENDING) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val feedLive =
        permissionState == PermissionState.GRANTED && isQrMode && uiState.verifiedSession == null

    // Over the feed the status bar shows WHITE icons; otherwise the theme's
    // own choice applies (dark icons on light surface, light on dark). The
    // theme's value is snapshotted and restored when the feed hides or this
    // screen leaves composition.
    val view = LocalView.current
    DisposableEffect(feedLive) {
        val window = (view.context as android.app.Activity).window
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        val themeDefault = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = if (feedLive) true else themeDefault
        onDispose { controller.isAppearanceLightStatusBars = themeDefault }
    }

    // Zero insets on this scaffold: the content area already accounts for the
    // status bar, and the live feed must reach under it edge-to-edge. Overlays
    // that must clear system UI apply their own statusBarsPadding.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            ScannerStage(
                modifier = Modifier.fillMaxSize(),
                isQrMode = isQrMode,
                onModeChange = {
                    isQrMode = it
                    if (it) checkInViewModel.consumeResult() // clear stale errors/mocks
                },
                state = uiState,
                permissionState = permissionState,
                onRetryPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                pin = pin,
                onPinChange = { value ->
                    // Self-filtering input: digits only, max 6
                    if (value.length <= 6 && value.all { it.isDigit() }) pin = value
                },
                onPinSubmit = {
                    checkInViewModel.verify(pin)
                    pin = ""
                },
                onScanCode = { payload -> checkInViewModel.verify(payload) },
                onDone = { checkInViewModel.consumeResult() },
                recent = uiState.myCheckIns.take(3),
                showRecent = uiState.myCheckIns.isNotEmpty() && uiState.verifiedSession == null
            )

            // Immersive header. The dark gradient + white text render only when
            // the camera feed is live underneath; otherwise the header matches
            // the theme, so light mode never shows a "shadow" with no camera.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (feedLive) Modifier.background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.75f),
                                1f to Color.Transparent
                            )
                        ) else Modifier
                    )
                    .statusBarsPadding()
                    .padding(top = 12.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Attendance Verification",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (feedLive) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "The code is only valid for 5 minutes after your lecturer opens the session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feedLive) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }

    // Errors (expired code, unknown code, network) surface as a snackbar so the
    // camera keeps running underneath — the student can scan the next code
    // without re-entering anything.
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }
}

@Composable
private fun ScannerStage(
    modifier: Modifier = Modifier,
    isQrMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    state: CheckInUiState,
    permissionState: PermissionState,
    onRetryPermission: () -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    onPinSubmit: () -> Unit,
    onScanCode: (String) -> Unit,
    onDone: () -> Unit,
    recent: List<com.myuptm.domain.model.AttendanceCheckIn>,
    showRecent: Boolean
) {
    val hasPermission = permissionState == PermissionState.GRANTED
    // "Over the feed" = live camera fills the whole stage. All furniture (dark
    // pills, scrim, white text) belongs ONLY to this state; anything else is a
    // themed panel on the surface colour.
    val overFeed = hasPermission && isQrMode && state.verifiedSession == null
    Box(
        modifier = modifier
            .background(if (overFeed) Color.Transparent else MaterialTheme.colorScheme.surface)
    ) {
        when {
            // Green confirmation — replaces the viewfinder; camera is unbound.
            state.verifiedSession != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    VerifiedPanel(
                        displayCode = state.verifiedSession.displayCode,
                        verifiedAt = state.verifiedAt,
                        onDone = onDone
                    )
                }
            }

            // PIN mode — the camera is NOT bound (no indicator light, no drain).
            !isQrMode -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        ImageVector.vectorResource(com.myuptm.R.drawable.ic_qr_code_scanner),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    PinEntry(
                        pin = pin,
                        onPinChange = onPinChange,
                        isVerifying = state.isVerifying,
                        onSubmit = onPinSubmit
                    )
                }
            }

            // QR mode + permission granted — the LIVE scanner (stays mounted
            // through the verifying window; [paused] gates frame analysis).
            hasPermission -> CameraScanner(
                onCode = onScanCode,
                active = !state.isVerifying,
                modifier = Modifier.fillMaxSize()
            )

            // Permission denied — retry panel; PIN stays one tap away.
            permissionState == PermissionState.DENIED -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        ImageVector.vectorResource(com.myuptm.R.drawable.ic_no_photography),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Camera access is needed to scan the QR", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Or switch to the PIN below and type the code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetryPermission) {
                        Text("Retry camera")
                    }
                }
            }

            // Waiting on the permission dialog.
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // Viewfinder furniture — brackets + caption, only while scanning camera-side.
        if (overFeed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CornerBracketFrame(size = 240.dp, modifier = Modifier.size(240.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Point camera at QR code",
                    // Soft dark shadow keeps the caption readable on blown-out
                    // bright frames (light mode over white walls/lights).
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f
                        )
                    ),
                    color = Color.White
                )
            }
        }

        // Recent verifications (proof strip) — floating chip rows. Dark
        // translucent over the feed; themed card everywhere else.
        if (showRecent) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (overFeed) Color.Black.copy(alpha = 0.62f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "Recent verifications",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (overFeed) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                recent.forEach { checkIn ->
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (overFeed) Color(0xFFA8E06B)
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            checkIn.displayCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overFeed) Color.White
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            java.text.SimpleDateFormat("dd MMM, HH:mm", LocalLocale.current.platformLocale)
                                .format(java.util.Date(checkIn.verifiedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overFeed) Color.White.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Mode toggle rides the bottom edge. The DARK pill wrapper exists to
        // keep the unselected segment readable over a bright camera frame, so
        // it is applied only over the feed; elsewhere the bare segmented
        // control sits on its own surface colour. Both get the nav-bar inset.
        if (state.verifiedSession == null) {
            if (overFeed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(5.dp)
                ) {
                    AttendanceModeToggle(isQrMode = isQrMode, onModeChange = onModeChange)
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // The app-level bottom nav already consumes the
                        // navigation-bar inset, so no extra bar padding here —
                        // just a small breathing gap.
                        .padding(bottom = 8.dp)
                ) {
                    AttendanceModeToggle(isQrMode = isQrMode, onModeChange = onModeChange)
                }
            }
        }
    }
}

// The live decoder: binds Preview + ImageAnalysis to the owner's lifecycle for
// as long as this composable exists; ML Kit scans each latest QR frame-class
// image (keep-only-latest) and pushes payloads up through [onCode]. Governor:
// a 2.5 s cooldown between decodes so one frame never double-fires a verify,
// plus a pause flag while a verification is in flight.
//
// Frame watchdog (Sprint 12): MIUI's camera HAL stalls the stream mid-run —
// device-log verified: the AF stats node spams errors until fence syncs fail,
// then CameraX gets fatal device errors (code 4) and frames stop arriving
// (one washed-out frame stays on screen; the camera indicator stays on).
// Recovery = full unbind/rebind, the same thing the PIN-toggle workaround
// does by hand. The analyzer timestamps EVERY arriving frame; a watchdog
// coroutine marks the scanner dead when nothing arrives for
// WATCHDOG_TIMEOUT_MS while it should be live → the outer composable
// unmounts the body (spinner), waits REBIND_GAP_MS for the old graph to
// fully close, then remounts it fresh (the gap avoids the "OPEN +
// GRAPH_STARTED" rebind race seen in the logs). Retry budget self-heals
// after a healthy stretch so a later stall still auto-recovers.
@Composable
private fun CameraScanner(
    onCode: (String) -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    // Remount key: bumped by the stall watchdog → DisposableEffect/LaunchedEffect
    // re-run from scratch (fresh bind = fresh streams, exactly like the manual
    // PIN toggle recovery).
    var rebindKey by remember { mutableStateOf(0) }
    var rebinding by remember { mutableStateOf(false) }
    var stalled by remember { mutableStateOf(false) }
    var stallCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // Self-healing budget: a healthy stretch means the current graph is good —
    // clear the retry counter so a LATER stall auto-recovers again.
    LaunchedEffect(rebindKey, rebinding) {
        if (!rebinding) {
            delay(30_000L)
            stallCount = 0
        }
    }

    if (rebinding) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else key(rebindKey) {
        // Inner scanner body — everything below remounts on rebindKey bump.
        ScannerBody(
            onCode = onCode,
            active = active,
            generation = rebindKey,
            onStalled = {
                if (stallCount < MAX_REBINDS && !rebinding) {
                    stallCount++
                    scope.launch {
                        // Unmount first (spinner), give CameraX a beat to fully
                        // close the dead graph, THEN mount fresh — no overlap.
                        rebinding = true
                        delay(REBIND_GAP_MS)
                        rebindKey++
                        rebinding = false
                    }
                } else {
                    stalled = true // give up: surface a manual-restart hint
                }
            },
            modifier = modifier
        )
    }

    if (stalled) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Camera stalled", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    stalled = false
                    stallCount = 0
                    rebindKey++
                }
            ) { Text("Restart camera") }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun ScannerBody(
    onCode: (String) -> Unit,
    active: Boolean,
    generation: Int,
    onStalled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    // Keep a main-thread-safe handle to the provider so dispose never needs
    // the blocking get() (that hung during rapid tab toggles → frozen preview).
    var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val paused = remember { AtomicBoolean(false) }
    val lastDecode = remember { AtomicLong(0L) }
    // Touched by EVERY arriving frame (not gated ones) — the stall watchdog's
    // liveness signal. Init = bind time so a never-started stream trips it too.
    val lastFrameSeen = remember { AtomicLong(System.currentTimeMillis()) }

    // The analysis gate follows verification: paused while [isVerifying] runs,
    // re-armed when verification settles (error paths included) so a rejected
    // scan doesn't leave the scanner dead.
    LaunchedEffect(active) { paused.set(!active) }

    // Stall watchdog: while the camera SHOULD be streaming (QR mode, not
    // verifying), wake up every 2 s and check frame freshness. A gap longer
    // than WATCHDOG_TIMEOUT_MS means the HAL stopped delivering frames →
    // report to the parent for a full remount.
    LaunchedEffect(active, generation) {
        while (isActive) {
            delay(1_000L)
            if (!active || paused.get()) continue
            val gap = System.currentTimeMillis() - lastFrameSeen.get()
            if (gap > WATCHDOG_TIMEOUT_MS) {
                onStalled()
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            paused.set(true)
            // Cancel any undelivered surface contract — CameraX otherwise waits
            // forever for a surface from a screen that no longer exists.
            surfaceRequest?.willNotProvideSurface()
            surfaceRequest = null
            runCatching { providerRef?.unbindAll() }
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        providerRef = provider
        // Fresh run: clear a stale decode cooldown so re-entry isn't blocked.
        lastDecode.set(0L)
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(executor) { proxy ->
                    // Liveness FIRST — every arriving frame (even gated ones)
                    // feeds the watchdog before any early-return.
                    lastFrameSeen.set(System.currentTimeMillis())
                    try {
                        if (paused.get()) return@setAnalyzer
                        val now = System.currentTimeMillis()
                        if (now - lastDecode.get() < DECODE_COOLDOWN_MS) return@setAnalyzer
                        if (proxy.image != null) {
                            val input = InputImage.fromMediaImage(
                                proxy.image!!,
                                proxy.imageInfo.rotationDegrees
                            )
                            val codes = runCatching { Tasks.await(scanner.process(input)) }
                                .getOrDefault(emptyList())
                            val payload = codes.firstOrNull()?.rawValue
                            lastDecode.set(System.currentTimeMillis())
                            if (!payload.isNullOrBlank()) {
                                paused.set(true) // one scan = one check-in
                                lastDecode.set(0L) // clear the cooldown gate
                                executor.execute { onCode(payload) }
                                return@setAnalyzer
                            }
                        }
                    } finally {
                        proxy.close()
                    }
                }
            }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }
        // Arm the gate AFTER the camera is live; sync with [active] (the paused
        // flag above is only the verification gate).
        paused.set(!active)
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val request = surfaceRequest
        if (request != null) {
            // EMBEDDED (TextureView): the preview composites through Compose's
            // draw tree, so it fades with the screen during nav transitions —
            // EXTERNAL/SurfaceView (the default) punches a hole and ghosts
            // through the fading destination (the transition bug from testing).
            CameraXViewfinder(
                surfaceRequest = request,
                implementationMode = ImplementationMode.EMBEDDED,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

private const val DECODE_COOLDOWN_MS = 2_500L

// Stall watchdog: 2.5 s without a delivered frame = the stream is dead
// (healthy streams deliver 15–30 fps; the MIUI stall shows one frozen
// frame and nothing further). Retry budget stops a hot rebind loop if the
// HAL is genuinely wedged — after 3 rapid remounts the UI asks for a manual
// restart. REBIND_GAP_MS parks on a spinner while the dead graph closes,
// avoiding the "OPEN + GRAPH_STARTED" overlap race seen in the logs.
private const val WATCHDOG_TIMEOUT_MS = 2_500L
private const val MAX_REBINDS = 3
private const val REBIND_GAP_MS = 300L

private enum class PermissionState { PENDING, GRANTED, DENIED }

// Corner-bracket viewfinder, mock-referenced: four L strokes on a transparent
// canvas, over a soft dark under-stroke so brackets stay visible even against
// a blown-out bright frame in light mode.
@Composable
private fun CornerBracketFrame(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = 5.dp.toPx()
        val arm = 40.dp.toPx()
        val accent = Color.White.copy(alpha = 0.95f)
        val shadow = Color.Black.copy(alpha = 0.35f)
        val w = this.size.width
        val h = this.size.height

        fun armPoints(x1: Float, y1: Float, x2: Float, y2: Float) {
            // Dark hairline under a white main stroke = works on any feed.
            drawLine(shadow, androidx.compose.ui.geometry.Offset(x1, y1), androidx.compose.ui.geometry.Offset(x2, y2), stroke + 4f, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(x1, y1), androidx.compose.ui.geometry.Offset(x2, y2), stroke, StrokeCap.Round)
        }

        // Top-left: horizontal arm + vertical arm
        armPoints(0f, 0f, arm, 0f)
        armPoints(0f, 0f, 0f, arm)
        // Top-right
        armPoints(w, 0f, w - arm, 0f)
        armPoints(w, 0f, w, arm)
        // Bottom-left
        armPoints(0f, h, arm, h)
        armPoints(0f, h, 0f, h - arm)
        // Bottom-right
        armPoints(w, h, w - arm, h)
        armPoints(w, h, w, h - arm)
    }
}

// Green confirmation card — the attendance was recorded.
@Composable
private fun VerifiedPanel(
    displayCode: String,
    verifiedAt: Long?,
    onDone: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Attendance verified",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (displayCode.isNotBlank()) {
                Text(
                    displayCode,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            verifiedAt?.let {
                Text(
                    java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", LocalLocale.current.platformLocale)
                        .format(java.util.Date(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun AttendanceModeToggle(isQrMode: Boolean, onModeChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.width(200.dp)) {
        SegmentedButton(
            selected = isQrMode,
            onClick = { onModeChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("QR") }
        SegmentedButton(
            selected = !isQrMode,
            onClick = { onModeChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("PIN") }
    }
}

@Composable
private fun PinEntry(
    pin: String,
    onPinChange: (String) -> Unit,
    isVerifying: Boolean,
    onSubmit: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("6-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSubmit() }),
            singleLine = true,
            modifier = Modifier
                .width(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400) 200.dp else 220.dp)
                .focusRequester(focusRequester)
        )
        Button(
            onClick = onSubmit,
            enabled = pin.length == 6 && !isVerifying
        ) {
            Text(if (isVerifying) "Verifying..." else "Verify")
        }
    }
}
