package maze.labyrinth.game

enum class MazeSolver(val value: String) {
    BFS("bfs"),
    DFS("dfs"),
    A_STAR("a_star");

    companion object {
        fun fromString(value: String): MazeSolver {
            return when (value) {
                "bfs" -> BFS
                "dfs" -> DFS
                "a_star" -> A_STAR
                else -> throw IllegalArgumentException("Unknown solver type: $value")
            }
        }
    }
}