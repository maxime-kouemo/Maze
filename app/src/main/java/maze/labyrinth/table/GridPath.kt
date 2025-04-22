package maze.labyrinth.table

/**
 * / **
 * Project: Labyrinth
 * Package: maze.labyrinth.table.GridPath
 * Created by mamboa on 2016-05-11.
 * Updated by mamboa on 2025-04-20
 * Description:
 * This class describes the a path of squares displayed on the grid
 */
class GridPath(var colorIndex: Int) {
    val gridPath = ArrayList<Square>()
    var isOver: Boolean = false

    /**
     * resetPath()
     * erase the grid path
     */
    fun resetPath() {
        gridPath.clear()
    }

    fun addSquare(uneCase: Square) {
        gridPath.add(uneCase)
    }

    /**
     * getGridPath()
     * returns the path formed by a list of squares
     */
    private fun getGridPath(): List<Square> {
        return gridPath
    }

    val theFirstSquare: Square?
        get() {
            if (!ifEmpty()) {
                return gridPath[0]
            }
            return null
        }

    val theLastSquare: Square?
        get() {
            if (!ifEmpty()) {
                return gridPath[gridPath.size - 1]
            }
            return null
        }

    fun ifEmpty(): Boolean {
        return gridPath.isEmpty()
    }

    /**
     * removeASquare()
     * removes from the grid path, all the other squares
     * from the last square to the one passed as parameter
     * @param aSquare: the square to be removed
     */
    fun removeASquare(aSquare: Square) {
        val indice = gridPath.indexOf(aSquare)
        if (indice >= 0) {
            for (i in gridPath.size - 1 downTo indice + 1) {
                gridPath.removeAt(i)
            }
        }
    }

    fun contains(aSquare: Square): Boolean {
        return getGridPath().contains(aSquare)
    }
}
