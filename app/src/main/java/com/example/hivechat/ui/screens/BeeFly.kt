package com.example.hivechat.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hivechat.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun BeeFly(
    radius: Float,
    speed: Int,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    val randomStartAngle = remember { Random.nextFloat() * 360f }

    val angle by infiniteTransition.animateFloat(
        initialValue = randomStartAngle,
        targetValue = randomStartAngle + 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val radians = angle * PI / 180.0

    // Flap effect
    val flapScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Icon(
        painter = painterResource(id = R.drawable.ic_bee),
        contentDescription = "Bee",
        modifier = Modifier
            .offset(
                x = (radius * cos(radians)).toFloat().dp,
                y = (radius * sin(radians)).toFloat().dp
            )
            .size(size)
            .scale(flapScale)
            .rotate((angle % 360) - 180f),
        tint = Color.Unspecified
    )
}
