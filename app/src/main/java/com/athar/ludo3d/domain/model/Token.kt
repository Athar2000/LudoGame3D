package com.athar.ludo3d.domain.model

// Added by Athar
// A token's position is encoded on a single unified local track per player:
//   0        -> still in base (not yet released)
//   1..51    -> on the shared outer path, LOCAL to this player
//                (global square = (color.startOffset + position - 1) % 52)
//   52..57   -> on this player's private 6-square home column
//   58       -> finished (reached home / final destination)
// This unified numbering means "did this move overshoot home" is a single
// integer comparison, and it keeps the engine free of Firebase/UI concerns.
data class Token(
    val id: Int,               // 0..3, unique per player
    val position: Int = BASE
) {
    val isInBase: Boolean get() = position == BASE
    val isFinished: Boolean get() = position == FINISHED
    val isOnHomeColumn: Boolean get() = position in 52..57
    val isOnSharedPath: Boolean get() = position in 1..51

    companion object {
        const val BASE = 0
        const val FINISHED = 58
    }
}
