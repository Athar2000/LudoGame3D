package com.athar.ludo3d.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.athar.ludo3d.presentation.screens.auth.LoginScreen
import com.athar.ludo3d.presentation.screens.game.GameScreen
import com.athar.ludo3d.presentation.screens.game.OnlineGameScreen
import com.athar.ludo3d.presentation.screens.home.HomeScreen
import com.athar.ludo3d.presentation.screens.leaderboard.LeaderboardScreen
import com.athar.ludo3d.presentation.screens.localsetup.LocalGameSetupScreen
import com.athar.ludo3d.presentation.screens.profile.ProfileScreen
import com.athar.ludo3d.presentation.screens.rewards.DailyRewardScreen
import com.athar.ludo3d.presentation.screens.shop.ShopScreen
import com.athar.ludo3d.presentation.screens.onboarding.OnboardingScreen
import com.athar.ludo3d.presentation.screens.room.PrivateRoomEntryScreen
import com.athar.ludo3d.presentation.screens.room.RoomLobbyScreen
import com.athar.ludo3d.presentation.screens.splash.SplashScreen
import com.athar.ludo3d.presentation.screens.vscomputer.VsComputerSetupScreen

// Added by Athar
// Phase 1 wires Splash -> Onboarding -> Login -> Home.
// Home's menu buttons navigate to reserved routes (Screen.kt) that get real
// destinations in Phase 4 (rooms/matchmaking) and Phase 7 (AI) etc.
@Composable
fun LudoNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    // Added by Athar
    // Only routes with a real composable registered below are safe to
    // navigate to. Tapping a Home menu item for a not-yet-built phase
    // (Play Online, Vs Computer, etc.) shows an honest "coming soon" toast
    // instead of crashing with a NavHost "destination not found" exception.
    val implementedRoutes = setOf(
        Screen.Splash.route, Screen.Onboarding.route, Screen.Login.route,
        Screen.Home.route, Screen.LocalMultiplayer.route, Screen.LocalGame.route,
        Screen.VsComputer.route, Screen.VsComputerGame.route, Screen.PrivateRoomEntry.route,
        Screen.Leaderboard.route, Screen.Profile.route, Screen.Shop.route, Screen.DailyReward.route
    )
    fun safeNavigate(route: String) {
        if (route in implementedRoutes) {
            navController.navigate(route)
        } else {
            Toast.makeText(context, "Coming in a later build phase", Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = ::safeNavigate
            )
        }

        // Reserved for later phases:
        // Screen.PlayOnline -> Phase 5 (public matchmaking)
        // Screen.Settings remains unbuilt (not part of the Phase 1-10 breakdown)

        // Phase 2: local/practice mode, fully working offline on the real engine.
        composable(Screen.LocalMultiplayer.route) {
            LocalGameSetupScreen(
                onStart = { players ->
                    LocalGameLauncher.launch(players)
                    navController.navigate(Screen.LocalGame.route)
                }
            )
        }
        composable(Screen.LocalGame.route) {
            GameScreen(
                players = LocalGameLauncher.pendingPlayers,
                onLeaveGame = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        // Phase 7: Vs Computer, using the same GameScreen/GameViewModel with AI-controlled players.
        composable(Screen.VsComputer.route) {
            VsComputerSetupScreen(
                onStart = { players, difficulty ->
                    LocalGameLauncher.launch(players, difficulty)
                    navController.navigate(Screen.VsComputerGame.route)
                }
            )
        }
        composable(Screen.VsComputerGame.route) {
            GameScreen(
                players = LocalGameLauncher.pendingPlayers,
                aiDifficulty = LocalGameLauncher.pendingAiDifficulty,
                onLeaveGame = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        // Phase 8: leaderboard, profile, shop, daily rewards.
        composable(Screen.Leaderboard.route) { LeaderboardScreen() }
        composable(Screen.Profile.route) { ProfileScreen() }
        composable(Screen.Shop.route) { ShopScreen() }
        composable(Screen.DailyReward.route) { DailyRewardScreen() }

        // Phase 4: private room system, backed by real Firebase Realtime Database.
        composable(Screen.PrivateRoomEntry.route) {
            PrivateRoomEntryScreen(
                onRoomReady = { roomId ->
                    navController.navigate(Screen.PrivateRoomLobby.createRoute(roomId))
                }
            )
        }
        composable(
            route = Screen.PrivateRoomLobby.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) {
            RoomLobbyScreen(
                onLeave = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onGameStarted = { roomId ->
                    navController.navigate(Screen.OnlineGame.createRoute(roomId)) {
                        popUpTo(Screen.PrivateRoomEntry.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.OnlineGame.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) {
            OnlineGameScreen(
                onLeaveGame = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}
