package com.example.hivechat.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hivechat.R
import com.example.hivechat.model.Device
import com.example.hivechat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    myName: String,
    devices: List<Device>,
    unreadMap: Map<String, Int>,
    isDiscovering: Boolean,
    connectionStatus: String = "",
    onDeviceClick: (Device) -> Unit,
    onDiscoverClick: () -> Unit,
    onLogout: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_my_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Hive Chat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = BeeBlack
                            )
                            Text(
                                text = "Signed in as $myName",
                                fontSize = 12.sp,
                                color = BeeBlack.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    val infiniteTransition = rememberInfiniteTransition()
                    val wobbleRotation by infiniteTransition.animateFloat(
                        initialValue = -5f,
                        targetValue = 5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.Hive,
                            contentDescription = "Logout / Change Username",
                            modifier = Modifier.size(32.dp).rotate(wobbleRotation),
                            tint = BeeBlack
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HoneyYellow,
                    titleContentColor = BeeBlack
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onDiscoverClick,
                containerColor = if (isDiscovering) AmberOrange else HoneyYellow,
                contentColor = BeeBlack,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 12.dp
                )
            ) {
                if (isDiscovering) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Discovering...",
                        modifier = Modifier.size(28.dp).rotate(rotation)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Discover Devices",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(HiveWhite)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search users...", color = BeeGray.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = HoneyGold) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = HiveWhite,
                    unfocusedContainerColor = HiveWhite,
                    focusedTextColor = BeeBlack,
                    unfocusedTextColor = BeeBlack,
                    cursorColor = HoneyGold,
                    focusedBorderColor = HoneyGold,
                    unfocusedBorderColor = BeeGray.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(25.dp))
            )

            // Connection Status
            if (connectionStatus.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = if (connectionStatus.contains("❌")) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = connectionStatus,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = if (connectionStatus.contains("❌")) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val filteredDevices = devices.filter { it.name.contains(searchQuery, ignoreCase = true) }

            when {
                filteredDevices.isEmpty() && !isDiscovering -> EmptyDeviceState()
                filteredDevices.isEmpty() && isDiscovering -> SearchingDeviceState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDevices) { device ->
                        DeviceCard(
                            device = device,
                            unreadCount = unreadMap[device.id] ?: 0,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDeviceState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Radar, contentDescription = "No devices", modifier = Modifier.size(80.dp), tint = HoneyGold.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No Bees Found", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BeeGray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Tap the radar button below to discover devices on your network", fontSize = 14.sp, color = BeeGray.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Icon(Icons.Default.TouchApp, contentDescription = "Tap", modifier = Modifier.size(40.dp), tint = HoneyYellow.copy(alpha = 0.7f))
    }
}

@Composable
fun SearchingDeviceState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(animation = tween(800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
        )
        Text("🐝", fontSize = 64.sp, modifier = Modifier.scale(scale))
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(modifier = Modifier.size(40.dp), color = HoneyYellow, strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Scanning for bees...", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = BeeGray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Make sure other devices are on the same Wi-Fi network", fontSize = 14.sp, color = BeeGray.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

@Composable
fun DeviceCard(device: Device, unreadCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(
            indication = androidx.compose.material.ripple.rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        ) { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(HoneyYellow), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = "User", modifier = Modifier.size(32.dp), tint = BeeBlack)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = BeeBlack)
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color.Red), contentAlignment = Alignment.Center) {
                            Text(unreadCount.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Online", fontSize = 12.sp, color = BeeGray)
                }
            }

            Icon(Icons.Default.ChevronRight, contentDescription = "Chat", tint = HoneyGold)
        }
    }
}
