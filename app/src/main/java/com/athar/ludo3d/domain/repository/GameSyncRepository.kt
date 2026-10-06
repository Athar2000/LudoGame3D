package com.athar.ludo3d.domain.repository

import com.athar.ludo3d.domain.model.GameState
import com.athar.ludo3d.domain.model.Room
import kotlinx.coroutines.flow.Flow

// Added by Athar
// How long a player has to roll/move before any other connected player may
// call forceSkipExpiredTurn(). Lives here (domain) rather than inside
// FirebaseGameSyncRepository so the UI layer (turn timer countdown) and the
// data layer (the actual expiry check) share one source of truth instead of
// two constants that could drift apart.
const val TURN_DURATION_MS = 20_000L

// Added by Athar
// The online counterpart to running LudoGameEngine directly in memory
// (GameViewModel, Phase 2). Every mutating call here re-reads the latest
// gameState from Firebase, reconstructs a LudoGameEngine from it, applies
// the SAME rules engine used locally, then writes the result back — so the
// rules themselves never diverge between local and online play. What DOES
// differ from local play: rollDice()/submitMove() first verify the caller
// is actually the current turn's player, using the value already stored in
// Firebase, not whatever the caller claims.
//
// IMPORTANT (see firebase/DATABASE_DESIGN.md): this client-side re-validation
// is Phase 5 bring-up, not the final security model. A modified client APK
// could skip these checks and write directly to Realtime Database if the
// security rules allowed it — which is exactly why database.rules.json locks
// gameState writes to "the player whose turn it currently is, per the data
// already in the room" rather than trusting any authenticated room member.
// The fully tamper-proof version moves this same logic into a Cloud Function
// running with the Admin SDK (Phase 6), so a hostile client can't bypass it
// by talking to Realtime Database directly at all.
sealed class GameSyncError(message: String) : Exception(message) {
    data object NotYourTurn : GameSyncError("It's not your turn.")
    data object GameNotFound : GameSyncError("This game could not be found.")
    data object GameNotInProgress : GameSyncError("This game isn't in progress.")
    data object AlreadyRolled : GameSyncError("You've already rolled this turn.")
    data object NoDiceRolled : GameSyncError("Roll the dice first.")
    data object InvalidMove : GameSyncError("That move isn't legal right now.")
    data object TurnNotExpiredYet : GameSyncError("The current turn hasn't timed out yet.")
    data object NotHost : GameSyncError("Only the host can do that.")
}

interface GameSyncRepository {
    /** Host-only. Builds the initial authoritative GameState from the room's players and writes it. */
    suspend fun startGame(room: Room): Result<Unit>

    /** Live gameplay state — every phone in the room collects the same Flow. */
    fun observeGameState(roomId: String): Flow<GameState?>

    suspend fun rollDice(roomId: String): Result<Unit>

    suspend fun submitMove(roomId: String, tokenId: Int): Result<Unit>

    /**
     * Callable by ANY connected player in the room, not just the current one —
     * so if the current player's phone dies mid-turn, someone else can still
     * un-stick the game once the turn timer has genuinely expired (checked
     * against the server-stored turnStartedAt, not the caller's own clock).
     */
    suspend fun forceSkipExpiredTurn(roomId: String): Result<Unit>

    /** Marks the caller disconnected/left; if it was their turn, advances play. */
    suspend fun leaveGame(roomId: String): Result<Unit>

    /** Host-only, only once the game is FINISHED. Resets gameState so the same room can play again. */
    suspend fun requestRematch(roomId: String): Result<Unit>
}
