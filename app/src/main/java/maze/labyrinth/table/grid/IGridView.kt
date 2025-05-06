package maze.labyrinth.table.grid

import androidx.annotation.StringRes
import maze.labyrinth.table.Dot

interface IGridView {
    fun initializeGame(gridState: GridState)
    fun invalidate()
    fun solveBFS(start: Dot, end: Dot, visualize: Boolean = false, delayMs: Long = 100)
    fun solveDFS(start: Dot, end: Dot, visualize: Boolean = false, delayMs: Long = 100)
    fun solveAStar(start: Dot, end: Dot, visualize: Boolean = false, delayMs: Long = 100)
    fun ifGameIsOver(): Boolean
    fun numberOfFilledSquares(): Int
    fun addAColor(color: Int)
    fun getColorID(color: Int): Int
    fun getString(@StringRes resId: Int): String
    fun reset()
    fun applyColorByItsIndex(colorIndex: Int)
}