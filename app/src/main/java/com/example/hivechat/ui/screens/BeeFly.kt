package com.example.hivechat.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hivechat.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BeeFly(
    radius: Float,
    speed: Int,
    size: Dp = 24.dp,
    delayMillis: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bee_orbit")

    val randomStartAngle = remember { (0..360).random().toFloat() }

    val angle by infiniteTransition.animateFloat(
        initialValue = randomStartAngle,
        targetValue = randomStartAngle + 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = speed,
                delayMillis = delayMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    // Wing flap animation
    val flapScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flap"
    )

    val radians = Math.toRadians(angle.toDouble())
    val x = (radius * cos(radians)).toFloat()
    val y = (radius * sin(radians)).toFloat()

    Image(
        painter = painterResource(id = R.drawable.ic_bee),
        contentDescription = "Flying Bee",
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .size(size)
            .scale(flapScale)
            .rotate(angle + 90f) // Rotate to face direction of movement
    )
}