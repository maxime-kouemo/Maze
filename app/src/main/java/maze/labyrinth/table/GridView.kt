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
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import maze.labyrinth.table.grid.GridState
import maze.labyrinth.table.grid.IGridView
import java.util.*
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
    private var isGameStarted = false

    // Dimensions of the game area and its squares (mWidth and mHeight)
    private var dimensions: Int = 0
    private var squareWidth: Int = 0
    private var squareHeight: Int = 0
    private var totalNumberOfSquares: Int = 0

    private val gridPaths by lazy { arrayListOf<GridPath>() } //list of grid paths, here there is only one path, but it can be more
    private val displayedDots by lazy { arrayListOf<Dot>() }
    private var pressedDot: Square? = null
    private var currentlyDrawnGridPath: GridPath? = null
    private val gridSquares by lazy { arrayListOf<Square>() }
    private val colors: ArrayList<Int> by lazy { ArrayList() }

    var gridInterface: IGrid? = null

    /**
     * ---------------------------------------------------------------------------------------------
     * Maze logic generation
     * ---------------------------------------------------------------------------------------------
     */
    private val walls by lazy { arrayListOf<Wall>() }
    private val currentPathToDraw by lazy { mutableSetOf<Int>() }
    private var mHeight: Int = 0
    private var mWidth: Int = 0
    private var generator = Random()
    private var ds: DisjointSet? = null
    private val currentNode = 0

    companion object {
        val BLUE1 = Color.parseColor("#0070C0")
        val RED1 = Color.parseColor("#FF0000")
        val GREEN1 = Color.parseColor("#00B050")
        val YELLOW = Color.parseColor("#FFFF0C")
        val ORANGE = Color.parseColor("#E36C0A")
        val BLUE2 = Color.parseColor("#C4EEF3")
        val GREEN2 = Color.parseColor("#93FF99")
        val RED2 = Color.parseColor("#943634")
        val GRAY = Color.parseColor("#938953")
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
    }

    /**
     * initializeGame
     * instal a game on the grid     *
     * @param
     */
    override fun initializeGame(gridState: GridState) {
        reset()
        isGameStarted = true
        dimensions = gridState.gridSize
        totalNumberOfSquares = dimensions * dimensions
        if (gridSquares.size != 0)
            gridSquares.clear()
        loadTheSquares(dimensions)

        displayedDots.clear()
        displayedDots.addAll(gridState.dots)
        gridPaths.clear()
        val numberOfColors = gridState.dots.size / 2
        for (i in 0 until numberOfColors)
            gridPaths.add(GridPath(gridState.dots[2 * i].colorIndex))

        initializeMaze(dimensions, dimensions) //important to define the squares first
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
        //1- Here we draw the circumference of the table
        circonferenceRect.set(
            conversionFromGridToScreenX(0),  // left
            conversionFromGridToScreenY(0),     // top
            squareWidth * dimensions,          // right
            squareHeight * dimensions
        )       // bottom
        canvas.drawRect(circonferenceRect, gridSquaresPaint)

        //2- we draw the walls
        drawMazeWalls(canvas)

        //3- we draw the dots
        drawDots(canvas)

        //4- we add colors to the filled squares
        for (i in 0 until gridPaths.size) {
            if (gridPaths[i] !== currentlyDrawnGridPath)
                drawPath(canvas, gridPaths[i])
        }

        //5- draw the active path
        currentlyDrawnGridPath?.let { drawPath(canvas, it) }

        //6- draw the shadow of the squares
        drawCircleAroundTheFinger(canvas)
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

        if (column >= dimensions || line >= dimensions || column < 0 || line < 0) {
            currentlyDrawnGridPath?.let {
                updateIncompletePath(it)
                displayTheActivePath()
                gridInterface?.update()
            }
            pressedDot = null
            //refresh
            invalidate()
            invalidateParentActivity()
            return true
        }
        //launch the stopwatch
        if (isGameStarted) {
            gridInterface?.startStopWatch()
            gridInterface?.setStartedGame(true)
            isGameStarted = false
        }

        val aSquare = Square(column, line)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val gridPath = getASquaresPath(aSquare, true)
                gridPath?.let {
                    if (ifDotPresent(aSquare)) {
                        it.resetPath()
                        it.addSquare(Square(column, line))
                        updateIncompletePath(it)
                        gridInterface?.updateSquares()
                    } else {
                        it.removeASquare(aSquare)
                        gridInterface?.updateSquares()
                    }
                    currentlyDrawnGridPath = it
                    pressedDot = Square(x, y)
                }
            }

            MotionEvent.ACTION_MOVE ->
                currentlyDrawnGridPath?.let { gridPath ->
                    pressedDot = Square(x, y)
                    //if the square is adjacent to the last square of the active path
                    if (aSquare != gridPath.theLastSquare && !gridPath.ifEmpty()) {
                        if (isActionValid(aSquare)) {
                            if (gridPath.contains(aSquare)) {
                                //Remove a square from the path if it is already present in the path
                                gridPath.removeASquare(aSquare)
                                updateIncompletePath(gridPath)
                                gridInterface?.updateSquares()
                            } else {
                                gridPath.addSquare(aSquare)
                                gridInterface?.updateSquares()
                                //if the square contains the last dot
                                val lastDot = getTheDotInASquare(aSquare)
                                if (lastDot != null && aSquare != gridPath.theFirstSquare) {
                                    updateIncompletePath(gridPath)
                                    displayTheActivePath()
                                    updateCompletePath(lastDot)
                                    gridInterface?.update()
                                    pressedDot = null
                                }
                            }//add a square to the path if that square isn't already in the path
                        }
                    }
                }

            MotionEvent.ACTION_UP ->
                currentlyDrawnGridPath?.let { gridPath ->
                    updateIncompletePath(gridPath)
                    displayTheActivePath()
                    gridInterface?.update()
                }
        }

        //refresh
        invalidate()
        invalidateParentActivity()
        return true
    }

    override fun invalidate() {
        super.invalidate()
    }

    private fun drawMazeWalls(canvas: Canvas) {
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
                canvas.drawLine(
                    x.toFloat(),
                    y.toFloat(),
                    x2.toFloat(),
                    y2.toFloat(),
                    gridSquaresPaint
                )
            } else { // if the wall is vertical
                val x = conversionFromGridToScreenX(square1.column)
                val y = conversionFromGridToScreenY(square1.line)
                val x2 = conversionFromGridToScreenX(square1.column)
                val y2 = conversionFromGridToScreenY(square1.line) + squareWidth
                canvas.drawLine(
                    x.toFloat(),
                    y.toFloat(),
                    x2.toFloat(),
                    y2.toFloat(),
                    gridSquaresPaint
                )
            }
        }
    }

    /**
     * drawDots
     * draw a dot with a circle form
     * @param canvas current canvas
     */
    private fun drawDots(canvas: Canvas) {
        for (i in 0 until displayedDots.size) {
            val dot = displayedDots[i]
            dotsPaint.color = colors[dot.colorIndex]
            val origin = dot.square
            canvas.drawCircle(
                (conversionFromGridToScreenX(origin.column) + squareWidth / 2).toFloat(),
                (conversionFromGridToScreenY(origin.line) + squareHeight / 2).toFloat(),
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
        if (!aGridPath.ifEmpty()/* && currentGridPath != null*/) {
            // draw shadow on each square containing a path
            if (gridInterface?.isShadowOnPathAllowed == true) {
                for (square in aGridPath.gridPath) {
                    //here we fill the squares of the path with pale color for some visual effects
                    drawSquaresShadow(canvas, square, aGridPath)
                }
            }

            // drawing the path
            val path = Path()
            val squaresList = aGridPath.gridPath
            var touchedSquare = squaresList[0]
            path.moveTo(
                (conversionFromGridToScreenX(touchedSquare.column) + squareWidth / 2).toFloat(),
                (conversionFromGridToScreenY(touchedSquare.line) + squareHeight / 2).toFloat()
            )

            for (i in squaresList.indices) {
                if (i > 0) {
                    touchedSquare = squaresList[i]
                    path.lineTo(
                        (conversionFromGridToScreenX(touchedSquare.column) + squareWidth / 2).toFloat(),
                        (conversionFromGridToScreenY(touchedSquare.line) + squareHeight / 2).toFloat()
                    )
                }

                val currentGridPath = currentlyDrawnGridPath
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
            canvas.drawPath(path, drawnPathsPaint)
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
        val rect = Rect()
        val x = conversionFromGridToScreenX(aSquare.column)
        val y = conversionFromGridToScreenY(aSquare.line)
        rect.set(x, y, x + squareWidth, y + squareHeight)
        shadowsPaint.color = colors[aPath.colorIndex]
        shadowsPaint.alpha = 50
        canvas.drawRect(rect, shadowsPaint)
    }

    /**
     * drawCircleAroundTheFinger
     * when the finger is on a square, this method displays a visual feedback
     *
     * @param canvas the current canvas
     */
    private fun drawCircleAroundTheFinger(canvas: Canvas) {
        val currentPressedDot = pressedDot
        val currentActiveGridPath = currentlyDrawnGridPath

        if (currentPressedDot != null && currentActiveGridPath != null) {
            val newRect = canvas.clipBounds
            newRect.inset(-squareWidth, -squareWidth)  //make the rect larger
            canvas.save()
            canvas.clipRect(newRect)
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
     * @param dimensions dimension du terrain
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
        for (i in gridPaths.indices) {
            if (gridPaths[i].colorIndex == fence.colorIndex)
                gridPaths[i].isOver = true
        }
    }

    /**
     * it the active path is already complete, erasing even a square decreases the number of formed
     * tubes
     *
     * @param activePath
     */
    private fun updateIncompletePath(activePath: GridPath) {
        for (i in gridPaths.indices) {
            if (gridPaths[i].colorIndex == activePath.colorIndex)
                if (gridPaths[i].isOver)
                    gridPaths[i].isOver = false
        }
    }

    /**
     * returns the number of formed tubes
     * @return nombre de tubes
     */
    private fun numberOfFormedTubes(): Int {
        var number = 0
        for (i in gridPaths.indices)
            if (gridPaths[i].isOver)
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
            for (j in gridPaths.indices) {
                //we go through each square of that path
                for (k in 0 until gridPaths[j].gridPath.size) {
                    if (gridSquares[i] == gridPaths[j].gridPath[k])
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
        return displayedDots.find { it.square == aSquare }
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
        return gridPaths.find { it.colorIndex == colorIndex }
    }

    /**
     * getASquaresPath()
     * for a given square, we obtains the grid path to whom it belongs
     * @param aSquare  the square whose path we are looking for
     * @param ifActive if the square is active
     * @return the path if it exists
     */
    private fun getASquaresPath(aSquare: Square, ifActive: Boolean): GridPath? {
        val size = gridPaths.size
        for (i in 0 until size) {
            if (!ifActive || gridPaths[i] !== currentlyDrawnGridPath) {
                val theSquares = gridPaths[i].gridPath
                if (theSquares.contains(aSquare))
                    return gridPaths[i]
            }
        }

        //if a square contains a dot, we start with a path of that color
        val point = getTheDotInASquare(aSquare)
        if (point != null) {
            val colorIndex = point.colorIndex
            val gridPath = getThePathFromTheColor(colorIndex)
            if (!(gridPath === currentlyDrawnGridPath && ifActive))
                return gridPath
        }
        return null
    }

    /**
     * ifDotPresent()
     * tells if a square contains a dot
     *
     * @param aSquare the square that we want to check
     * @return true if the dot is present, false otherwise
     */
    private fun ifDotPresent(aSquare: Square): Boolean {
        return displayedDots.find { it.square == aSquare } != null
    }

    /**
     * displayTheActivePath()
     * this method draws the path as long as the user's finger is going through the squares
     */
    private fun displayTheActivePath() {
        currentlyDrawnGridPath?.let {
            for (square in it.gridPath) {
                gridInterface?.updateSquares()
            }
            currentlyDrawnGridPath = null
        }
    }

    override fun reset() {
        for (i in gridPaths.indices) {
            //the following line updates the number of tubes
            gridPaths[i].isOver = false
            gridPaths[i].resetPath()
        }

        for (i in gridSquares.indices)
            gridSquares[i].numberOfPassages = 0
        gridPaths.clear()
        currentlyDrawnGridPath = null
        pressedDot = null
        gridSquares.clear()
        walls.clear()
        displayedDots.clear()
        gridSquares.clear()
        walls.clear()
        displayedDots.clear()
    }


    /**
     * ifGameIsOver
     * if all the tubes are formed the game is over
     *
     * @return true if the game is over, false otherwise
     */
    override fun ifGameIsOver(): Boolean {
        return numberOfFormedTubes() == displayedDots.size / 2
    }

    /**
     * isActionValid(aSquare: Square):
     * An action is valid when two squares are adjacent and aren't separated by a wall
     *
     * @param aSquare the neighboring box
     * @return true if the action is valid
     */
    private fun isActionValid(aSquare: Square): Boolean {
        currentlyDrawnGridPath?.let { path ->
            //if they aren't adjacent
            if (path.theLastSquare != null && !ifNeighbors(aSquare, path.theLastSquare!!))
                return false

            val square1 = getCorrespondingSquareInGridSquares(path.theLastSquare)
            val square2 = getCorrespondingSquareInGridSquares(aSquare)

            //if there is a wall between them
            if (square1 != null && square2 != null && isWallBetween(square1, square2))
                return false

            //if the square contains a dot of another color
            val size = displayedDots.size
            for (i in 0 until size) {
                if (path.colorIndex != displayedDots[i].colorIndex && displayedDots[i].square == aSquare)
                    return false
            }
            return true
        }
        return false
    }

    /**
     * initialize the maze with all its walls
     */
    private fun initializeMaze(w: Int, h: Int) {
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
        generator = Random()
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
    }

    /**
     * for each square of the squarelist, erases its path history
     */
    private fun resetPathHistory() {
        for (square in gridSquares) {
            square.historyPath.clear()
        }
    }

    private fun displaySolutionPath(currentNode: Int = this.currentNode) {
        /*if (colors.size == 0)
            loadAllColors()*/
        val solutionsPath = GridPath(gridInterface?.currentColor ?: 0) // current color

        //we record the final path where the currentNode passed as parameter is the end
        currentPathToDraw.addAll(gridSquares[currentNode].historyPath)
        currentPathToDraw.add(currentNode)
        for (i in currentPathToDraw) {
            solutionsPath.addSquare(gridSquares[i])
            gridSquares[i].numberOfPassages++ // updates the number of filled squares
        }

        //refresh
        invalidate()

        // TODO uncomment if necessary
        invalidateParentActivity()

        gridPaths.clear()
        gridPaths.add(solutionsPath)
        currentPathToDraw.clear()
    }

    override fun solveDFS(start: Dot, end: Dot) {
        val startSquare = getSquareIdGivenADot(start)
        val endSquare = getSquareIdGivenADot(end)
        solveDFS(startSquare, endSquare)
    }

    /**
     * solves the Maze with the DFS (Depth First Search) algorithm
     */
    private fun solveDFS(start: Int, end: Int) {
        val frontier = Stack<Int>()
        frontier.push(start)
        val visitedNodes = ArrayList<Int>()
        var currentNode = start
        while (!frontier.isEmpty()) {
            visitedNodes.add(currentNode)
            //if we found the solution
            if (currentNode == end) {
                break
            } else {
                if (gridSquares[currentNode].neighborsWithWhomIDontShareAWall.size > 0) {
                    for (i in 0 until gridSquares[currentNode].neighborsWithWhomIDontShareAWall.size) {
                        //if the node is unvisited we add it  currentNode -> child node
                        val neighbor = gridSquares[currentNode].neighborsWithWhomIDontShareAWall[i]
                        if (!visitedNodes.contains(neighbor)) {
                            //save the path from start (or the root) to the current node
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
            }//we continue the search
        }
        displaySolutionPath(end)
        resetPathHistory()
    }

    override fun solveBFS(start: Dot, end: Dot) {
        val startSquare = getSquareIdGivenADot(start)
        val endSquare = getSquareIdGivenADot(end)
        solveBFS(startSquare, endSquare)
    }

    /**
     * solves the Maze with the BFS (Breadth First Search) algorithm
     */
    private fun solveBFS(start: Int, end: Int) {
        val frontier = LinkedList<Int>()
        frontier.add(start)
        val visitedNodes = ArrayList<Int>()
        var currentNode = start
        while (!frontier.isEmpty()) {
            visitedNodes.add(currentNode)
            frontier.remove(currentNode)
            //if we found the solution
            if (currentNode == end) {
                break
            } else {
                if (gridSquares[currentNode].neighborsWithWhomIDontShareAWall.size > 0) {
                    for (i in 0 until gridSquares[currentNode].neighborsWithWhomIDontShareAWall.size) {
                        //if the node is unvisited we add it    currentNode -> child node
                        val neighbor = gridSquares[currentNode].neighborsWithWhomIDontShareAWall[i]
                        if (!visitedNodes.contains(neighbor)) {
                            //save the path from start (or the root) to the current node
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
            }//we continue the search
        }
        displaySolutionPath(end)
        resetPathHistory()
    }


    /**
     * For a given dot, returns the index of the square
     * @param point
     * @return
     */
    private fun getSquareIdGivenADot(point: Dot): Int {
        val squareIndex = gridSquares.indexOfFirst { it == point.square }
        return if (squareIndex > -1) squareIndex else 0
    }

    /**
     * loadAllColors()
     */
    fun loadAllColors() {
        colors.add(BLUE1)
        colors.add(RED1)
        colors.add(GREEN1)
        colors.add(YELLOW)
        colors.add(ORANGE)
        colors.add(BLUE2)
        colors.add(GREEN2)
        colors.add(RED2)
        colors.add(GRAY)
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

    /**
     * delete the color add as parameter. Plus, there is an information telling if the index
     * is already evaluated
     * @param color
     */
    private fun deleteAColor(colorToRemove: Int) {
        var index = colors.indexOf(colorToRemove)
        if (index != -1) {
            colors.removeAt(index)
        }
    }

    private fun numberOfColors(): Int {
        return colors.size
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
            if (gridPaths.isNotEmpty()) {
                gridPaths[0].colorIndex = colorIndex
            }

            //on the two dots
            if (displayedDots.isNotEmpty()) {
                for (dot in displayedDots) {
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
     * Todo:
     * start and stop watch should be done from within
     * invalidate decor
     */
}

interface IGrid {
    var isShadowOnPathAllowed: Boolean?
    var currentColor: Int?
    fun update()
    fun updateSquares()
    fun startStopWatch()
    fun setStartedGame(isGameStarted: Boolean)
}


