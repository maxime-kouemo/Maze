package maze.labyrinth.table

/**
 * Project: Labyrinth
 * Package: maze.labyrinth.Tableau.Grid
 * Created by mamboa on 2016-05-11.
 * Description:
 * - draws a grid
 * - defines the game mecanics: visual effects, the game logic, the filling of the squares,
 * finger gestures on the screen
 */

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import maze.labyrinth.table.grid.GridState
import maze.labyrinth.table.grid.IGridView
import java.util.LinkedList
import java.util.PriorityQueue
import java.util.Random
import java.util.Stack
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

class GridView(context: Context, attrs: AttributeSet) : View(context, attrs), IGridView {

    private val gridSquaresPaint by lazy { Paint() }
    private val drawnPathsPaint by lazy { Paint() }
    private val shadowsPaint by lazy { Paint() }
    private val dotsPaint by lazy { Paint() }
    private val coloredCircle by lazy { Paint() }
    private val circonferenceRect by lazy { Rect() }
    private val reusableRect by lazy { Rect() }
    private val wallsPath by lazy { Path() }
    private val reusablePath by lazy { Path() }
    private val reusableClipRect by lazy { Rect() }
    private var isGameStarted = false

    // Dimensions of the game area and its squares (mWidth and mHeight)
    private var dimensions: Int = 0
    private var squareWidth: Int = 0
    private var squareHeight: Int = 0
    private var totalNumberOfSquares: Int = 0

    // Game state encapsulated in a dedicated data class
    private var gameState: GameState = GameState()

    // gridSquares holds all square data including neighbor information; maintained separately for efficient access during pathfinding
    private val gridSquares by lazy { arrayListOf<Square>() }
    private val colors: ArrayList<Int> by lazy { ArrayList() }

    var gridInterface: IGrid? = null

    private var isSolverRunning = false
    private var isSolverCancelled = false
    private var solverJob: Job? = null

    /**
     * ---------------------------------------------------------------------------------------------
     * Maze logic generation
     * ---------------------------------------------------------------------------------------------
     */
    // walls holds wall data for maze generation; kept separate from gridSquares for clarity in maze generation algorithm
    // Future optimization could explore deriving walls from gridSquares neighbor lists, but this would complicate generateMaze()
    private val walls by lazy { arrayListOf<Wall>() }
    private val currentPathToDraw by lazy { mutableSetOf<Int>() }
    private var mHeight: Int = 0
    private var mWidth: Int = 0
    private var generator = Random()
    private var ds: DisjointSet? = null

    companion object {
        val BLUE1 = Color.parseColor("#0070C0")
    }

    init {
        //squares of the grid of the grid
        gridSquaresPaint.style = Paint.Style.STROKE
        gridSquaresPaint.strokeWidth = 4f
        gridSquaresPaint.color = Color.BLACK
        gridSquaresPaint.strokeJoin = Paint.Join.ROUND
        gridSquaresPaint.isAntiAlias = true

        //aspect of the dots
        dotsPaint.style = Paint.Style.FILL
        drawnPathsPaint.style = Paint.Style.STROKE

        //aspect of the path
        drawnPathsPaint.strokeCap = Paint.Cap.ROUND
        drawnPathsPaint.strokeJoin = Paint.Join.ROUND
        drawnPathsPaint.isAntiAlias = true
        shadowsPaint.style = Paint.Style.FILL
        coloredCircle.style = Paint.Style.FILL

        totalNumberOfSquares = 0
        // Initialize wallsPath for batch drawing
        wallsPath.reset()
        reusablePath.reset()
        reusableClipRect.setEmpty()
    }

    override fun initializeGame(gridState: GridState) {
        reset()
        isGameStarted = true
        dimensions = gridState.gridSize
        totalNumberOfSquares = dimensions * dimensions
        if (gridSquares.size != 0)
            gridSquares.clear()
        loadTheSquares(dimensions)

        gameState.displayedDots.clear()
        gameState.displayedDots.addAll(gridState.dots)
        gameState.gridPaths.clear()
        val numberOfColors = gridState.dots.size / 2
        for (i in 0 until numberOfColors)
            gameState.gridPaths.add(GridPath(gridState.dots[2 * i].colorIndex))

        initializeMaze(dimensions, dimensions, gridState.seed)
        generateMaze()
    }


    override fun onSizeChanged(a: Int, b: Int, x: Int, y: Int) {
        //thickness of the line
        val line = max(1, gridSquaresPaint.strokeWidth.toInt())
        squareWidth = (a - (this.paddingRight + this.paddingLeft + line)) / dimensions
        squareHeight = (b - (this.paddingTop + this.paddingBottom + line)) / dimensions
        //we redefine the thickness of the line (here line is != line vs column
        drawnPathsPaint.strokeWidth = squareWidth.toFloat() / 4
    }

