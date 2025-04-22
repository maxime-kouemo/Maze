package maze.labyrinth.game

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import maze.labyrinth.table.grid.IGridView

class GameViewModel : ViewModel() {
    var game: Game? = null

    fun initializeGame(gridView: IGridView, preferences: SharedPreferences, gridSize: Int = -1) {
         if (game == null) {
            game = Game(gridView)
            game?.initializeGameWithSize(gridSize = gridSize, preferences = preferences)
         }
    }
}