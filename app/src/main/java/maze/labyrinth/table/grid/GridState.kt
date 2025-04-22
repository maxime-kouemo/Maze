package maze.labyrinth.table.grid

import maze.labyrinth.table.Dot
import maze.labyrinth.table.Square

data class GridState(
    val gridSize: Int,
    val squares: List<Square>  = emptyList(),
    val dots: List<Dot> = emptyList(),
    val startPosition: Square? = null,
    val endPosition: Square? = null,
    val currentPath: List<Square> = emptyList(),
    val visitedSquares: Set<Square> = emptySet(),
    val isGenerating: Boolean = false,
    val isSolving: Boolean = false,
    val animationSpeed: Float = 1f,
    val gridScale: Float = 1f
)