package com.athar.ludo3d.presentation.screens.game.board

import com.athar.ludo3d.domain.model.PlayerColor
import com.athar.ludo3d.domain.model.Token

// Added by Athar
// Pure coordinate math: maps every engine position (Token.position, see
// domain/model/Token.kt) to a cell on a 15x15 Ludo grid. Built from ONE
// hand-authored 13-cell "arm" template for Red, then rotated 90/180/270
// degrees around the board center to generate Green/Yellow/Blue — this
// guarantees perfect 4-fold symmetry instead of 52 hand-typed coordinates
// that are easy to get subtly wrong.
//
// Grid convention: (row, col), both 0..14, (0,0) = top-left.
object BoardLayout {
    const val GRID_SIZE = 15
    val CENTER = Cell(7, 7)

    data class Cell(val row: Int, val col: Int)

    // Red's 13-cell arm: right along row 6 (cols 1..5), up column 6 (rows 5..0),
    // then right along row 0 (cols 7..8) to hand off to Green's arm.
    private val redArmTemplate = listOf(
        Cell(6, 1), Cell(6, 2), Cell(6, 3), Cell(6, 4), Cell(6, 5),
        Cell(5, 6), Cell(4, 6), Cell(3, 6), Cell(2, 6), Cell(1, 6), Cell(0, 6),
        Cell(0, 7), Cell(0, 8)
    )

    private val redHomeColumnTemplate = listOf(
        Cell(7, 1), Cell(7, 2), Cell(7, 3), Cell(7, 4), Cell(7, 5), Cell(7, 6)
    )

    private val redBaseTemplate = listOf(
        Cell(1, 1), Cell(1, 4), Cell(4, 1), Cell(4, 4)
    )

    private fun rotate90(cell: Cell) = Cell(cell.col, GRID_SIZE - 1 - cell.row)
    private fun rotate180(cell: Cell) = Cell(GRID_SIZE - 1 - cell.row, GRID_SIZE - 1 - cell.col)
    private fun rotate270(cell: Cell) = Cell(GRID_SIZE - 1 - cell.col, cell.row)

    /** All 52 shared-path cells, indexed by GLOBAL square 0..51 (matches PlayerColor.startOffset). */
    val pathCells: List<Cell> = redArmTemplate +
        redArmTemplate.map(::rotate90) +
        redArmTemplate.map(::rotate180) +
        redArmTemplate.map(::rotate270)

    val homeColumnCells: Map<PlayerColor, List<Cell>> = mapOf(
        PlayerColor.RED to redHomeColumnTemplate,
        PlayerColor.GREEN to redHomeColumnTemplate.map(::rotate90),
        PlayerColor.YELLOW to redHomeColumnTemplate.map(::rotate180),
        PlayerColor.BLUE to redHomeColumnTemplate.map(::rotate270)
    )

    val baseCells: Map<PlayerColor, List<Cell>> = mapOf(
        PlayerColor.RED to redBaseTemplate,
        PlayerColor.GREEN to redBaseTemplate.map(::rotate90),
        PlayerColor.YELLOW to redBaseTemplate.map(::rotate180),
        PlayerColor.BLUE to redBaseTemplate.map(::rotate270)
    )

    /** Global square index this color's local path position 1..51 sits on. */
    fun globalSquare(color: PlayerColor, localPos: Int): Int =
        (color.startOffset + localPos - 1).mod(52)

    /** Resolves ANY engine position (0=base, 1..51=path, 52..57=home column, 58=finished) to a grid cell. */
    fun cellFor(color: PlayerColor, tokenId: Int, position: Int): Cell = when {
        position == Token.BASE -> baseCells.getValue(color)[tokenId]
        position in 1..51 -> pathCells[globalSquare(color, position)]
        position in 52..57 -> homeColumnCells.getValue(color)[position - 52]
        else -> CENTER // finished tokens stack visually at center; draw layer offsets them slightly
    }
}