    override fun onDraw(canvas: Canvas) {
        // Precompute bounds for circonferenceRect
        if (circonferenceRect.isEmpty) {
            circonferenceRect.set(
                conversionFromGridToScreenX(0),  // left
                conversionFromGridToScreenY(0),     // top
                squareWidth * dimensions,          // right
                squareHeight * dimensions
            )       // bottom
        }
        canvas.drawRect(circonferenceRect, gridSquaresPaint)

        //2- we draw the walls
        drawMazeWalls(canvas)

        //3- we draw the dots
        drawDots(canvas)

        //4- we add colors to the filled squares
        for (i in 0 until gameState.gridPaths.size) {
            if (gameState.gridPaths[i] !== gameState.currentlyDrawnGridPath)
                drawPath(canvas, gameState.gridPaths[i])
        }

        //5- draw the active path
        gameState.currentlyDrawnGridPath?.let { drawPath(canvas, it) }

        //6- draw the shadow of the squares
        drawCircleAroundTheFinger(canvas)
        // Avoid unnecessary invalidate calls here
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = this.measuredWidth - (this.paddingLeft + this.paddingRight)
        val height = this.measuredHeight - (this.paddingTop + this.paddingBottom)
        val size = kotlin.math.min(height, width)
        //new dimension of the image
        this.setMeasuredDimension(
            size + paddingLeft + paddingRight,
            size + paddingTop + paddingBottom
        )
    }

    /**
     * Events on the screen : finger gestures
     * @param event
     * @return
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        val column = conversionFromScreenToGridX(x)
        val line = conversionFromScreenToGridY(y)
        // Cache the last touched grid coordinates to avoid recalculation
        val cachedSquare = Square(column, line)

        if (isOutOfBounds(column, line)) {
            handleOutOfBounds()
            return true
        }

        // Launch the stopwatch if game just started
        startGameIfNeeded()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleActionDown(cachedSquare, x, y)
            MotionEvent.ACTION_MOVE -> handleActionMove(cachedSquare, x, y)
            MotionEvent.ACTION_UP -> handleActionUp()
        }

        // Refresh the view
        invalidate()
        invalidateParentActivity()
        return true
    }

    /**
     * Checks if the touch coordinates are out of the grid bounds.
     * @param column the grid column
     * @param line the grid row
     * @return true if out of bounds, false otherwise
     */
    private fun isOutOfBounds(column: Int, line: Int): Boolean {
        return column >= dimensions || line >= dimensions || column < 0 || line < 0
    }

    /**
     * Handles the logic for when touch is out of bounds.
     */
    private fun handleOutOfBounds() {
        gameState.currentlyDrawnGridPath?.let {
            updateIncompletePath(it)
            displayTheActivePath()
            gridInterface?.update()
        }
        gameState.pressedDot = null
    }

    /**
     * Starts the game stopwatch if the game hasn't started yet.
     */
    private fun startGameIfNeeded() {
        if (isGameStarted) {
            gridInterface?.startStopWatch()
            gridInterface?.setStartedGame(true)
            isGameStarted = false
        }
    }

    /**
     * Handles the logic for MotionEvent.ACTION_DOWN.
     * @param cachedSquare the square touched
     * @param x the x-coordinate of the touch
     * @param y the y-coordinate of the touch
     */
    private fun handleActionDown(cachedSquare: Square, x: Int, y: Int) {
        val gridPath = getASquaresPath(cachedSquare, true)
        gridPath?.let {
            if (ifDotPresent(cachedSquare)) {
                it.resetPath()
                it.addSquare(Square(cachedSquare.column, cachedSquare.line))
                updateIncompletePath(it)
                gridInterface?.updateSquares()
            } else {
                it.removeASquare(cachedSquare)
                gridInterface?.updateSquares()
            }
            gameState.currentlyDrawnGridPath = it
            gameState.pressedDot = Square(x, y)
        }
    }

    /**
     * Handles the logic for MotionEvent.ACTION_MOVE.
     * @param cachedSquare the square touched
     * @param x the x-coordinate of the touch
     * @param y the y-coordinate of the touch
     */
    private fun handleActionMove(cachedSquare: Square, x: Int, y: Int) {
        gameState.currentlyDrawnGridPath?.let { gridPath ->
            gameState.pressedDot = Square(x, y)
            // If the square is adjacent to the last square of the active path
            if (cachedSquare != gridPath.theLastSquare && !gridPath.ifEmpty()) {
                if (isActionValid(cachedSquare)) {
                    handlePathUpdate(cachedSquare, gridPath)
                }
            }
        }
    }

    /**
     * Handles updating the path based on the touched square.
     * @param cachedSquare the square touched
     * @param gridPath the current grid path being modified
     */
    private fun handlePathUpdate(cachedSquare: Square, gridPath: GridPath) {
        if (gridPath.contains(cachedSquare)) {
            // Remove a square from the path if it is already present in the path
            gridPath.removeASquare(cachedSquare)
            updateIncompletePath(gridPath)
            gridInterface?.updateSquares()
        } else {
            gridPath.addSquare(cachedSquare)
            gridInterface?.updateSquares()
            // If the square contains the last dot
            val lastDot = getTheDotInASquare(cachedSquare)
            if (lastDot != null && cachedSquare != gridPath.theFirstSquare) {
                updateIncompletePath(gridPath)
                displayTheActivePath()
                updateCompletePath(lastDot)
                gridInterface?.update()
                gameState.pressedDot = null
            }
        }
    }

    /**
     * Handles the logic for MotionEvent.ACTION_UP.
     */
    private fun handleActionUp() {
        gameState.currentlyDrawnGridPath?.let { gridPath ->
            updateIncompletePath(gridPath)
            displayTheActivePath()
            gridInterface?.update()
        }
    }

