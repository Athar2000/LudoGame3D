package com.athar.ludo3d.domain.model

// Added by Athar
data class Player(
    val userId: String,
    val name: String,
    val color: PlayerColor,
    val isAI: Boolean = false,
    val connected: Boolean = true,
    val tokens: List<Token> = List(4) { Token(id = it) }
) {
    val hasWon: Boolean get() = tokens.all { it.isFinished }
}
