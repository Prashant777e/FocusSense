package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.PurpleAccent
import com.example.ui.viewmodel.FocusSenseViewModel
import kotlinx.coroutines.launch

enum class SelectedRoleView {
    ROLE_PICKER,
    PARENT_FLOW,
    CHILD_FLOW
}

@Composable
fun RoleSelectionAuthScreen(
    viewModel: FocusSenseViewModel,
    modifier: Modifier = Modifier
) {
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()

    var currentView by remember { mutableStateOf(SelectedRoleView.ROLE_PICKER) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Brand Header
            AuthBrandHeader(
                currentView = currentView,
                onBack = {
                    viewModel.clearAuthError()
                    currentView = SelectedRoleView.ROLE_PICKER
                }
            )

            // Body Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                AnimatedContent(
                    targetState = currentView,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "RoleViewTransition"
                ) { targetView ->
                    when (targetView) {
                        SelectedRoleView.ROLE_PICKER -> {
                            RolePickerView(
                                onSelectParent = {
                                    viewModel.clearAuthError()
                                    currentView = SelectedRoleView.PARENT_FLOW
                                },
                                onSelectChild = {
                                    viewModel.clearAuthError()
                                    currentView = SelectedRoleView.CHILD_FLOW
                                },
                                onLaunchDemoParent = {
                                    viewModel.launchDemoParent()
                                },
                                onLaunchDemoChild = {
                                    viewModel.launchDemoChild()
                                }
                            )
                        }
                        SelectedRoleView.PARENT_FLOW -> {
                            ParentAuthView(
                                isAuthLoading = isAuthLoading,
                                authError = authError,
                                serverUrl = serverUrl,
                                onUpdateServerUrl = { viewModel.updateServerUrl(it) },
                                onTestServer = { viewModel.testServerConnection() },
                                onSignIn = { email, pass ->
                                    viewModel.signInParent(email, pass)
                                },
                                onSignUp = { name, family, email, pass, pin ->
                                    viewModel.signUpParent(name, family, email, pass, pin)
                                },
                                onClearError = { viewModel.clearAuthError() }
                            )
                        }
                        SelectedRoleView.CHILD_FLOW -> {
                            ChildPairingView(
                                isAuthLoading = isAuthLoading,
                                authError = authError,
                                serverUrl = serverUrl,
                                onUpdateServerUrl = { viewModel.updateServerUrl(it) },
                                onTestServer = { viewModel.testServerConnection() },
                                onPairChild = { parentEmail, parentPass, childName, deviceName ->
                                    viewModel.pairChildDevice(parentEmail, parentPass, childName, deviceName)
                                },
                                onClearError = { viewModel.clearAuthError() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthBrandHeader(
    currentView: SelectedRoleView,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Navy800, IndigoDark)
                )
            )
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentView != SelectedRoleView.ROLE_PICKER) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to role selection",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmeraldSafe)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cloud Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logo Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(IndigoPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "FocusSense",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "FocusSense",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Context-Aware Parental Control & Habit Building",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RolePickerView(
    onSelectParent: () -> Unit,
    onSelectChild: () -> Unit,
    onLaunchDemoParent: () -> Unit,
    onLaunchDemoChild: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Who is using this phone?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Select your role to set up this device. Both devices install the exact same FocusSense app.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Android Studio Sync Notice & Project View Guide
        var isStudioTipExpanded by remember { mutableStateOf(false) }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { isStudioTipExpanded = !isStudioTipExpanded }
                .padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Syncing with Android Studio? Tap for Tips",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }
                    Text(
                        text = if (isStudioTipExpanded) "▲" else "▼",
                        fontSize = 12.sp,
                        color = IndigoPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                AnimatedVisibility(visible = isStudioTipExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "• View all files: In Android Studio's project panel (top-left), switch the view from 'Android' to 'Project' to see the backend server/ folder, .env, and architecture plans.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                        Text(
                            text = "• Autonomous edge: This Android client has embedded SQLite Room with pre-seeded demo users (Sarah, Leo, Maya) and local ML safety evaluation. It runs 100% offline without needing the external server.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                        Text(
                            text = "• Quick test: Click 'Parent Dashboard' below to immediately explore the monitoring portal with all pre-loaded features!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Instant 1-Click Demo Evaluation Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = IndigoPrimary.copy(alpha = 0.08f)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, IndigoPrimary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(IndigoPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Instant 1-Click Live Demo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                        Text(
                            text = "Explore full dashboard without typing credentials",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLaunchDemoParent,
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("demo_launch_parent_button")
                    ) {
                        Text(
                            text = "Parent Dashboard",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onLaunchDemoChild,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("demo_launch_child_button")
                    ) {
                        Text(
                            text = "Child Sentinel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "  OR SET UP THIS DEVICE  ",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Role Card: Parent
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSelectParent() }
                .border(1.dp, IndigoPrimary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .testTag("select_role_parent_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Parent Role",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "I am a Parent",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PurpleAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Admin Console",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurpleAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Monitor live location, set healthy screen schedules, and receive instant AI threat alerts.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Role Card: Child
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSelectChild() }
                .border(1.dp, EmeraldSafe.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .testTag("select_role_child_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(EmeraldSafe.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Child Device",
                        tint = EmeraldSafe,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "This is my Child's Device",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldSafeBg
                        ) {
                            Text(
                                text = "Sentinel",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSafe,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Activates background accessibility protection, app curfew windows, and safety location sharing.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Info Callout
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Android Studio Note:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "If backend files (/server/main.py, requirements.txt, .env) are not visible in the left sidebar, click the dropdown above the project tree and switch from 'Android' to 'Project' view.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentAuthView(
    isAuthLoading: Boolean,
    authError: String?,
    serverUrl: String,
    onUpdateServerUrl: (String) -> Unit,
    onTestServer: suspend () -> Pair<Boolean, String>,
    onSignIn: (email: String, pass: String) -> Unit,
    onSignUp: (name: String, family: String, email: String, pass: String, pin: String) -> Unit,
    onClearError: () -> Unit
) {
    var selectedAuthTab by remember { mutableIntStateOf(0) } // 0: Sign In, 1: Sign Up

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Sign Up fields
    var fullName by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf("") }
    var securityPin by remember { mutableStateOf("1234") }

    var showServerSettings by remember { mutableStateOf(false) }
    var serverStatusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedAuthTab,
            containerColor = Color.Transparent,
            contentColor = IndigoPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedAuthTab]),
                    color = IndigoPrimary
                )
            }
        ) {
            Tab(
                selected = selectedAuthTab == 0,
                onClick = {
                    onClearError()
                    selectedAuthTab = 0
                },
                text = { Text("Sign In", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("tab_parent_signin")
            )
            Tab(
                selected = selectedAuthTab == 1,
                onClick = {
                    onClearError()
                    selectedAuthTab = 1
                },
                text = { Text("Create Family Account", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("tab_parent_signup")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Error message banner
        if (!authError.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CoralDangerBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = CoralDanger,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = authError,
                        fontSize = 12.sp,
                        color = CoralDanger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (selectedAuthTab == 0) {
            // SIGN IN FORM
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    onClearError()
                },
                label = { Text("Parent Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signin_email_field"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onClearError()
                },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signin_password_field"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = IndigoPrimary.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        email = "parent@demo.com"
                        password = "demo"
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = IndigoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-fill Demo Credentials (parent@demo.com / demo)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = IndigoPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        onSignIn(email.trim(), password)
                    }
                },
                enabled = !isAuthLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("parent_signin_submit_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Authenticating...")
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign In as Parent", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // SIGN UP FORM
            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    onClearError()
                },
                label = { Text("Your Full Name") },
                placeholder = { Text("e.g. Sarah Connor") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_name_field"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it },
                label = { Text("Family Name (Optional)") },
                placeholder = { Text("e.g. Connor Family") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_family_field"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    onClearError()
                },
                label = { Text("Email Address") },
                placeholder = { Text("sarah@example.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_email_field"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onClearError()
                },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_password_field"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = securityPin,
                onValueChange = { if (it.length <= 6) securityPin = it },
                label = { Text("Parent Security PIN (4 digits)") },
                supportingText = { Text("Required to unlock or adjust settings on your child's phone.") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_signup_pin_field"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (fullName.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                        onSignUp(fullName.trim(), familyName.trim(), email.trim(), password, securityPin)
                    }
                },
                enabled = !isAuthLoading && fullName.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("parent_signup_submit_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Creating Account...")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Family & Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Collapsible Server Setting
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showServerSettings = !showServerSettings }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showServerSettings) "Hide Server Connection Settings" else "Advanced: Configure Server URL",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = showServerSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    var localUrl by remember { mutableStateOf(serverUrl) }
                    OutlinedTextField(
                        value = localUrl,
                        onValueChange = {
                            localUrl = it
                            onUpdateServerUrl(it)
                        },
                        label = { Text("Central Server URL (Render / Local)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IndigoPrimary)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val (success, msg) = onTestServer()
                                    serverStatusMsg = if (success) "✓ $msg" else "✗ $msg"
                                }
                            }
                        ) {
                            Text("Test Server Connection", fontSize = 12.sp)
                        }

                        if (serverStatusMsg != null) {
                            Text(
                                text = serverStatusMsg ?: "",
                                fontSize = 11.sp,
                                color = if (serverStatusMsg?.startsWith("✓") == true) EmeraldSafe else CoralDanger
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildPairingView(
    isAuthLoading: Boolean,
    authError: String?,
    serverUrl: String,
    onUpdateServerUrl: (String) -> Unit,
    onTestServer: suspend () -> Pair<Boolean, String>,
    onPairChild: (parentEmail: String, parentPass: String, childName: String, deviceName: String) -> Unit,
    onClearError: () -> Unit
) {
    var parentEmail by remember { mutableStateOf("") }
    var parentPassword by remember { mutableStateOf("") }
    var parentPassVisible by remember { mutableStateOf(false) }

    var childName by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }

    var showServerSettings by remember { mutableStateOf(false) }
    var serverStatusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Child Device Setup Header
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = EmeraldSafeBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EmeraldSafe),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Child Device Pairing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSafe
                    )
                    Text(
                        text = "Sign in with parent credentials to securely bind this child phone to your family.",
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFF065F46)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Error message banner
        if (!authError.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CoralDangerBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = CoralDanger,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = authError,
                        fontSize = 12.sp,
                        color = CoralDanger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Parent Authentication fields
        Text(
            text = "1. Parent Credentials",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = parentEmail,
            onValueChange = {
                parentEmail = it
                onClearError()
            },
            label = { Text("Parent Account Email") },
            placeholder = { Text("e.g. sarah@example.com") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("child_pairing_parent_email_field"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldSafe)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = parentPassword,
            onValueChange = {
                parentPassword = it
                onClearError()
            },
            label = { Text("Parent Account Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = if (parentPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { parentPassVisible = !parentPassVisible }) {
                    Icon(
                        imageVector = if (parentPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("child_pairing_parent_password_field"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldSafe)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Child Details
        Text(
            text = "2. Child & Device Identification",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = childName,
            onValueChange = {
                childName = it
                onClearError()
            },
            label = { Text("Child's Name") },
            placeholder = { Text("e.g. Leo (12 yrs)") },
            leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("child_pairing_name_field"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldSafe)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Nickname") },
            placeholder = { Text("e.g. Leo's Pixel 8") },
            leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("child_pairing_device_field"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldSafe)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (parentEmail.isNotBlank() && parentPassword.isNotBlank() && childName.isNotBlank()) {
                    onPairChild(parentEmail.trim(), parentPassword, childName.trim(), deviceName.trim())
                }
            },
            enabled = !isAuthLoading && parentEmail.isNotBlank() && parentPassword.isNotBlank() && childName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("child_pairing_submit_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isAuthLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Pairing Device...")
            } else {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pair Device & Activate Sentinel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Collapsible Server Setting
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showServerSettings = !showServerSettings }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showServerSettings) "Hide Server Connection Settings" else "Advanced: Configure Server URL",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = showServerSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    var localUrl by remember { mutableStateOf(serverUrl) }
                    OutlinedTextField(
                        value = localUrl,
                        onValueChange = {
                            localUrl = it
                            onUpdateServerUrl(it)
                        },
                        label = { Text("Central Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldSafe)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val (success, msg) = onTestServer()
                                    serverStatusMsg = if (success) "✓ $msg" else "✗ $msg"
                                }
                            }
                        ) {
                            Text("Test Server Connection", fontSize = 12.sp)
                        }

                        if (serverStatusMsg != null) {
                            Text(
                                text = serverStatusMsg ?: "",
                                fontSize = 11.sp,
                                color = if (serverStatusMsg?.startsWith("✓") == true) EmeraldSafe else CoralDanger
                            )
                        }
                    }
                }
            }
        }
    }
}
