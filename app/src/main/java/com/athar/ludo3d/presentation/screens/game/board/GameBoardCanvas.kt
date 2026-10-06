package com.athar.ludo3d.presentation.screens.game.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.athar.ludo3d.domain.engine.LudoGameEngine
import com.athar.ludo3d.domain.model.Player
import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.presentation.theme.TokenBlue
import com.athar.ludo3d.presentation.theme.TokenGreen
import com.athar.ludo3d.presentation.theme.TokenRed
import com.athar.ludo3d.presentation.theme.TokenYellow

// Added by Athar
// Renders the board on a single Canvas: no bitmaps, no 3D engine, so it stays
// light on mid-range phones (section 25) while still reading as "3D" through
// layered gradients, soft shadow offsets on every cell and every token, and a
// highlighted glow on cells that are currently legal moves.
fun playerColorToUi(color: PlayerColor): Color = when (color) {
    PlayerColor.RED -> TokenRed
    PlayerColor.GREEN -> TokenGreen
    PlayerColor.YELLOW -> TokenYellow
    PlayerColor.BLUE -> TokenBlue
}

@Composable
fun GameBoardCanvas(
    players: List<Player>,
    highlightedTokenIds: Set<Pair<String, Int>>, // (playerId, tokenId) that are legal to move right now
    onTokenTap: (playerId: String, tokenId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(players, highlightedTokenIds) {
                detectTapGestures { tapOffset ->
                    val cellSize = size.width / BoardLayout.GRID_SIZE.toFloat()
                    // Hit-test only tappable (highlighted / legal) tokens, closest center wins,
                    // and only within roughly one token radius so mis-taps are ignored.
                    var bestDistance = Float.MAX_VALUE
                    var bestHit: Pair<String, Int>? = null
                    players.forEach { player ->
                        player.tokens.forEach { token ->
                            val key = player.userId to token.id
                            if (key !in highlightedTokenIds) return@forEach
                            val cell = BoardLayout.cellFor(player.color, token.id, token.position)
                            val center = cellCenter(cell.row, cell.col, cellSize)
                            val dx = tapOffset.x - center.x
                            val dy = tapOffset.y - center.y
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (distance < cellSize * 0.6f && distance < bestDistance) {
                                bestDistance = distance
                                bestHit = key
                            }
                        }
                    }
                    bestHit?.let { (playerId, tokenId) -> onTokenTap(playerId, tokenId) }
                }
            }
    ) {
        val cellSize = size.width / BoardLayout.GRID_SIZE

        // --- Board base: a "wooden frame" gradient behind everything (section 9) ---
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF3E2A1E), Color(0xFF6B4A32), Color(0xFF3E2A1E))
            ),
            cornerRadius = CornerRadius(24f, 24f)
        )

        // Inner play surface, inset slightly so the wooden frame shows as a border.
        val inset = cellSize * 0.15f
        drawRoundRect(
            color = Color(0xFFF5EFE3),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(16f, 16f)
        )

        // --- Four home quadrants, colored, with soft drop shadow for depth ---
        drawHomeQuadrant(PlayerColor.RED, 0, 0, cellSize)
        drawHomeQuadrant(PlayerColor.GREEN, 0, 9, cellSize)
        drawHomeQuadrant(PlayerColor.YELLOW, 9, 9, cellSize)
        drawHomeQuadrant(PlayerColor.BLUE, 9, 0, cellSize)

        // --- Path cells (52) ---
        BoardLayout.pathCells.forEachIndexed { index, cell ->
            val isSafe = index in LudoGameEngine.SAFE_SQUARES
            drawCell(
                row = cell.row,
                col = cell.col,
                cellSize = cellSize,
                fill = if (isSafe) Color(0xFFE9E2D0) else Color(0xFFFFFFFF),
                drawStar = isSafe
            )
        }

        // --- Home column cells, tinted per color ---
        BoardLayout.homeColumnCells.forEach { (color, cells) ->
            cells.forEach { cell ->
                drawCell(cell.row, cell.col, cellSize, playerColorToUi(color).copy(alpha = 0.45f))
            }
        }

        // --- Center finish triangle ---
        drawCell(BoardLayout.CENTER.row, BoardLayout.CENTER.col, cellSize, Color(0xFFD8CBB0))

        // --- Highlighted legal-move glow, drawn under the tokens ---
        players.forEach { player ->
            player.tokens.forEach { token ->
                if (highlightedTokenIds.contains(player.userId to token.id)) {
                    val cell = BoardLayout.cellFor(player.color, token.id, token.position)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = cellSize * 0.55f,
                        center = cellCenter(cell.row, cell.col, cellSize)
                    )
                }
            }
        }

        // --- Tokens, drawn as 3D-look pawns (radial gradient + shadow offset) ---
        players.forEach { player ->
            player.tokens.forEachIndexed { idx, token ->
                if (token.isFinished) return@forEachIndexed // stacked at center, simplified
                val cell = BoardLayout.cellFor(player.color, token.id, token.position)
                val stackOffset = if (token.isInBase) Offset.Zero else Offset((idx % 2) * 4f, (idx / 2) * 4f)
                drawToken(cellCenter(cell.row, cell.col, cellSize) + stackOffset, cellSize, playerColorToUi(player.color))
            }
        }
    }
}

