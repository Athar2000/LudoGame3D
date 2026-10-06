package com.athar.ludo3d.presentation.navigation

// Added by Athar
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Home : Screen("home")

    // Destinations wired up in later phases. Routes are reserved now
    // so navigation from Home compiles end-to-end without dead links.
    data object PlayOnline : Screen("play_online") // Phase 5: public matchmaking, not yet built

    data object VsComputer : Screen("vs_computer")
    data object VsComputerGame : Screen("vs_computer_game")
    data object LocalMultiplayer : Screen("local_multiplayer")
    data object LocalGame : Screen("local_game") // player list handed off via LocalGameLauncher

    data object PrivateRoomEntry : Screen("private_room_entry")
    data object PrivateRoomLobby : Screen("private_room_lobby/{roomId}") {
        fun createRoute(roomId: String) = "private_room_lobby/$roomId"
    }
    data object OnlineGame : Screen("online_game_started/{roomId}") {
        fun createRoute(roomId: String) = "online_game_started/$roomId"
    }

    data object Leaderboard : Screen("leaderboard")
    data object Profile : Screen("profile")
    data object Shop : Screen("shop")
    data object DailyReward : Screen("daily_reward")
    data object Settings : Screen("settings")
}
