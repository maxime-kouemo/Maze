package maze.labyrinth.table

import java.util.ArrayList

/**
 * Project: Labyrinth
 * Package: maze.labyrinth.Tableau.Square
 * Square (or room)
 * This class defines a square / room
 * A square can be empty at the beginning, in this case it can serve as a portion of a path
 * A square can contain a dot of a given color
 * The main grid at the begining is formed of a set of squares.
 * Created by mamboa on 28/01/2016.
 */
class Square {

    //allows to know all the paths that cross this box
    var line: Int = 0
    var column: Int = 0
    var numberOfPassages: Int = 0
    var squareId: Int = 0   // describes the id of the square in a grid (from top to bottom, from left to the right)
    var distance = -1
    var paths = ArrayList<Int>()  //used with disjoint method
    var neighborsWithWhomIShareAWall = ArrayList<Int>()
    var neighborsWithWhomIDontShareAWall = ArrayList<Int>()
    /**
     * returns the different squares traveled from the begining square, before reaching the
     * current one
     */
    val historyPath = ArrayList<Int>()  //use with DFS


    constructor(colonne: Int, ligne: Int, id: Int) {
        line = ligne
        column = colonne
        numberOfPassages = 0
        this.squareId = id
    }

    constructor(i: Int) {
        squareId = i
        distance = -1
        paths = ArrayList()
    }

    constructor(colonne: Int, ligne: Int) {
        line = ligne
        column = colonne
        numberOfPassages = 0
        squareId = 0
    }

    /**
     * get the index of a room given the dimensions of the whole grid
     * @param width
     * @return
     */
    fun getSquareIdGivenDimensions(width: Int): Int {
        if (squareId == 0)
            squareId = line + column * width
        return line + column * width
    }

    /**
     * equals
     * tells if the current square is equal to the one passed as parameter
     * @param tampon
     * @return
     */
    override fun equals(tampon: Any?): Boolean {
        if (tampon !is Square) {
            return false
        }
        val tampon1 = tampon as Square?
        return tampon1!!.column == this.column && tampon1.line == this.line
    }
}
