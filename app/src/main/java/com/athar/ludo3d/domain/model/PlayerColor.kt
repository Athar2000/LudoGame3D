package com.athar.ludo3d.domain.model

// Added by Athar
// The four Ludo colors and their fixed entry point on the shared 52-square outer path.
// Classic board layout: each color's start is 13 squares apart on the 0..51 ring.
enum class PlayerColor(val startOffset: Int) {
    RED(0),
    GREEN(13),
    YELLOW(26),
    BLUE(39)
}
