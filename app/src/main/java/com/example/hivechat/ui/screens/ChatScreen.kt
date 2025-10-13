package com.example.hivechat.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import com.example.hivechat.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val BrownBorder = Color(0xFF8B4513) // 1px brown border

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    device: Device,
    messages: List<Message>,
    onSendMessage: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = messages.size - 1,
                    scrollOffset = 0
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(HoneyGold.copy(alpha = 0.3f))
                                .border(1.dp, BrownBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Avatar",
                                modifier = Modifier.size(24.dp),
                                tint = BeeBlack
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = device.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = BeeBlack
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulse_scale"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .scale(scale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Online",
                                    fontSize = 13.sp,
                                    color = BeeBlack.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
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
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, BrownBorder, RoundedCornerShape(22.dp)),
                        placeholder = {
                            Text(
                                "Type a message...",
                                color = BeeGray.copy(alpha = 0.5f),
                                fontSize = 15.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = HiveWhite,
                            unfocusedContainerColor = HiveWhite,
                            focusedBorderColor = HoneyYellow,
                            unfocusedBorderColor = HoneyGold.copy(alpha = 0.2f),
                            cursorColor = HoneyYellow,
                            focusedTextColor = BeeBlack,
                            unfocusedTextColor = BeeBlack
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText.trim())
                                    messageText = ""
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        maxLines = 4,
                        singleLine = false,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val buttonScale by animateFloatAsState(
                        targetValue = if (messageText.isNotBlank()) 1f else 0.9f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "button_scale"
                    )

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText.trim())
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .scale(buttonScale)
                            .clip(CircleShape)
                            .border(1.dp, BrownBorder, CircleShape)
                            .background(
                                brush = if (messageText.isNotBlank()) {
                                    Brush.linearGradient(
                                        colors = listOf(HoneyYellow, HoneyGold)
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            BeeGray.copy(alpha = 0.15f),
                                            BeeGray.copy(alpha = 0.1f)
                                        )
                                    )
                                },
                                shape = CircleShape
                            ),
                        enabled = messageText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) BeeBlack else BeeGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            HiveWhite,
                            HoneyYellow.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            AnimatedContent(
                targetState = messages.isEmpty(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "chatTransition"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyChatState(device.name)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.timestamp }
                        ) { message ->
                            MessageBubble(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(deviceName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "bee_bounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🐝",
            fontSize = 72.sp,
            modifier = Modifier.offset(y = bounceOffset.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Start buzzing!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = BeeBlack
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Send your first message to $deviceName",
            fontSize = 15.sp,
            color = BeeGray.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun MessageBubble(message: Message) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
        label = "message_enter"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .border(
                        1.dp, BrownBorder, RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (message.isMine) 20.dp else 4.dp,
                            bottomEnd = if (message.isMine) 4.dp else 20.dp
                        )
                    ),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (message.isMine) 20.dp else 4.dp,
                    bottomEnd = if (message.isMine) 4.dp else 20.dp
                ),
                color = if (message.isMine) MyMessageBubble else TheirMessageBubble,
                shadowElevation = 3.dp,
                tonalElevation = if (message.isMine) 2.dp else 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (!message.isMine) {
                        Text(
                            text = message.senderName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HoneyGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = if (message.isMine) MyMessageText else TheirMessageText
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = dateFormat.format(Date(message.timestamp)),
                fontSize = 11.sp,
                color = BeeGray.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}
