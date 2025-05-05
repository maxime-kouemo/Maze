package maze.labyrinth.game

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import maze.labyrinth.MainActivity
import maze.labyrinth.R
import maze.labyrinth.databinding.FragmentGameBinding
import maze.labyrinth.table.GridView
import maze.labyrinth.table.IGrid
import maze.labyrinth.table.grid.IGridView
import java.util.Locale


class GameFragment : Fragment(), IGrid {

    // Use Fragment binding
    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!! // Non-null assertion operator

    override var currentColor: Int?
        get() = gameViewModel.game?.currentColor
        set(value) {
            if (value != null) {
                gameViewModel.game?.currentColor = value
            } // Allow setting
        }

    override var isShadowOnPathAllowed: Boolean? = false

    private var gameNumber = 1

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private val stopwatchViewModel: StopwatchViewModel by viewModels()
    private val gameViewModel: GameViewModel by activityViewModels()
    private var isGameStarted = false

    // Constants moved from MainActivity companion object
    companion object {
        private const val MAXIMUM_SIZE = 30
        private const val MINIMUM_SIZE = 3
        private const val DEFAULT_SIZE = 5
        private const val ARG_GRID_SIZE = "arg_grid_size"

        // Factory method to pass arguments safely
        fun newInstance(gridSize: Int): GameFragment {
            val fragment = GameFragment()
            val args = Bundle()
            args.putInt(ARG_GRID_SIZE, gridSize)
            fragment.arguments = args
            return fragment
        }
    }

    // Inflate the layout
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Setup views and logic after the view is created
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve grid size from arguments or use default
        val gridSize = arguments?.getInt(ARG_GRID_SIZE, DEFAULT_SIZE) ?: DEFAULT_SIZE

        val gridView: IGridView = binding.gridView
        binding.gridView.gridInterface = this
        gameViewModel.initializeGame(gridView, preferences, gridSize)
        gameViewModel.game?.let {
            updateGridSize()
            updateSquares()
        }