    private fun drawMazeWalls(canvas: Canvas) {
        if (wallsPath.isEmpty) {
            for (i in 0 until walls.size) {
                val wall = walls[i]
                //a wall can be drawn from the coordinates of either the square1 or square2

                val square1 = gridSquares[wall.square1]
                if (wall.horizontal) { // if the wall is horizontal
                    val x = conversionFromGridToScreenX(square1.column)
                    val y = conversionFromGridToScreenY(square1.line)
                    val x2 =
                        conversionFromGridToScreenX(square1.column) + squareWidth //as we are talking about squares, mWidth or mHeight are the same
                    val y2 = conversionFromGridToScreenY(square1.line)
                    wallsPath.moveTo(x.toFloat(), y.toFloat())
                    wallsPath.lineTo(x2.toFloat(), y2.toFloat())
                } else { // if the wall is vertical
                    val x = conversionFromGridToScreenX(square1.column)
                    val y = conversionFromGridToScreenY(square1.line)
                    val x2 = conversionFromGridToScreenX(square1.column)
                    val y2 = conversionFromGridToScreenY(square1.line) + squareWidth
                    wallsPath.moveTo(x.toFloat(), y.toFloat())
                    wallsPath.lineTo(x2.toFloat(), y2.toFloat())
                }
            }
        }
        canvas.drawPath(wallsPath, gridSquaresPaint)
    }

    /**
     * drawDots
     * draw a dot with a circle form
     * @param canvas current canvas
     */
    private fun drawDots(canvas: Canvas) {
        for (i in 0 until gameState.displayedDots.size) {
            val dot = gameState.displayedDots[i]
            dotsPaint.color = colors[dot.colorIndex]
            val origin = dot.square
            canvas.drawCircle(
                getScreenXForColumn(origin.column),
                getScreenYForRow(origin.line),
                (squareWidth / 3).toFloat(),
                dotsPaint
            )
        }
    }

    /**
     * drawPath
     * draw the path defined by the user on the grid
     * @param canvas   current canvas
     * @param aGridPath path define by the finger on the screen
     */
    private fun drawPath(canvas: Canvas, aGridPath: GridPath) {
        if (!aGridPath.ifEmpty()) {
            // draw shadow on each square containing a path
            if (gridInterface?.isShadowOnPathAllowed == true) {
                for (square in aGridPath.gridPath) {
                    //here we fill the squares of the path with pale color for some visual effects
                    drawSquaresShadow(canvas, square, aGridPath)
                }
            }

            // we draw the path using reusablePath to avoid creating new Path objects
            reusablePath.reset()
            val squaresList = aGridPath.gridPath
            if (squaresList.isNotEmpty()) {
                var touchedSquare = squaresList[0]
                reusablePath.moveTo(
                    getScreenXForColumn(touchedSquare.column),
                    getScreenYForRow(touchedSquare.line)
                )

                for (i in squaresList.indices) {
                    if (i > 0) {
                        touchedSquare = squaresList[i]
                        reusablePath.lineTo(
                            getScreenXForColumn(touchedSquare.column),
                            getScreenYForRow(touchedSquare.line)
                        )
                    }

                    val currentGridPath = gameState.currentlyDrawnGridPath
                    if (currentGridPath != null) {
                        if (i < squaresList.size - 1) {
                            val nextSquare = squaresList[i + 1]
                            if (aGridPath !== currentGridPath && currentGridPath.contains(nextSquare)) {
                                break
                            }
                        }
                    }
                }
                drawnPathsPaint.color = colors[aGridPath.colorIndex]
                canvas.drawPath(reusablePath, drawnPathsPaint)
            }
        }
    }

    /**
     * drawSquaresShadow
     * draws a shadow on an active square(for a visual effect)
     *
     * @param canvas     the canvas
     * @param aSquare  the current square
     * @param aPath    the current grid path
     */
    private fun drawSquaresShadow(canvas: Canvas, aSquare: Square, aPath: GridPath) {
        val x = conversionFromGridToScreenX(aSquare.column)
        val y = conversionFromGridToScreenY(aSquare.line)
        reusableRect.set(x, y, x + squareWidth, y + squareHeight)
        shadowsPaint.color = colors[aPath.colorIndex]
        shadowsPaint.alpha = 50
        canvas.drawRect(reusableRect, shadowsPaint)
    }

    /**
     * drawCircleAroundTheFinger
     * when the finger is on a square, this method displays a visual feedback
     *
     * @param canvas the current canvas
     */
    private fun drawCircleAroundTheFinger(canvas: Canvas) {
        val currentPressedDot = gameState.pressedDot
        val currentActiveGridPath = gameState.currentlyDrawnGridPath

        if (currentPressedDot != null && currentActiveGridPath != null) {
            reusableClipRect.set(canvas.clipBounds)
            reusableClipRect.inset(-squareWidth, -squareWidth)  //make the rect larger
            canvas.save()
            canvas.clipRect(reusableClipRect)
            coloredCircle.color = colors[currentActiveGridPath.colorIndex]
            coloredCircle.alpha = 50
            canvas.drawCircle(
                currentPressedDot.column.toFloat(),
                currentPressedDot.line.toFloat(),
                squareWidth.toFloat(),
                coloredCircle
            )
            canvas.restore()
        }
    }

