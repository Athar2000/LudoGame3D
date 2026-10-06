package com.athar.ludo3d.domain.engine

import kotlin.random.Random

// Added by Athar
// Phase 9. Tiny deterministic particle simulation (seedable, so it's testable).
// Coordinates are normalized to the board: x,y in 0..1 of its width/height, so
// the drawing layer scales them to whatever size the board is on screen.
data class Particle(
    val x: Float, val y: Float,
    val vx: Float, val vy: Float,
    val life: Float,          // seconds remaining
    val maxLife: Float,
    val colorIndex: Int,
    val size: Float           // fraction of board width
) {
    val alpha: Float get() = (life / maxLife).coerceIn(0f, 1f)
    val isAlive: Boolean get() = life > 0f
}

object ParticleSystem {
    private const val GRAVITY = 0.9f // board-heights per second squared

    /** Radial burst at (cx, cy) — used when a token is captured. */
    fun burst(cx: Float, cy: Float, count: Int, colorCount: Int, random: Random): List<Particle> =
        List(count) {
            val angle = random.nextFloat() * (2 * Math.PI).toFloat()
            val speed = 0.25f + random.nextFloat() * 0.45f
            val life = 0.5f + random.nextFloat() * 0.4f
            Particle(cx, cy, kotlin.math.cos(angle) * speed, kotlin.math.sin(angle) * speed,
                life, life, random.nextInt(colorCount), 0.012f + random.nextFloat() * 0.012f)
        }

    /** Confetti falling from above the board — used for the win celebration. */
    fun confetti(count: Int, colorCount: Int, random: Random): List<Particle> =
        List(count) {
            val life = 1.8f + random.nextFloat() * 1.4f
            Particle(random.nextFloat(), -0.05f - random.nextFloat() * 0.25f,
                (random.nextFloat() - 0.5f) * 0.25f, 0.15f + random.nextFloat() * 0.25f,
                life, life, random.nextInt(colorCount), 0.014f + random.nextFloat() * 0.014f)
        }

    /** Advance by dt seconds and drop dead particles. */
    fun step(particles: List<Particle>, dt: Float): List<Particle> =
        particles.mapNotNull { p ->
            val life = p.life - dt
            if (life <= 0f) null
            else p.copy(x = p.x + p.vx * dt, y = p.y + p.vy * dt, vy = p.vy + GRAVITY * dt, life = life)
        }
}
