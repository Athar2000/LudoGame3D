package com.athar.ludo3d.presentation.screens.game.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.athar.ludo3d.domain.engine.CaptureInfo
import com.athar.ludo3d.domain.engine.Particle
import com.athar.ludo3d.domain.engine.ParticleSystem
import kotlin.random.Random

// Added by Athar
// Phase 9: capture burst + win confetti, drawn on a Canvas layered over the
// board. Simulation lives in the pure, tested ParticleSystem; this file only
// spawns, steps once per frame (withFrameNanos), and draws. A pending list is
// consumed via a monotonically increasing "effectVersion" so recomposition
// never re-spawns the same burst.
private val captureColors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD54F), Color(0xFFFF7043), Color(0xFFE94B4B))
private val confettiColors = listOf(Color(0xFFE94B4B), Color(0xFF35C46B), Color(0xFFF6C445), Color(0xFF3E7BFA), Color(0xFF9B59F6))

@Composable
fun ParticleOverlay(
    captures: List<CaptureInfo>,
    winnerCelebration: Boolean,
    effectVersion: Int,
    modifier: Modifier = Modifier
) {
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var isConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(effectVersion) {
        if (effectVersion == 0) return@LaunchedEffect
        val random = Random(effectVersion)
        val spawned = mutableListOf<Particle>()
        captures.forEach { c ->
            val cell = BoardLayout.cellFor(c.color, c.tokenId, c.previousPosition)
            val n = BoardLayout.GRID_SIZE.toFloat()
            spawned += ParticleSystem.burst((cell.col + 0.5f) / n, (cell.row + 0.5f) / n, 22, captureColors.size, random)
        }
        if (winnerCelebration) {
            isConfetti = true
            spawned += ParticleSystem.confetti(120, confettiColors.size, random)
        } else if (captures.isNotEmpty()) {
            isConfetti = false
        }
        particles = particles + spawned

        var last = 0L
        while (particles.isNotEmpty()) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                particles = ParticleSystem.step(particles, dt)
            }
        }
    }

    Canvas(modifier = modifier) {
        val palette = if (isConfetti) confettiColors else captureColors
        particles.forEach { p ->
            drawCircle(
                color = palette[p.colorIndex % palette.size].copy(alpha = p.alpha),
                radius = p.size * size.width,
                center = Offset(p.x * size.width, p.y * size.height)
            )
        }
    }
}
