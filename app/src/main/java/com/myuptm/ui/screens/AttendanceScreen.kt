package com.myuptm.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.domain.model.LetterStatus
import com.myuptm.ui.components.QrViewfinder
import com.myuptm.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen() {
    var isQrMode by remember { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Sprint 8 Task 4 (part 2): absence letters now create a real Firestore record.
    val attendanceViewModel: AttendanceViewModel = viewModel()
    val attendanceState by attendanceViewModel.uiState.collectAsState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { attendanceViewModel.submitAbsenceLetter(it, className = null) }
    }

    LaunchedEffect(attendanceState.submitSuccess) {
        if (attendanceState.submitSuccess) {
            snackbarHostState.showSnackbar("Absence letter submitted — pending lecturer review")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Attendance Verification", style = MaterialTheme.typography.headlineSmall)

            Button(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                enabled = !attendanceState.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (attendanceState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (attendanceState.isSubmitting) "Uploading Absence Letter..." else "Upload Absence Letter (PDF)")
            }

            attendanceState.submitError?.let { err ->
                Text(
                    "Error: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Sprint 8: the student sees the review status of their letters.
            if (attendanceState.myLetters.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    attendanceState.myLetters.take(3).forEach { letter ->
                        Text(
                            "Letter ${letter.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (letter.status) {
                                LetterStatus.APPROVED -> MaterialTheme.colorScheme.primary
                                LetterStatus.DECLINED -> MaterialTheme.colorScheme.error
                                LetterStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

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
                    visible = isQrMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        QrViewfinder(Modifier.size(260.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Point camera at QR code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isQrMode,
                    enter = fadeIn() + slideInVertically { it / 4 } + expandVertically(),
                    exit = fadeOut() + slideOutVertically { it / 4 } + shrinkVertically()
                ) {
                    PinEntry(
                        pin = pin,
                        onPinChange = { pin = it },
                        onSubmit = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Attendance recorded (mock)")
                            }
                        }
                    )
                }
            }

            AttendanceModeToggle(isQrMode = isQrMode, onModeChange = { isQrMode = it })
            Spacer(Modifier.height(32.dp))
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
private fun PinEntry(pin: String, onPinChange: (String) -> Unit, onSubmit: () -> Unit) {
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
        Button(onClick = onSubmit, enabled = pin.length == 6) { Text("Verify") }
    }
}