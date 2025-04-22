package maze.labyrinth.game

/**
 * Represents the state of a stopwatch.
 */
data class StopwatchState(
    val isRunning: Boolean = false,
    val elapsedTime: Long = 0L,
    val startTime: Long = 0L,
    val timeSwapBuff: Long = 0L,
    val updatedTime: Long = 0L,
    val isGameStarted: Boolean = false
)