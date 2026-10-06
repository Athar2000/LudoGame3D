package com.athar.ludo3d.presentation.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.athar.ludo3d.domain.engine.AIDifficulty
import com.athar.ludo3d.domain.model.Player

// Added by Athar
// GameViewModel takes the runtime player list (chosen on the setup screen) as
// a constructor argument, so it can't be built by Hilt's default no-arg
// injection — this factory is the standard AndroidX way to hand it that data
// while keeping GameViewModel's constructor honest (no nullable "set it
// later" fields).
class GameViewModelFactory(
    private val players: List<Player>,
    private val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(players, aiDifficulty) as T
    }
}
