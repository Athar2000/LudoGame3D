// Added by Athar
// The server-side source of truth for prices (see rewardsAndShop.ts's
// purchaseItem, which never trusts a client-supplied price). The Android
// client has its own copy for display (presentation/screens/shop/ShopCatalog.kt)
// — keep the two in sync manually when adding items, same discipline as
// ludoEngine.ts/LudoGameEngine.kt.
export type ShopCategory = "DICE_SKIN" | "PAWN_SKIN" | "BOARD_THEME" | "AVATAR" | "EFFECT";

export interface ShopItem {
  id: string;
  category: ShopCategory;
  name: string;
  priceCoins: number;
}

export const SHOP_CATALOG: ShopItem[] = [
  { id: "dice_neon", category: "DICE_SKIN", name: "Neon Dice", priceCoins: 300 },
  { id: "dice_wood", category: "DICE_SKIN", name: "Carved Wood Dice", priceCoins: 450 },
  { id: "pawn_crystal", category: "PAWN_SKIN", name: "Crystal Pawns", priceCoins: 500 },
  { id: "pawn_royal", category: "PAWN_SKIN", name: "Royal Gold Pawns", priceCoins: 800 },
  { id: "board_midnight", category: "BOARD_THEME", name: "Midnight Board", priceCoins: 600 },
  { id: "board_marble", category: "BOARD_THEME", name: "Marble Board", priceCoins: 900 },
  { id: "avatar_astronaut", category: "AVATAR", name: "Astronaut", priceCoins: 200 },
  { id: "avatar_dragon", category: "AVATAR", name: "Dragon", priceCoins: 350 },
  { id: "effect_confetti", category: "EFFECT", name: "Confetti Win Effect", priceCoins: 400 },
  { id: "effect_fireworks", category: "EFFECT", name: "Fireworks Win Effect", priceCoins: 650 },
];
