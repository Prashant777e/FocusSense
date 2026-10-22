package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.ActiveAppBlockInfo
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy900
import com.example.ui.theme.PurpleAccent

@Composable
fun AppRestrictionOverlay(
    blockInfo: ActiveAppBlockInfo,
    onDismiss: () -> Unit,
    onParentOverrideRequested: () -> Unit
) {
    val isBlocked = blockInfo.isBlocked

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isBlocked) listOf(
                        Navy900.copy(alpha = 0.95f),
                        IndigoDark.copy(alpha = 0.98f)
                    ) else listOf(
                        Color(0xFF064E3B).copy(alpha = 0.95f),
                        Color(0xFF0F172A).copy(alpha = 0.98f)
                    )
                )
            )
            .padding(24.dp)
            .testTag("app_restriction_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Focus Shield Graphic
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(if (isBlocked) IndigoPrimary.copy(alpha = 0.2f) else EmeraldSafe.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Default.HourglassTop else Icons.Default.CheckCircle,
                        contentDescription = if (isBlocked) "Focus Shield" else "Access Allowed",
                        tint = if (isBlocked) IndigoPrimary else EmeraldSafe,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (isBlocked) "Focus Mode Active" else "App Access Allowed",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isBlocked) AmberWarning.copy(alpha = 0.15f) else EmeraldSafe.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isBlocked) "Curfew: ${blockInfo.ruleName}" else "Curfew Check: Permitted",
                        color = if (isBlocked) AmberWarning else EmeraldSafe,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isBlocked) {
                        "${blockInfo.restrictedAppName} is currently restricted by your parent schedule to build great focus habits."
                    } else {
                        "${blockInfo.restrictedAppName} is currently allowed. No active schedule curfew or instant lock is restricting it right now."
                    },
                    fontSize = 14.sp,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Time Remaining Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isBlocked) Icons.Default.School else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBlocked) "Curfew window ends at ${blockInfo.endTime}" else "Status: Open to launch",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isBlocked) IndigoPrimary else EmeraldSafe),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_restriction_button")
                ) {
                    Text(if (isBlocked) "Return to Study" else "OK, Got It", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                if (isBlocked) {
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onParentOverrideRequested,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("parent_override_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Parent Unlock (PIN)", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
