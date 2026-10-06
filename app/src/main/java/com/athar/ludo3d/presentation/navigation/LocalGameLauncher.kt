package com.athar.ludo3d.presentation.navigation

import com.athar.ludo3d.domain.engine.AIDifficulty
import com.athar.ludo3d.domain.model.Player

// Added by Athar
// Compose Navigation route arguments must be primitives/strings, but a local
// game needs to hand a full List<Player> (and, for Vs Computer, an
// AIDifficulty) from the setup screen to the game screen. Rather than
// (de)serializing that into a nav arg just to immediately parse it back, we
// pass it through this single-slot in-memory holder scoped to the navigation
// graph's lifetime. This is NOT how online room data flows (that comes from
// Firebase Realtime Database, section 5) — this exists only for the
// same-device local/practice and vs-computer modes.
object LocalGameLauncher {
    var pendingPlayers: List<Player> = emptyList()
        private set
    var pendingAiDifficulty: AIDifficulty = AIDifficulty.MEDIUM
        private set

    fun launch(players: List<Player>, aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM) {
        pendingPlayers = players
        pendingAiDifficulty = aiDifficulty
    }
}
