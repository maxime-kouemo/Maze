package maze.labyrinth.table

/**
 * Project: Labyrinth
 * Package: maze.labyrinth.table.Dot
 * Created by mamboa on 2016-05-11
 * Updated by mamboa on 2025-04-20
 * Description:
 * This class defines a dot represented by a circle in a square
 */
/**
 * Class Dot
 * @param x the x coordinate of the dot
 * @param y the y coordinate of the dot
 * @param colorIndex the color index of the dot
 */
class Dot(
    x: Int, y: Int,//color index of the dot
    var colorIndex: Int
) {
    //square in whom the dot is drawn
    var square: Square
        private set

    init {
        square = Square(x, y)
    }

    fun setDot(x: Int, y: Int, colorIndex: Int) {
        square.column = x
        square.line = y
        this.colorIndex = colorIndex
    }
}
