package com.example.hivechat.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hivechat.model.Device
import com.example.hivechat.model.Message
import com.example.hivechat.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, BeeBlack, CircleShape),
                            tint = BeeBlack
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(device.name, fontWeight = FontWeight.Bold, color = BeeBlack)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BeeBlack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HoneyYellow)
            )
        },
        containerColor = HiveWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.timestamp }) { message ->
                    MessageBubble(message)
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(HiveWhite),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, BeeGray, RoundedCornerShape(24.dp)),
                    placeholder = { Text("Type something silly… 🐝") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
//                        textColor = BeeBlack,
//                        placeholderColor = BeeGray,
                        cursorColor = BeeBlack,
                        focusedBorderColor = HoneyYellow,
                        unfocusedBorderColor = BeeGray
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText.trim())
                                messageText = ""
                            }
                        }
                    ),
                    maxLines = 4,
                    singleLine = false
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText.trim())
                            messageText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (messageText.isNotBlank()) Brush.linearGradient(listOf(HoneyYellow, HoneyGold))
                            else Brush.linearGradient(listOf(BeeGray.copy(alpha = 0.15f), BeeGray.copy(alpha = 0.1f)))
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank()) BeeBlack else BeeGray
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isMine) 20.dp else 4.dp,
                bottomEnd = if (message.isMine) 4.dp else 20.dp
            ),
            color = if (message.isMine) MyMessageBubble else TheirMessageBubble,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isMine) {
                    Text(
                        message.senderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = HoneyGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    message.text,
                    fontSize = 15.sp,
                    color = if (message.isMine) MyMessageText else TheirMessageText
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = dateFormat.format(Date(message.timestamp)),
            fontSize = 11.sp,
            color = BeeGray.copy(alpha = 0.5f)
        )
    }
}
