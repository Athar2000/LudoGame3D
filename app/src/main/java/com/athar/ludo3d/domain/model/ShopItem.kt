package com.athar.ludo3d.domain.model

// Added by Athar
// Section 17. This catalog is a DISPLAY mirror of the server-side source of
// truth in functions/src/shopCatalog.ts — purchaseItem() (the Cloud
// Function) never trusts a price the client sends, it looks the item up in
// its own copy of this list. Keep the two in sync manually when adding
// items, same discipline as the engine/AI ports.
enum class ShopCategory { DICE_SKIN, PAWN_SKIN, BOARD_THEME, AVATAR, EFFECT }

data class ShopItem(
    val id: String,
    val category: ShopCategory,
    val name: String,
    val priceCoins: Int
)

val SHOP_CATALOG: List<ShopItem> = listOf(
    ShopItem("dice_neon", ShopCategory.DICE_SKIN, "Neon Dice", 300),
    ShopItem("dice_wood", ShopCategory.DICE_SKIN, "Carved Wood Dice", 450),
    ShopItem("pawn_crystal", ShopCategory.PAWN_SKIN, "Crystal Pawns", 500),
    ShopItem("pawn_royal", ShopCategory.PAWN_SKIN, "Royal Gold Pawns", 800),
    ShopItem("board_midnight", ShopCategory.BOARD_THEME, "Midnight Board", 600),
    ShopItem("board_marble", ShopCategory.BOARD_THEME, "Marble Board", 900),
    ShopItem("avatar_astronaut", ShopCategory.AVATAR, "Astronaut", 200),
    ShopItem("avatar_dragon", ShopCategory.AVATAR, "Dragon", 350),
    ShopItem("effect_confetti", ShopCategory.EFFECT, "Confetti Win Effect", 400),
    ShopItem("effect_fireworks", ShopCategory.EFFECT, "Fireworks Win Effect", 650)
)
