package maze.labyrinth.game

import android.content.SharedPreferences
import maze.labyrinth.R
import maze.labyrinth.table.Dot
import maze.labyrinth.table.GridView
import maze.labyrinth.table.Square
import maze.labyrinth.table.grid.GridState
import maze.labyrinth.table.grid.IGridView
import java.util.Random
import kotlin.math.abs

/**
 * This class defines the different levels. Only when this class is installed that the game
 * can start
 * Created by mamboa on 01/02/2016
 */
class Game(private val gridView: IGridView) {

    var currentColor = 0
        set(value) {
            val cleanedValue = if (value == 0) GridView.BLUE1 else value
            gridView.addAColor(cleanedValue)
            val colorID = gridView.getColorID(cleanedValue)

            field = if (colorID == -1) {
                0
            } else {
                colorID
            }
            dot1?.colorIndex = colorID
            dot2?.colorIndex = colorID

            gridView.applyColorByItsIndex(field)
        }
    var gridSize = 0
    private val dots = ArrayList<Dot>()

    private var gameID = 0

    internal var dot1: Dot? = null
    internal var dot2: Dot? = null

    private var generator = Random()

    init {
        this.dots.addAll(dots)
    }

    /**
     *
     * @param gridSize
     * @return return a game (full of parameters)
     */
    fun initializeGameWithSize(gridSize: Int = -1, preferences: SharedPreferences? = null) {
        // load the color from preferences and apply it
        val tempColor = GridView.BLUE1
        val color =
            preferences?.getInt(gridView.getString(R.string.path_color), tempColor) ?: tempColor
        currentColor = color

        if (gridSize > -1) {
            this.gridSize = gridSize
        }

        //avoid  two points to be in the same square
        val tab = IntArray(4)
        generateDots(tab)

        // if the dots already exist just change their positions in the grid
        if (dot1 != null && dot2 != null) {
            dot1?.setDot(tab[0], tab[1], currentColor)
            dot2?.setDot(tab[2], tab[3], currentColor)
        } else {
            dot1 = Dot(tab[0], tab[1], currentColor)
            dot2 = Dot(tab[2], tab[3], currentColor)
        }
        dots.clear()
        dot1?.run { dots.add(this) }
        dot2?.run { dots.add(this) }

        gameID++

        val gridState = createGridState()
        gridView.initializeGame(gridState)
        gridView.invalidate() //refresh the view to display the changes
    }

    private fun createGridState(): GridState {
        return GridState(gridSize = gridSize, dots = dots)
    }

    /**
     * generate two dots and make sure they are not in the same square
     * @param tab
     */
    private fun generateDots(tab: IntArray) {
        var areDirectHorizontalNeighbors: Boolean
        var areDirectVerticalNeighbors: Boolean
        var areInTheSameRoom: Boolean
        do {
            for (i in tab.indices) {
                tab[i] = randomSize(gridSize)
            }
            val room1 = Square(tab[0], tab[1]).getSquareIdGivenDimensions(gridSize)
            val room2 = Square(tab[2], tab[3]).getSquareIdGivenDimensions(gridSize)

            areDirectHorizontalNeighbors = abs(room1 - room2) == gridSize
            areDirectVerticalNeighbors = abs(room1 - room2) < gridSize - 1
            areInTheSameRoom = room1 - room2 == 0
        } while (areInTheSameRoom || areDirectVerticalNeighbors || areDirectHorizontalNeighbors)
    }

    /**
     * returns a random integer between 0 and size -1
     * @param size
     * @return
     */
    private fun randomSize(size: Int): Int {
        return if (size == 0) 0 else generator.nextInt(size)
    }

    fun solve(mazeSolver: MazeSolver) {
        if (dot1 == null || dot2 == null) {
            throw IllegalStateException("Dots are not initialized")
        }
        when (mazeSolver) {
            MazeSolver.BFS -> gridView.solveBFS(dot1!!, dot2!!)
            MazeSolver.DFS -> gridView.solveDFS(dot1!!, dot2!!)
            MazeSolver.A_STAR-> gridView.solveAStar(dot1!!, dot2!!)
            else -> throw IllegalArgumentException("Unknown algorithm: ${mazeSolver.value}")
        }
    }

    fun reset() {
        gridView.reset()
    }

    fun ifGameIsOver(): Boolean {
        return gridView.ifGameIsOver()
    }

    fun numberOfFilledSquares(): Int {
        return gridView.numberOfFilledSquares()
    }

    fun invalidate() {
        gridView.invalidate()
    }
}
