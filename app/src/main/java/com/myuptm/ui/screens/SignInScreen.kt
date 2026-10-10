package com.myuptm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myuptm.R

// UPTM campus-photo backdrop: the building's own accent stripes (red + blue)
// drive the small brand moments on top of a neutral dark scrim, so the screen
// stays readable with any user palette (dynamic theme included).
private val ScrimBase = Color(0xFF0B1220)
private val BrandRed = Color(0xFFD22630)
private val BrandBlue = Color(0xFF1E88E5)
private val ErrorOnDark = Color(0xFFFFB4AB)

@Composable
fun SignInScreen(onSignInClick: () -> Unit,
                 errorMessage: String? = null,
                 isLoading: Boolean = false,
                 // Sprint 13: pending-account approval — spinner + email shown
                 // while the admin has not assigned a role yet.
                 isWaitingApproval: Boolean = false,
                 waitingEmail: String? = null,
                 onCancelWait: (() -> Unit)? = null,
                 // Sprint 14: Help entry on the login screen.
                 onHelpClick: () -> Unit = {}
                 ) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed campus photo (decorative).
        Image(
            painter = painterResource(R.drawable.uptm_building),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Readability scrim: gentle at the top (status bar), strong at the
        // bottom where the headline and the action card sit.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.35f),
                        0.20f to Color.Transparent,
                        0.55f to ScrimBase.copy(alpha = 0.55f),
                        1.0f to ScrimBase.copy(alpha = 0.92f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Glass campus tag.
            Surface(
                color = Color.White.copy(alpha = 0.14f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    text = "UNIVERSITI POLY-TECH MALAYSIA",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "MyUPTM",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Brand accent echoing the red/blue stripes on the building.
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(listOf(BrandRed, BrandBlue)),
                        RoundedCornerShape(2.dp)
                    )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Sign in with your university account",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Glass action card — everything interactive lives here.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.13f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onSignInClick,
                        enabled = !isLoading && !isWaitingApproval,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F),
                            disabledContainerColor = Color.White.copy(alpha = 0.30f),
                            disabledContentColor = Color.White.copy(alpha = 0.9f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF1F1F1F),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_google),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = ErrorOnDark,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    if (isWaitingApproval) {
                        // The moment the admin flips this account to
                        // approved = true, the app resumes itself (no retry).
                        Box(
                            modifier = Modifier
                                .padding(top = 20.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 20.dp)
                                .size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Waiting for account approval",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = waitingEmail.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "The admin must approve this account in the Dashboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (onCancelWait != null) {
                            OutlinedButton(
                                onClick = onCancelWait,
                                modifier = Modifier.padding(top = 16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Cancel and sign out")
                            }
                        }
                    }
                }
            }

            // Sprint 14: Help entry on the login screen too (rubric: Help menu).
            TextButton(
                onClick = onHelpClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            ) {
                Text("Help & guide", color = Color.White.copy(alpha = 0.85f))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
