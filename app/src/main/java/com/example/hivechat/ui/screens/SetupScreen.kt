@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.hivechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hivechat.R
import com.example.hivechat.ui.screens.BeeFly
import com.example.hivechat.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SetupScreen(
    onNameSet: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(HoneyLight, HiveWhite)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Card container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile icon
            val iconSize = 80.dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(iconSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize(),
                    tint = HoneyYellow
                )

                // Bees orbiting the icon
                val beeCount = 4
                val random = remember { Random(System.currentTimeMillis()) }
                repeat(beeCount) {
                    val radius = 35f + random.nextFloat() * 20f
                    val speed = 4000 + random.nextInt(3000)
                    BeeFly(radius = radius, speed = speed, size = 20.dp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Titles
            Text(
                text = "Welcome to the Hive!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = BeeBlack
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose your name to start buzzing",
                fontSize = 16.sp,
                color = BeeGray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Name Input
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    showError = false
                },
                label = { Text("Your Name") },
                placeholder = { Text("Enter your name") },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text("Please enter a name", color = Color.Red) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HoneyYellow,
                    unfocusedBorderColor = HoneyGold.copy(alpha = 0.5f),
                    focusedLabelColor = HoneyYellow,
                    cursorColor = HoneyYellow
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (name.isNotBlank()) onNameSet(name.trim())
                        else showError = true
                    }
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Join button
            Button(
                onClick = {
                    if (name.isNotBlank()) onNameSet(name.trim())
                    else showError = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HoneyYellow,
                    contentColor = BeeBlack
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 12.dp
                )
            ) {
                Text(
                    text = "Join the Hive",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
