package com.example.hivechat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.hivechat.ui.theme.*

private val BrownBorder = Color(0xFF8B4513)

@Composable
fun WiFiDirectDialog(
    onDismiss: () -> Unit,
    onEnableWiFiDirect: () -> Unit,
    onUseHotspot: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, BrownBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = HiveWhite),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Network",
                    modifier = Modifier.size(64.dp),
                    tint = AmberOrange
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Network Restricted 🚫",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BeeBlack,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    text = "Your college/office WiFi blocks device discovery. Choose an option to continue:",
                    fontSize = 14.sp,
                    color = BeeGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Option 1: WiFi Direct
                Button(
                    onClick = onEnableWiFiDirect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HoneyYellow,
                        contentColor = BeeBlack
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Use WiFi Direct",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Hotspot
                OutlinedButton(
                    onClick = onUseHotspot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BeeBlack
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 2.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(HoneyGold)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "📱 Use Mobile Hotspot Instead",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cancel
                TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        color = BeeGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HotspotInstructionsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(2.dp, BrownBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = HiveWhite),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "📱 Mobile Hotspot Setup",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = BeeBlack
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Instructions
                InstructionStep(
                    number = "1",
                    text = "One person creates a Mobile Hotspot (Settings → Hotspot)"
                )
                InstructionStep(
                    number = "2",
                    text = "Other person connects to that hotspot"
                )
                InstructionStep(
                    number = "3",
                    text = "Both open Hive Chat and tap the radar button"
                )
                InstructionStep(
                    number = "4",
                    text = "You'll see each other! Start chatting 🐝"
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HoneyYellow,
                        contentColor = BeeBlack
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Got it!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(HoneyYellow, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BeeBlack
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text,
            fontSize = 14.sp,
            color = BeeBlack,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}