private fun DrawScope.drawHomeQuadrant(color: PlayerColor, rowStart: Int, colStart: Int, cellSize: Float) {
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(playerColorToUi(color).copy(alpha = 0.85f), playerColorToUi(color).copy(alpha = 0.55f))
        ),
        topLeft = Offset(colStart * cellSize + cellSize * 0.4f, rowStart * cellSize + cellSize * 0.4f),
        size = Size(cellSize * 5.2f, cellSize * 5.2f),
        cornerRadius = CornerRadius(cellSize * 0.6f, cellSize * 0.6f)
    )
    // inner "tray" where tokens sit, lighter, to read as an indent (depth cue)
    drawRoundRect(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(colStart * cellSize + cellSize * 1.1f, rowStart * cellSize + cellSize * 1.1f),
        size = Size(cellSize * 3.8f, cellSize * 3.8f),
        cornerRadius = CornerRadius(cellSize * 0.4f, cellSize * 0.4f)
    )
}

private fun DrawScope.drawCell(row: Int, col: Int, cellSize: Float, fill: Color, drawStar: Boolean = false) {
    val topLeft = Offset(col * cellSize, row * cellSize)
    // Subtle drop shadow offset below-right of each cell for a raised, tactile feel.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.08f),
        topLeft = topLeft + Offset(1.5f, 1.5f),
        size = Size(cellSize * 0.94f, cellSize * 0.94f),
        cornerRadius = CornerRadius(cellSize * 0.12f, cellSize * 0.12f)
    )
    drawRoundRect(
        color = fill,
        topLeft = topLeft,
        size = Size(cellSize * 0.94f, cellSize * 0.94f),
        cornerRadius = CornerRadius(cellSize * 0.12f, cellSize * 0.12f)
    )
    drawRoundRect(
        color = Color(0x14000000),
        topLeft = topLeft,
        size = Size(cellSize * 0.94f, cellSize * 0.94f),
        cornerRadius = CornerRadius(cellSize * 0.12f, cellSize * 0.12f),
        style = Stroke(width = 1f)
    )
    if (drawStar) {
        drawCircle(
            color = Color(0xFFB8A868),
            radius = cellSize * 0.18f,
            center = topLeft + Offset(cellSize * 0.47f, cellSize * 0.47f)
        )
    }
}

private fun DrawScope.drawToken(center: Offset, cellSize: Float, color: Color) {
    val radius = cellSize * 0.34f
    // Drop shadow first (depth cue), then a radial-gradient body for a glossy pawn look.
    drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = radius, center = center + Offset(2f, 3f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = 1f), color.copy(alpha = 0.7f)),
            center = center - Offset(radius * 0.3f, radius * 0.3f),
            radius = radius * 1.4f
        ),
        radius = radius,
        center = center
    )
    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = radius * 0.25f, center = center - Offset(radius * 0.35f, radius * 0.35f))
    drawCircle(color = Color.Black.copy(alpha = 0.15f), radius = radius, center = center, style = Stroke(width = 1.5f))
}

private fun cellCenter(row: Int, col: Int, cellSize: Float) =
    Offset(col * cellSize + cellSize * 0.47f, row * cellSize + cellSize * 0.47f)
