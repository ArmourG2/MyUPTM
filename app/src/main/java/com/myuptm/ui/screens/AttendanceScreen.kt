package com.myuptm.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.myuptm.viewmodel.AttendanceCheckInViewModel
import kotlinx.coroutines.launch

// Sprint 11: REAL attendance verification — the student either scans the
// lecturer's QR (payload = the 6-digit session code) or types the PIN; both run
// the same check against the active 5-minute Firestore session. Scanning uses
// the zxing embedded Activity (no in-app camera preview to maintain).
@Composable
fun AttendanceScreen() {
    var isQrMode by remember { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val checkInViewModel: AttendanceCheckInViewModel = viewModel()
    val uiState by checkInViewModel.uiState.collectAsState()

    // zxing's CaptureActivity handles its own camera permission + preview UI;
    // we only receive the decoded payload here.
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ScanContract()
    ) { result ->
        result.contents?.let { contents -> checkInViewModel.verify(contents) }
    }

    fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan the attendance code shown by your lecturer")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
        scanLauncher.launch(options)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Attendance Verification", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "The code is only valid for 5 minutes after your lecturer opens the session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Animated centre area: QR and PIN swap in the same slot
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = uiState.verifiedSession != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    VerifiedPanel(
                        displayCode = uiState.verifiedSession?.displayCode.orEmpty(),
                        verifiedAt = uiState.verifiedAt,
                        onDone = { checkInViewModel.consumeResult() }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.verifiedSession == null && isQrMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(84.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Scan the QR shown on the lecturer's screen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { launchScanner() }, enabled = !uiState.isVerifying) {
                            if (uiState.isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text("Scan QR code")
                        }
                    }
                }

                AnimatedVisibility(
                    visible = uiState.verifiedSession == null && !isQrMode,
                    enter = fadeIn() + slideInVertically { it / 4 } + expandVertically(),
                    exit = fadeOut() + slideOutVertically { it / 4 } + shrinkVertically()
                ) {
                    PinEntry(
                        pin = pin,
                        onPinChange = { pin = it },
                        isVerifying = uiState.isVerifying,
                        onSubmit = {
                            checkInViewModel.verify(pin)
                            pin = ""
                        }
                    )
                }

                uiState.error?.let { message ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            AttendanceModeToggle(isQrMode = isQrMode, onModeChange = { isQrMode = it })
            Spacer(Modifier.height(16.dp))

            // Recent verifications (proof panel) — newest first, capped at 3.
            if (uiState.myCheckIns.isNotEmpty() && uiState.verifiedSession == null) {
                Text(
                    "Recent verifications",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                uiState.myCheckIns.take(3).forEach { checkIn ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            checkIn.displayCode,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(checkIn.verifiedAt)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
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
                    java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
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
            onValueChange = { value ->
                // Self-filtering input: digits only, max 6
                if (value.length <= 6 && value.all { it.isDigit() }) onPinChange(value)
            },
            label = { Text("6-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            singleLine = true,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .focusRequester(focusRequester)
        )
        Button(onClick = onSubmit, enabled = pin.length == 6 && !isVerifying) {
            Text(if (isVerifying) "Verifying..." else "Verify")
        }
    }
}
