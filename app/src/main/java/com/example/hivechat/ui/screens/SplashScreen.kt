package com.example.hivechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hivechat.R
import com.example.hivechat.ui.theme.AmberOrange
import com.example.hivechat.ui.theme.HoneyGold
import com.example.hivechat.ui.theme.HoneyYellow
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToSetup: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000)
        onNavigateToSetup()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8F0)), // Off-white background
        contentAlignment = Alignment.Center
    ) {
        val logoSize = 180.dp // Bigger logo

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(logoSize)
        ) {
            // Your logo
            Icon(
                painter = painterResource(id = R.drawable.ic_my_logo),
                contentDescription = "My Logo",
                modifier = Modifier.fillMaxSize(),
                tint = Color.Unspecified
            )

            // Bees flying around the logo
            val beeCount = 5
            val random = remember { Random(System.currentTimeMillis()) }
            repeat(beeCount) {
                val radius = logoSize.value / 2 + random.nextFloat() * 30f
                val speed = 5000 + random.nextInt(3000)
                BeeFly(radius = radius, speed = speed, size = 40.dp)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Text(
                text = "Hive Chat",
                fontSize = 42.sp,
                color = Color.Black,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect. Chat. Buzz Together! 🐝",
                fontSize = 16.sp,
                color = Color(0xFF8B4513)
            )
        }
    }
}