    /**
     * loadTheSquares
     * load the squares and assign them ids according to given dimensions
     * Notice that we give one dimension because we suppose that we will always encounter squares
     * @param dimensions size of the grid
     */
    private fun loadTheSquares(dimensions: Int) {
        var k = 0
        for (i in 0 until dimensions)
            for (j in 0 until dimensions) {
                gridSquares.add(Square(i, j, k))
                k++
            }
    }

    private fun getCorrespondingSquareInGridSquares(tempSquare: Square?): Square? {
        return if (tempSquare == null) {
            null
        } else {
            gridSquares.find { it == tempSquare }
        }
    }

    /**
     * when two dots of the same color are joined by a path, this method updates the state of that
     * path from the list of paths
     *
     * @param fence the fence dot
     */
    private fun updateCompletePath(fence: Dot) {
        for (i in gameState.gridPaths.indices) {
            if (gameState.gridPaths[i].colorIndex == fence.colorIndex)
                gameState.gridPaths[i].isOver = true
        }
    }

    /**
     * it the active path is already complete, erasing even a square decreases the number of formed
     * tubes
     *
     * @param activePath
     */
    private fun updateIncompletePath(activePath: GridPath) {
        for (i in gameState.gridPaths.indices) {
            if (gameState.gridPaths[i].colorIndex == activePath.colorIndex)
                if (gameState.gridPaths[i].isOver)
                    gameState.gridPaths[i].isOver = false
        }
    }

    /**
     * returns the number of formed tubes
     * @return nombre de tubes
     */
    private fun numberOfFormedTubes(): Int {
        var number = 0
        for (i in gameState.gridPaths.indices)
            if (gameState.gridPaths[i].isOver)
                number++
        return number
    }

    /**
     * numberOfFilledSquares
     * returns the number of formed squares at a given time
     *
     * @return nombreCase
     */
    override fun numberOfFilledSquares(): Int {
        var numberOfCases = 0
        detectSquareOccupation()
        for (i in gridSquares.indices)
            if (gridSquares[i].numberOfPassages >= 1)
                numberOfCases++
        return numberOfCases
    }

