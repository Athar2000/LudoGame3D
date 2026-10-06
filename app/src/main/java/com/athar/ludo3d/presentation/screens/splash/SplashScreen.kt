package com.athar.ludo3d.presentation.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.athar.ludo3d.presentation.theme.NavyBackground
import com.athar.ludo3d.presentation.theme.PrimaryBlue
import com.athar.ludo3d.presentation.theme.TokenBlue
import com.athar.ludo3d.presentation.theme.TokenGreen
import com.athar.ludo3d.presentation.theme.TokenRed
import com.athar.ludo3d.presentation.theme.TokenYellow
import kotlinx.coroutines.delay

// Added by Athar
// Logo + a spinning "3D" dice (gradient/shadow illusion, no 3D engine needed
// for a splash) + four pulsing pawn dots. Hands off to Onboarding after 1.8s.
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "dice-rotate")
    val diceRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    LaunchedEffect(Unit) {
        delay(1800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(PrimaryBlue.copy(alpha = 0.25f), NavyBackground))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "LUDO 3D",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .rotate(diceRotation)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, PrimaryBlue)))
            )

            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(TokenRed, TokenGreen, TokenYellow, TokenBlue).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}
