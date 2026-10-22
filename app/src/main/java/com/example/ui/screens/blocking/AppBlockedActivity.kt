package com.example.ui.screens.blocking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.FocusSenseApplication
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MyApplicationTheme

class AppBlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_RULE_NAME = "extra_rule_name"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_END_TIME = "extra_end_time"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "This Application"
        val ruleName = intent.getStringExtra(EXTRA_RULE_NAME) ?: "Curfew Schedule"
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Restricted Content"
        val endTime = intent.getStringExtra(EXTRA_END_TIME) ?: "End of Period"

        setContent {
            MyApplicationTheme {
                AppBlockedScreen(
                    appName = appName,
                    packageName = blockedPkg,
                    ruleName = ruleName,
                    category = category,
                    endTime = endTime,
                    onGoHome = { navigateHome() },
                    onCallHelpline = { callCrisisHelpline() },
                    onSmsHelpline = { smsCrisisHelpline() },
                    onParentUnlock = { pin ->
                        verifyAndUnlock(blockedPkg, pin)
                    }
                )
            }
        }
    }

    private fun navigateHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun callCrisisHelpline() {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:18005990019")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun smsCrisisHelpline() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:+919152987821?body=HELP")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun verifyAndUnlock(packageName: String, pin: String): Boolean {
        val app = application as? FocusSenseApplication ?: return false
        val expectedPin = "1234" // Default parent override PIN
        if (pin.trim() == expectedPin) {
            app.repository.temporarilyUnlockApp(packageName, 15)
            finish()
            return true
        }
        return false
    }
}