        // Collect stopwatch state updates
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                stopwatchViewModel.stopwatchState.collect { state ->
                    updateStopwatchUI(state)
                }
            }
        }

        // Setup button listeners
        binding.genererButton.setOnClickListener { resetGame() }
        binding.sizeButton.setOnClickListener {
            levelSettingDialog()
        }
        binding.solutionButton.setOnClickListener { solveGame() }

        // Update initial title
        binding.titleTextView.text = getString(R.string.random_game, gameNumber)

        // Action bar setup is often handled by the Activity, but fragment can influence title etc.
        // If the activity toolbar should show specific title for this fragment:
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.app_name) // Or specific title


        // Initial color application
        applySettingsChanges()
    }

    private fun updateStopwatchUI(state: StopwatchState) {
        val secs = (state.updatedTime / 1000).toInt()
        val minutes = secs / 60
        val milliseconds = (state.updatedTime % 1000).toInt()
        binding.stopWatchTextView.text = (buildString {
            append("")
            append(minutes)
            append(":")
            append(String.format(Locale.getDefault(), "%02d", secs % 60))
            append(":")
            append(String.format(Locale.getDefault(), "%02d", milliseconds / 10))
        })
    }

    override fun onResume() {
        super.onResume()
        applySettingsChanges() // Apply color when resuming
        if (/*stopwatchViewModel.stopwatchState.value.isRunning && */stopwatchViewModel.stopwatchState.value.isGameStarted) {
            stopwatchViewModel.startStopWatch()
        }
    }

    override fun onPause() {
        super.onPause()
        stopwatchViewModel.pauseStopWatch()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        if (hidden) {
            // Fragment is hidden
            stopwatchViewModel.pauseStopWatch()
        } else {
            // Fragment is visible
            applySettingsChanges()
            if (stopwatchViewModel.stopwatchState.value.isRunning) {
                stopwatchViewModel.startStopWatch()
            }
        }
    }

    // Clean up binding when the view is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- GridInterface Implementation ---
    override fun update() {
        updateGridSize()
        updateSquares()

        if (gameViewModel.game?.ifGameIsOver() == true) {
            stopwatchViewModel.pauseStopWatch()
            displayVictoryDialog()
        }
    }

    override fun startStopWatch() {
        // Check if binding is null (can happen if called after onDestroyView)
        if (_binding == null) return
        if (!isGameStarted) return // Only start if game should be running

        stopwatchViewModel.startStopWatch()
    }

    override fun setStartedGame(isGameStarted: Boolean) {
        this.isGameStarted = isGameStarted
        stopwatchViewModel.setStartedGame(isGameStarted)

        if (isGameStarted) {
            stopwatchViewModel.startStopWatch()
        }
    }
    // --- End GridInterface ---

    private fun solveGame() {
        gameViewModel.run {
            val solver =
                preferences.getString(getString(R.string.solving_algorithm), MazeSolver.DFS.value)
                    ?: MazeSolver.DFS.value
            game?.let {
                it.solve(MazeSolver.fromString(solver))
                updateGridSize()
                updateSquares()
                stopwatchViewModel.pauseStopWatch()
                isGameStarted = false // Game ended by solving
            }
        }
    }

    private fun updateGridSize() {
        // Check binding validity
        if (_binding == null) return
        val gridSize = gameViewModel.game?.gridSize ?: " "
        binding.levelTextView.text = getString(R.string.grid_size, gridSize, gridSize)
    }

    override fun updateSquares() {
        // Check binding validity
        if (_binding == null) return
        binding.numberOfSquaresTextView.text =
            getString(R.string.filled_squares, gameViewModel.game?.numberOfFilledSquares())
    }

    private fun displayVictoryDialog() {
        // Check context validity
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.congratulations)
            .setMessage(
                getString(
                    R.string.victory_time,
                    binding.stopWatchTextView.text
                )
            ) // Show time
            .setPositiveButton(R.string.new_game) { _, _ -> resetGame() }
            // .setNegativeButton(R.string.close, null)
            .setCancelable(false) // Prevent dismissing by tapping outside
            .show()
    }

    private fun resetGame() {
        gameViewModel.game?.let {
            stopwatchViewModel.resetStopWatch()
            it.reset()
            // Use current grid size unless changed via dialog
            it.initializeGameWithSize(preferences = preferences)

            updateGridSize()
            updateSquares()

            binding.titleTextView.text = getString(R.string.random_game, ++gameNumber)
            binding.stopWatchTextView.text = (buildString { // Reset display
                append("0:00:00")
            })
            isGameStarted = false
            stopwatchViewModel.setStartedGame(false)
        }
    }

    private fun levelSettingDialog() {
        if (!isAdded || context == null) return // Check fragment state
        val wasStarted = isGameStarted
        if (wasStarted) stopwatchViewModel.pauseStopWatch()

        val builder = AlertDialog.Builder(requireContext())
        val edittext = EditText(requireContext()) // Use requireContext()
        edittext.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        edittext.setTextColor(Color.BLACK) // Consider using theme colors
        // Set current size as hint or text
        edittext.hint = (gameViewModel.game?.gridSize ?: DEFAULT_SIZE).toString()

        builder.setMessage(getString(R.string.type_number, MINIMUM_SIZE, MAXIMUM_SIZE))
        builder.setTitle(getString(R.string.grid_size_simple))
        builder.setView(edittext)
        builder.setPositiveButton(R.string.load) { dialog, _ -> // _ signifies unused whichButton
            val value = edittext.text.toString()
            if (value.matches("^[0-9]+$".toRegex())) {
                val newGridSize = Integer.parseInt(value)
                if (newGridSize in MINIMUM_SIZE..MAXIMUM_SIZE) {
                    if (newGridSize != gameViewModel.game?.gridSize) {
                        gameViewModel.game?.gridSize = newGridSize
                        loadNewGridGame(newGridSize) // Pass size to reload method
                        Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        // Same size, just resume stopwatch if needed
                        if (wasStarted) startStopWatch()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.type_number, MINIMUM_SIZE, MAXIMUM_SIZE),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (wasStarted) startStopWatch() // Resume if invalid input
                }
            } else if (value.isNotEmpty()) { // Only show error if input was not empty
                Toast.makeText(
                    requireContext(),
                    getString(R.string.digits_only),
                    Toast.LENGTH_SHORT
                ).show()
                if (wasStarted) startStopWatch() // Resume if invalid input
            } else {
                // Input was empty, just resume
                if (wasStarted) startStopWatch()
            }
            dialog.dismiss() // Dismiss dialog explicitly here
        }
        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
            if (wasStarted) startStopWatch() // Resume stopwatch if cancelled
        }
        builder.show()
    }

    // Reloads the hosting Activity with the new grid size in its intent extras
    private fun loadNewGridGame(newSize: Int) {
        val activity = requireActivity() // Get hosting activity
        val intent = Intent(activity, MainActivity::class.java).apply {
            putExtra("Id", newSize) // Use the constant key if defined in MainActivity
            // Flags might be needed depending on activity launch mode
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) // Standard reload flags
        }
        activity.startActivity(intent)
        activity.finish() // Close the current activity instance
    }

    private fun applySettingsChanges() {
        gameViewModel.game?.let { currentGame ->
            val color = preferences.getInt(getString(R.string.path_color), GridView.BLUE1)
            isShadowOnPathAllowed =
                preferences.getBoolean(getString(R.string.shadow_on_path), false)

            currentGame.currentColor = color
            currentGame.invalidate()
        }
    }
}