    /**
     * detectSquareOccupation
     * this method searches each square in every gridpath (from the list of paths)
     * when a square matches a square on the grid, its number of passes increments
     */
    private fun detectSquareOccupation() {
        for (i in gridSquares.indices)
            gridSquares[i].numberOfPassages = 0

        //for each square on the grid
        for (i in gridSquares.indices) {
            //we go through each path
            for (j in gameState.gridPaths.indices) {
                //we go through each square of that path
                for (k in 0 until gameState.gridPaths[j].gridPath.size) {
                    if (gridSquares[i] == gameState.gridPaths[j].gridPath[k])
                        gridSquares[i].numberOfPassages++
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Screen vs Grid convertion
     * ---------------------------------------------------------------------------------------------
     */

    /**
     * conversionFromScreenToGridX
     * for a x-coordinate of a pixel coordinate on the screen, this method gives the column
     * the coordinate of the grid to which it belongs.
     * @param x the x coordinate
     * @return column
     */
    private fun conversionFromScreenToGridX(x: Int): Int {
        return floor(((x - this.paddingLeft).toFloat() / squareWidth).toDouble()).toInt()
    }

    /**
     * conversionFromScreenToGridY
     * for a y-coordinate of a pixel on the screen, this method gives the line
     * Coordinate grid to which it belongs
     * @param y  coordinate
     * @return row
     */
    private fun conversionFromScreenToGridY(y: Int): Int {
        return floor(((y - this.paddingTop).toFloat() / squareHeight).toDouble()).toInt()
    }

    /**
     * conversionFromGridToScreenX
     * For any column in the grid, this method gives the x-component of its abscissa into
     * pixel repository
     * @param column the component column in a grid
     * @return x
     */
    private fun conversionFromGridToScreenX(column: Int): Int {
        return this.paddingLeft + squareWidth * column
    }

    /**
     * conversionFromGridToScreenY
     * For any column in the grid, gives the x-component of its abscissa
     * in the pixel repository
     * @param row the component row in the grid
     * @return y
     */
    private fun conversionFromGridToScreenY(row: Int): Int {
        return this.paddingTop + squareHeight * row
    }

    /**
     * Utility method to get the screen X coordinate for a grid column
     * @param column the grid column
     * @return the screen X coordinate
     */
    private fun getScreenXForColumn(column: Int): Float {
        return (conversionFromGridToScreenX(column) + squareWidth / 2).toFloat()
    }

    /**
     * Utility method to get the screen Y coordinate for a grid row
     * @param row the grid row
     * @return the screen Y coordinate
     */
    private fun getScreenYForRow(row: Int): Float {
        return (conversionFromGridToScreenY(row) + squareHeight / 2).toFloat()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Screen vs Grid conversion over
     * ---------------------------------------------------------------------------------------------
     */


    /**
     * ifNeighbors
     * tels if two squares are adjacents
     * @param sqr1 the first square
     * @param sqr2 the second square
     * @return true if yes, false otherwise
     */
    private fun ifNeighbors(sqr1: Square, sqr2: Square): Boolean {
        return abs(sqr1.column - sqr2.column) + abs(sqr1.line - sqr2.line) == 1
    }

    /**
     * getTheDotInASquare()
     * @param aSquare the square from whom we need to know if it contains a dot
     * @return the dot if it exists, null otherwise
     */
    private fun getTheDotInASquare(aSquare: Square): Dot? {
        return gameState.displayedDots.find { it.square == aSquare }
    }

    /**
     * isWallBetween
     * @param sqr1 first square
     * @param sqr2 second square
     * @return if yes or no it exist a wall among these two squares
     */
    private fun isWallBetween(sqr1: Square, sqr2: Square): Boolean {
        return sqr1.neighborsWithWhomIShareAWall.find { it == sqr2.squareId } != null
    }

    /**
     * getThePathFromTheColor
     * get the grid path whose color index is passed as a parameter
     *
     * @param colorIndex index of the color
     * @return a path
     */
    private fun getThePathFromTheColor(colorIndex: Int): GridPath? {
        return gameState.gridPaths.find { it.colorIndex == colorIndex }
    }

    /**
     * getASquaresPath()
     * for a given square, we obtains the grid path to whom it belongs
     * @param aSquare  the square whose path we are looking for
     * @param ifActive if the square is active
     * @return the path if it exists
     */
    private fun getASquaresPath(aSquare: Square, ifActive: Boolean): GridPath? {
        // First, check existing paths for the square
        val pathFromExisting = findPathContainingSquare(aSquare, ifActive)
        if (pathFromExisting != null) {
            return pathFromExisting
        }

        // If not found in paths, check if the square contains a dot to start a new path
        return findPathFromDot(aSquare, ifActive)
    }

    /**
     * Finds a path that contains the specified square among existing paths.
     * @param aSquare the square to search for
     * @param ifActive whether the path should be considered active
     * @return the path if found, null otherwise
     */
    private fun findPathContainingSquare(aSquare: Square, ifActive: Boolean): GridPath? {
        val size = gameState.gridPaths.size
        for (i in 0 until size) {
            if (!ifActive || gameState.gridPaths[i] !== gameState.currentlyDrawnGridPath) {
                val theSquares = gameState.gridPaths[i].gridPath
                if (theSquares.contains(aSquare)) {
                    return gameState.gridPaths[i]
                }
            }
        }
        return null
    }

    /**
     * Finds a path based on a dot in the specified square.
     * @param aSquare the square to check for a dot
     * @param ifActive whether the path should be considered active
     * @return the path if a dot is found and a matching path exists, null otherwise
     */
    private fun findPathFromDot(aSquare: Square, ifActive: Boolean): GridPath? {
        val point = getTheDotInASquare(aSquare)
        if (point != null) {
            val colorIndex = point.colorIndex
            val gridPath = getThePathFromTheColor(colorIndex)
            if (!(gridPath === gameState.currentlyDrawnGridPath && ifActive)) {
                return gridPath
            }
        }
        return null
    }

    /**
     * isActionValid(aSquare: Square):
     * An action is valid when two squares are adjacent and aren't separated by a wall
     *
     * @param aSquare the neighboring box
     * @return true if the action is valid
     */
    private fun isActionValid(aSquare: Square): Boolean {
        val currentPath = gameState.currentlyDrawnGridPath ?: return false

        // Check if the squares are not adjacent
        if (currentPath.theLastSquare == null || !ifNeighbors(
                aSquare,
                currentPath.theLastSquare!!
            )
        ) {
            return false
        }

        // Check if there is a wall between the squares
        val square1 = getCorrespondingSquareInGridSquares(currentPath.theLastSquare)
        val square2 = getCorrespondingSquareInGridSquares(aSquare)
        if (square1 != null && square2 != null && isWallBetween(square1, square2)) {
            return false
        }

        // Check if the square contains a dot of another color
        return !hasDifferentColorDot(aSquare, currentPath.colorIndex)
    }

    /**
     * Checks if a square contains a dot of a different color than the specified color index.
     * @param aSquare the square to check
     * @param colorIndex the color index to compare against
     * @return true if the square has a dot of a different color, false otherwise
     */
    private fun hasDifferentColorDot(aSquare: Square, colorIndex: Int): Boolean {
        val size = gameState.displayedDots.size
        for (i in 0 until size) {
            if (colorIndex != gameState.displayedDots[i].colorIndex && gameState.displayedDots[i].square == aSquare) {
                return true
            }
        }
        return false
    }

    override fun reset() {
        for (i in gameState.gridPaths.indices) {
            //the following line updates the number of tubes
            gameState.gridPaths[i].isOver = false
            gameState.gridPaths[i].resetPath()
        }

        for (i in gridSquares.indices)
            gridSquares[i].numberOfPassages = 0
        gameState.reset()
        // Clear gridSquares to release memory; avoid duplicate clearing
        gridSquares.clear()
        // Clear walls to release memory; maintained separately for maze generation
        walls.clear()
        // Avoid duplicate clearing of gridSquares and walls
        // Reset wallsPath for next initialization
        wallsPath.reset()
        // Also reset reusablePath to ensure it's clean for the next use
        reusablePath.reset()
        // Reset reusableClipRect to ensure it's clean for the next use
        reusableClipRect.setEmpty()
    }

    /**
     * ifGameIsOver
     * if all the tubes are formed the game is over
     *
     * @return true if the game is over, false otherwise
     */
    override fun ifGameIsOver(): Boolean {
        return numberOfFormedTubes() == gameState.displayedDots.size / 2
    }

    /**
     * initialize the maze with all its walls
     */
    private fun initializeMaze(w: Int, h: Int, seed: Long? = null) {
        mWidth = w
        mHeight = h

        // Initialization of the maze with all its walls
        walls.clear()
        for (i in 0 until mHeight) {
            for (j in 0 until mWidth) {
                if (i > 0)
                //horizontal walls
                    walls.add(Wall(j + i * mHeight, j + (i - 1) * mHeight, false))
                if (j > 0)
                //vertical walls
                    walls.add(Wall(j + i * mHeight, j - 1 + i * mHeight, true))
            }
        }

        // Random sorting of walls
        generator = if (seed != null) Random(seed) else Random()
        for (i in walls.indices) {
            //randomly generating an index
            val randomWall = generator.nextInt(walls.size)
            //permutation
            val tempWall = walls[i]
            walls[i] = walls[randomWall]
            walls[randomWall] = tempWall
        }

        ds = DisjointSet(mWidth * mHeight)
    }

    /**
     * we generate the maze. It consist in randomly removing some walls from the grid
     */
    private fun generateMaze() {
        for (i in walls.indices.reversed()) {
            //if two walls are not part of the same set
            if (ds?.find(walls[i].square1) != ds?.find(walls[i].square2)) {
                //we create a link between the both in the disjoint set
                ds?.union(walls[i].square1, walls[i].square2)

                //we get the two adjacent squares
                val wall = walls[i]
                val sqr1 = gridSquares[wall.square1]
                val sqr2 = gridSquares[wall.square2]
                //each square is put in the list of neighbors of his neighbors without wall
                sqr1.neighborsWithWhomIDontShareAWall.add(sqr2.squareId)
                sqr2.neighborsWithWhomIDontShareAWall.add(sqr1.squareId)

                //we remove the wall
                walls.removeAt(i)
            } else {
                //we get the two adjacent squares
                val wall = walls[i]
                val sqr1 = gridSquares[wall.square1]
                val sqr2 = gridSquares[wall.square2]
                //each square is put in the list of his neighbors with wall
                sqr1.neighborsWithWhomIShareAWall.add(sqr2.squareId)
                sqr2.neighborsWithWhomIShareAWall.add(sqr1.squareId)
            }
        }
        // we precompute neighbors for all squares to ensure consistency
        // This step is redundant since it's done above, but ensures completeness if logic changes
        for (i in 0 until gridSquares.size) {
            gridSquares[i].neighborsWithWhomIDontShareAWall // Access to ensure it's initialized if needed
        }
    }

    /**
     * for each square of the squarelist, erases its path history
     */
    private fun resetPathHistory() {
        for (square in gridSquares) {
            square.historyPath.clear()
        }
    }

    private fun displaySolutionPath(solutionPath: MutableList<Int>) {
        /*if (colors.size == 0)
            loadAllColors()*/
        val solutionsPath = GridPath(gridInterface?.currentColor ?: 0) // current color

        currentPathToDraw.addAll(solutionPath)
        for (i in currentPathToDraw) {
            solutionsPath.addSquare(gridSquares[i])
            gridSquares[i].numberOfPassages++ // updates the number of filled squares
        }

        //refresh
        invalidate()

        // TODO uncomment if necessary
        invalidateParentActivity()

        gameState.gridPaths.clear()
        gameState.gridPaths.add(solutionsPath)
        currentPathToDraw.clear()
    }

    override fun cancelSolver() {
        if (isSolverRunning) {
            isSolverCancelled = true
            solverJob?.cancel()
            isSolverRunning = false
            resetSolverState()
            // Ensure the UI is updated after cancellation
            invalidate()
        }
    }

    private fun resetSolverState() {
        isSolverCancelled = false
        currentPathToDraw.clear()
        // Clear all solver-related paths from game state
        gameState.gridPaths.clear()
        // Reset path history for all squares to avoid lingering data
        resetPathHistory()
        invalidate()
    }

    override fun solveDFS(start: Dot, end: Dot, isVisualized: Boolean, delayPerStepInMs: Long) {
        if (isSolverRunning) return
        val startTime = System.nanoTime()
        val startSquare = getSquareIdGivenADot(start)
        val endSquare = getSquareIdGivenADot(end)

        isSolverRunning = true
        isSolverCancelled = false
        solverJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val solutionPath = solveDFS(startSquare, endSquare, isVisualized, delayPerStepInMs)
                val endTime = System.nanoTime()
                val durationMs = (endTime - startTime) / 1_000_000.0
                Log.d("GridView", "DFS Solving Time: $durationMs ms")

                withContext(Dispatchers.Main) {
                    if (!isSolverCancelled) {
                        displaySolutionPath(solutionPath)
                        resetPathHistory()
                        // Notify game that solver is complete
                        if (isVisualized) {
                            gridInterface?.onSolverCompleted()
                        }
                    }
                }
            } finally {
                isSolverRunning = false
            }
        }
    }

    private suspend fun solveDFS(
        start: Int,
        end: Int,
        visualize: Boolean,
        delayMs: Long
    ): MutableList<Int> {
        val frontier = Stack<Int>()
        frontier.push(start)
        val visitedNodes = ArrayList<Int>()
        var currentNode = start

        while (!frontier.isEmpty() && !isSolverCancelled) {
            visitedNodes.add(currentNode)

            if (visualize) {
                val currentPath = gridSquares[currentNode].historyPath + currentNode
                visualizeStep(currentPath, delayMs)
            }

            if (currentNode == end) {
                break
            } else {
                if (gridSquares[currentNode].neighborsWithWhomIDontShareAWall.isNotEmpty()) {
                    for (i in 0 until gridSquares[currentNode].neighborsWithWhomIDontShareAWall.size) {
                        val neighbor = gridSquares[currentNode].neighborsWithWhomIDontShareAWall[i]
                        if (!visitedNodes.contains(neighbor)) {
                            val neighborNodeSquare = gridSquares[neighbor]
                            if (currentNode == start)
                                neighborNodeSquare.historyPath.add(currentNode)
                            else {
                                neighborNodeSquare.historyPath.addAll(gridSquares[currentNode].historyPath)
                                neighborNodeSquare.historyPath.add(currentNode)
                            }
                            frontier.push(neighbor)
                        }
                    }
                }
                currentNode = frontier.pop()
            }
        }
        return if (isSolverCancelled) mutableListOf() else arrayListOf<Int>().apply {
            addAll(gridSquares[end].historyPath)
            add(end)
        }
    }

    override fun solveBFS(start: Dot, end: Dot, isVisualized: Boolean, delayPerStepInMs: Long) {
        if (isSolverRunning) return
        val startTime = System.nanoTime()
        val startSquare = getSquareIdGivenADot(start)
        val endSquare = getSquareIdGivenADot(end)

        isSolverRunning = true
        isSolverCancelled = false
        solverJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val solutionPath = solveBFS(startSquare, endSquare, isVisualized, delayPerStepInMs)
                val endTime = System.nanoTime()
                val durationMs = (endTime - startTime) / 1_000_000.0
                Log.d("GridView", "BFS Solving Time: $durationMs ms")

                withContext(Dispatchers.Main) {
                    if (!isSolverCancelled) {
                        displaySolutionPath(solutionPath)
                        resetPathHistory()
                        // Notify game that solver is complete
                        if (isVisualized) {
                            gridInterface?.onSolverCompleted()
                        }
                    }
                }
            } finally {
                isSolverRunning = false
            }
        }
    }

    private suspend fun solveBFS(
        start: Int,
        end: Int,
        visualize: Boolean,
        delayMs: Long
    ): MutableList<Int> {
        val frontier = LinkedList<Int>()
        frontier.add(start)
        val visitedNodes = ArrayList<Int>()
        var currentNode = start

        while (!frontier.isEmpty() && !isSolverCancelled) {
            visitedNodes.add(currentNode)
            frontier.remove(currentNode)

            if (visualize) {
                val currentPath = gridSquares[currentNode].historyPath + currentNode
                visualizeStep(currentPath, delayMs)
            }

            if (currentNode == end) {
                break
            } else {
                if (gridSquares[currentNode].neighborsWithWhomIDontShareAWall.isNotEmpty()) {
                    for (i in 0 until gridSquares[currentNode].neighborsWithWhomIDontShareAWall.size) {
                        val neighbor = gridSquares[currentNode].neighborsWithWhomIDontShareAWall[i]
                        if (!visitedNodes.contains(neighbor)) {
                            val neighborNodeSquare = gridSquares[neighbor]
                            if (currentNode == start)
                                neighborNodeSquare.historyPath.add(currentNode)
                            else {
                                neighborNodeSquare.historyPath.addAll(gridSquares[currentNode].historyPath)
                                neighborNodeSquare.historyPath.add(currentNode)
                            }
                            frontier.add(neighbor)
                        }
                    }
                }
                currentNode = frontier.element()
            }
        }
        return if (isSolverCancelled) mutableListOf() else arrayListOf<Int>().apply {
            addAll(gridSquares[end].historyPath)
            add(end)
        }
    }

    override fun solveAStar(start: Dot, end: Dot, isVisualized: Boolean, delayPerStepInMs: Long) {
        if (isSolverRunning) return
        val startTime = System.nanoTime()
        val startSquare = getSquareIdGivenADot(start)
        val endSquare = getSquareIdGivenADot(end)

        isSolverRunning = true
        isSolverCancelled = false
        solverJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val solutionPath =
                    solveAStar(startSquare, endSquare, isVisualized, delayPerStepInMs)
                val endTime = System.nanoTime()
                val durationMs = (endTime - startTime) / 1_000_000.0
                Log.d("GridView", "A* Solving Time: $durationMs ms")

                withContext(Dispatchers.Main) {
                    if (!isSolverCancelled) {
                        displaySolutionPath(solutionPath)
                        resetPathHistory()
                        // Notify game that solver is complete
                        if (isVisualized) {
                            gridInterface?.onSolverCompleted()
                        }
                    }
                }
            } finally {
                isSolverRunning = false
            }
        }
    }

    private suspend fun solveAStar(
        start: Int,
        end: Int,
        visualize: Boolean,
        delayMs: Long
    ): MutableList<Int> {
        val openSet = PriorityQueue<Pair<Int, Int>>(compareBy { it.second })
        val openSetNodes = mutableSetOf<Int>()
        val closedSet = mutableSetOf<Int>()
        val cameFrom = mutableMapOf<Int, Int>()
        val gScore = mutableMapOf<Int, Int>().withDefault { Int.MAX_VALUE }
        val fScore = mutableMapOf<Int, Int>().withDefault { Int.MAX_VALUE }

        gScore[start] = 0
        fScore[start] = manhattanDistance(start, end)
        openSet.add(Pair(start, fScore[start]!!))
        openSetNodes.add(start)

        while (openSet.isNotEmpty() && !isSolverCancelled) {
            val current = openSet.poll().first
            openSetNodes.remove(current)

            if (visualize) {
                val currentPath = reconstructPath(current, cameFrom)
                visualizeStep(currentPath, delayMs)
            }

            if (current == end) {
                return reconstructPath(current, cameFrom).toMutableList()
            }

            closedSet.add(current)

            for (neighbor in gridSquares[current].neighborsWithWhomIDontShareAWall) {
                if (neighbor in closedSet) continue

                val tentativeGScore = gScore[current]!! + 1

                if (neighbor !in openSetNodes) {
                    openSet.add(Pair(neighbor, fScore.getValue(neighbor)))
                    openSetNodes.add(neighbor)
                } else if (tentativeGScore >= gScore.getValue(neighbor)) {
                    continue
                }

                cameFrom[neighbor] = current
                gScore[neighbor] = tentativeGScore
                fScore[neighbor] = gScore[neighbor]!! + manhattanDistance(neighbor, end)
                if (neighbor in openSetNodes) {
                    openSet.add(Pair(neighbor, fScore[neighbor]!!))
                }
            }
        }
        return mutableListOf()
    }

    private fun reconstructPath(current: Int, cameFrom: Map<Int, Int>): List<Int> {
        val path = mutableListOf(current)
        var currentNode = current
        while (currentNode in cameFrom) {
            currentNode = cameFrom[currentNode]!!
            path.add(0, currentNode)
        }
        return path
    }

    private suspend fun visualizeStep(path: List<Int>, delayMs: Long) {
        withContext(Dispatchers.Main) {
            displayCurrentPath(path)
            // Update the number of filled squares during visualization
            gridInterface?.updateSquares()
            delay(delayMs)
        }
    }

    private fun displayCurrentPath(path: List<Int>) {
        val solutionsPath = GridPath(gridInterface?.currentColor ?: 0)
        for (i in path) {
            solutionsPath.addSquare(gridSquares[i])
            gridSquares[i].numberOfPassages++
        }
        gameState.gridPaths.clear()
        gameState.gridPaths.add(solutionsPath)
        invalidate()
        invalidateParentActivity()
    }

    /**
     * add a color
     */
    override fun addAColor(color: Int) {
        val index = colors.indexOf(color)
        if (index == -1) {
            colors.add(color)
        }
    }

    override fun getColorID(color: Int): Int {
        return colors.indexOf(color)
    }

    override fun getString(resId: Int): String {
        return context.getString(resId)
    }

    override fun applyColorByItsIndex(colorIndex: Int) {
        //for the moment, there are only two points and 1 path
        if (colorIndex != -1) {
            //on the only path
            if (gameState.gridPaths.isNotEmpty()) {
                gameState.gridPaths[0].colorIndex = colorIndex
            }

            //on the two dots
            if (gameState.displayedDots.isNotEmpty()) {
                for (dot in gameState.displayedDots) {
                    dot.colorIndex = colorIndex
                }
            }
        }
    }

    private fun invalidateParentActivity() {
        (getLifecycleFromContext() as? AppCompatActivity)?.window?.decorView?.findViewById<View>(
            android.R.id.content
        )?.invalidate()
    }

    private fun getLifecycleFromContext(): LifecycleOwner? {
        var testContext = context
        while (testContext is ContextWrapper) {
            if (testContext is LifecycleOwner) {
                return testContext
            }
            testContext = testContext.baseContext
        }

        return null
    }

    /**
     * Data class to encapsulate game state for better modularity and clarity
     */
    data class GameState(
        var gridPaths: ArrayList<GridPath> = arrayListOf(),
        var displayedDots: ArrayList<Dot> = arrayListOf(),
        var currentlyDrawnGridPath: GridPath? = null,
        var pressedDot: Square? = null
    ) {
        fun reset() {
            for (i in gridPaths.indices) {
                gridPaths[i].isOver = false
                gridPaths[i].resetPath()
            }
            gridPaths.clear()
            currentlyDrawnGridPath = null
            pressedDot = null
            displayedDots.clear()
        }
    }

    private fun displayTheActivePath() {
        gameState.currentlyDrawnGridPath?.let {
            for (square in it.gridPath) {
                gridInterface?.updateSquares()
            }
            gameState.currentlyDrawnGridPath = null
        }
    }

    private fun ifDotPresent(aSquare: Square): Boolean {
        return getTheDotInASquare(aSquare) != null
    }

    private fun manhattanDistance(node: Int, goal: Int): Int {
        val nodeSquare = gridSquares[node]
        val goalSquare = gridSquares[goal]
        return abs(nodeSquare.column - goalSquare.column) + abs(nodeSquare.line - goalSquare.line)
    }

    private fun getSquareIdGivenADot(point: Dot): Int {
        val squareIndex = gridSquares.indexOfFirst { it == point.square }
        return if (squareIndex > -1) squareIndex else 0
    }
}

interface IGrid {
    var isShadowOnPathAllowed: Boolean?
    var currentColor: Int?
    fun update()
    fun updateSquares()
    fun startStopWatch()
    fun setStartedGame(isGameStarted: Boolean)
    fun onSolverCompleted()
}