@Composable
fun AppBlockedScreen(
    appName: String,
    packageName: String,
    ruleName: String,
    category: String,
    endTime: String,
    onGoHome: () -> Unit,
    onCallHelpline: () -> Unit = {},
    onSmsHelpline: () -> Unit = {},
    onParentUnlock: (String) -> Boolean
) {
    // Intercept back button to always send to Home instead of falling into blocked app
    BackHandler {
        onGoHome()
    }

    val catUpper = category.uppercase()

    val isSelfHarmCrisis = catUpper.contains("SELF-HARM") || catUpper.contains("CRISIS") || catUpper.contains("SUICIDE") || ruleName.contains("Critical", ignoreCase = true)
    val isGrooming = catUpper.contains("GROOMING") || catUpper.contains("STRANGER") || catUpper.contains("PREDATORY")
    val isBullying = catUpper.contains("BULLYING") || catUpper.contains("HARASSMENT")
    val isAdult = catUpper.contains("ADULT") || catUpper.contains("EXPLICIT") || catUpper.contains("PORN")
    val isScam = catUpper.contains("SCAM") || catUpper.contains("PHISHING")
    val isViolence = catUpper.contains("VIOLENCE") || catUpper.contains("WEAPONS")

    val displayTitle = when {
        isSelfHarmCrisis -> "SELF-HARM & CRISIS INTERVENTION"
        isGrooming -> "PREDATORY GROOMING & STRANGER RISK"
        isBullying -> "CYBERBULLYING & HARASSMENT RESTRICTION"
        isAdult -> "EXPLICIT & ADULT CONTENT RESTRICTION"
        isScam -> "SCAM & PHISHING PROTECTION"
        isViolence -> "VIOLENCE & WEAPONS RESTRICTION"
        else -> "CONTENT ACCESS RESTRICTED"
    }

    val displaySubtitle = when {
        isSelfHarmCrisis -> "We care about you. FocusSense paused $appName because distress or self-harm keywords were detected."
        isGrooming -> "FocusSense paused $appName because suspicious stranger interaction or secrecy pressure was detected."
        isBullying -> "FocusSense paused $appName because toxic comments, insults, or harassment language was detected."
        isAdult -> "FocusSense paused $appName because explicit, adult, or age-restricted material was detected."
        isScam -> "FocusSense paused $appName because potential scam, phishing, or financial fraud was detected."
        isViolence -> "FocusSense paused $appName because physical violence or weapons language was detected."
        else -> "$appName is currently paused by FocusSense safety controls."
    }

    val iconHeader = when {
        isSelfHarmCrisis -> Icons.Default.Favorite
        isGrooming -> Icons.Default.Shield
        isBullying -> Icons.Default.Warning
        isAdult -> Icons.Default.Block
        isScam -> Icons.Default.Security
        isViolence -> Icons.Default.Warning
        else -> Icons.Default.Block
    }

    val iconColor = when {
        isSelfHarmCrisis -> Color(0xFFD32F2F)
        isGrooming -> Color(0xFFE65100)
        isBullying -> Color(0xFFC2185B)
        isAdult -> Color(0xFF7B1FA2)
        isScam -> Color(0xFFD84315)
        isViolence -> Color(0xFFB71C1C)
        else -> CoralDanger
    }

    val iconBgColor = when {
        isSelfHarmCrisis -> Color(0xFFFFEBEE)
        isGrooming -> Color(0xFFFFF3E0)
        isBullying -> Color(0xFFFCE4EC)
        isAdult -> Color(0xFFF3E5F5)
        isScam -> Color(0xFFFFF3E0)
        isViolence -> Color(0xFFFFEBEE)
        else -> CoralDangerBg
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // High-Visibility Warning Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconHeader,
                    contentDescription = displayTitle,
                    tint = iconColor,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = displayTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displaySubtitle,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isSelfHarmCrisis) {
                // Urgent 24/7 Crisis Support Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tele-MANAS & Kiran Helpline (India)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFC62828)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You are not alone. Free, confidential mental health and crisis support is available 24/7 across India. Reach out right now:",
                            fontSize = 13.sp,
                            color = Color(0xFF333333),
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Call Helpline Button
                        Button(
                            onClick = onCallHelpline,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Call 1800-599-0019 (24/7 Toll-Free)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // SMS Helpline Button
                        OutlinedButton(
                            onClick = onSmsHelpline,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Contact AASRA (+91-9152987821)",
                                fontSize = 13.sp,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }
            } else if (isGrooming) {
                // Predatory Grooming & Stranger Safety Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Online Stranger Safety Rules",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFE65100)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• Never share your address, school name, phone number, or photos with online strangers.",
                            fontSize = 13.sp,
                            color = Color(0xFF4E342E),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• No safe friend or adult will ever ask you to keep secrets from your parents.",
                            fontSize = 13.sp,
                            color = Color(0xFF4E342E),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Never agree to meet anyone offline without parent permission and accompaniment.",
                            fontSize = 13.sp,
                            color = Color(0xFF4E342E),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else if (isBullying) {
                // Cyberbullying & Harassment Support Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFC2185B),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cyberbullying Support Guidance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFC2185B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• You don't have to deal with toxic comments or insults alone.",
                            fontSize = 13.sp,
                            color = Color(0xFF880E4F),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Consider muting, blocking, or reporting toxic accounts.",
                            fontSize = 13.sp,
                            color = Color(0xFF880E4F),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Talk with a trusted parent, teacher, or adult about what happened.",
                            fontSize = 13.sp,
                            color = Color(0xFF880E4F),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else if (isAdult) {
                // Explicit Content Restriction Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = Color(0xFF7B1FA2),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Age-Ineligible Material Boundary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF7B1FA2)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "FocusSense restricts adult or explicit material to maintain a safe, healthy digital environment for young users.",
                            fontSize = 13.sp,
                            color = Color(0xFF4A148C),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else if (isScam) {
                // Scam & Phishing Protection Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFD84315),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Financial & Account Security Warning",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFD84315)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• Never share passwords, PINs, credit card numbers, or OTP codes.",
                            fontSize = 13.sp,
                            color = Color(0xFFBF360C),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Beware of offers promising 'free game coins', 'unlimited robux', or 'free giveaways'.",
                            fontSize = 13.sp,
                            color = Color(0xFFBF360C),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // Schedule & Rule Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = ruleName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Category: $category",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (endTime.isNotBlank() && endTime != "End of Period") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Restricted Until: $endTime",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CoralDanger
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Take a break, focus on your studies, or spend quality time with family.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Return Home Button
            Button(
                onClick = onGoHome,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("app_blocked_go_home_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Return to Home Screen",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Parent Override Button
            OutlinedButton(
                onClick = {
                    pinText = ""
                    pinError = null
                    showPinDialog = true
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("app_blocked_parent_unlock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parent PIN Override (Unlock 15m)",
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Text(text = "Parent Authorization")
            },
            text = {
                Column {
                    Text(
                        text = "Enter your Parent PIN to grant a 15-minute temporary access pass for $appName.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = {
                            if (it.length <= 6) {
                                pinText = it
                                pinError = null
                            }
                        },
                        label = { Text("Parent PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError != null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parent_pin_override_input")
                    )
                    pinError?.let { err ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = onParentUnlock(pinText)
                        if (!success) {
                            pinError = "Incorrect PIN. Default is 1234."
                        } else {
                            showPinDialog = false
                        }
                    },
                    modifier = Modifier.testTag("parent_pin_override_confirm")
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
