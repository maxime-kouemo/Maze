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
import androidx.core.view.isVisible
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

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    override var currentColor: Int?
        get() = gameViewModel.game?.currentColor
        set(value) {
            if (value != null) {
                gameViewModel.game?.currentColor = value
            }
        }

    override var isShadowOnPathAllowed: Boolean? = false

    private var gameNumber = 1

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private val stopwatchViewModel: StopwatchViewModel by viewModels()
    private val gameViewModel: GameViewModel by activityViewModels()
    private var isGameStarted = false
    private var isSolving = false

    companion object {
        private const val MAXIMUM_SIZE = 30
        private const val MINIMUM_SIZE = 3
        private const val DEFAULT_SIZE = 5
        private const val ARG_GRID_SIZE = "arg_grid_size"

        fun newInstance(gridSize: Int): GameFragment {
            val fragment = GameFragment()
            val args = Bundle()
            args.putInt(ARG_GRID_SIZE, gridSize)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

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
            it.setOnSolverCompletedListener {
                onSolverCompleted()
            }
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

        binding.titleTextView.text = getString(R.string.random_game, gameNumber)

        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.app_name)

        applySettingsChanges()
    }

    override fun onSolverCompleted() {
        isSolving = false
        binding.solutionButton.text = getString(R.string.solution)
        isGameStarted = false
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
        applySettingsChanges()
        if (stopwatchViewModel.stopwatchState.value.isGameStarted) {
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
            stopwatchViewModel.pauseStopWatch()
        } else {
            applySettingsChanges()
            if (stopwatchViewModel.stopwatchState.value.isRunning) {
                stopwatchViewModel.startStopWatch()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun update() {
        updateGridSize()
        updateSquares()

        if (gameViewModel.game?.ifGameIsOver() == true) {
            stopwatchViewModel.pauseStopWatch()
            displayVictoryDialog()
        }
    }

    override fun startStopWatch() {
        if (_binding == null) return
        if (!isGameStarted) return

        stopwatchViewModel.startStopWatch()
    }

    override fun setStartedGame(isGameStarted: Boolean) {
        this.isGameStarted = isGameStarted
        stopwatchViewModel.setStartedGame(isGameStarted)

        if (isGameStarted) {
            stopwatchViewModel.startStopWatch()
        }
    }

    private fun solveGame() {
        gameViewModel.run {
            if (isSolving && game?.isSolverVisualizationEnabled == true) {
                // Cancel current solving process
                game?.cancelSolver()
                isSolving = false
                binding.solutionButton.text = getString(R.string.solution)
                updateSquares() // Update UI after cancellation
                return@run
            }

            val solver =
                preferences.getString(getString(R.string.solving_algorithm), MazeSolver.DFS.value)
                    ?: MazeSolver.DFS.value
            game?.let {
                if (it.isSolverVisualizationEnabled) {
                    isSolving = true
                    binding.solutionButton.text = getString(R.string.cancel)
                }

                it.solve(MazeSolver.fromString(solver))
                updateGridSize()
                updateSquares() // Ensure UI is updated immediately after solve
                // Force a refresh to ensure the UI updates
                binding.numberOfSquaresTextView.post {
                    updateSquares()
                }
                stopwatchViewModel.pauseStopWatch()

                if (!it.isSolverVisualizationEnabled) {
                    isGameStarted = false // Game ended by solving
                }
            }
        }
    }

    private fun updateGridSize() {
        if (_binding == null) return
        val gridSize = gameViewModel.game?.gridSize ?: " "
        binding.levelTextView.text = getString(R.string.grid_size, gridSize, gridSize)
    }

    override fun updateSquares() {
        if (_binding == null) return
        binding.numberOfSquaresTextView.text =
            getString(R.string.filled_squares, gameViewModel.game?.numberOfFilledSquares())
    }

    private fun displayVictoryDialog() {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.congratulations)
            .setMessage(
                getString(
                    R.string.victory_time,
                    binding.stopWatchTextView.text
                )
            )
            .setPositiveButton(R.string.new_game) { _, _ -> resetGame() }
            .setCancelable(false)
            .show()
    }

    private fun resetGame() {
        gameViewModel.game?.let {
            // Cancel any ongoing solver operation
            if (isSolving) {
                it.cancelSolver()
                isSolving = false
                binding.solutionButton.text = getString(R.string.solution)
                updateSquares() // Update UI after cancellation
            }

            stopwatchViewModel.resetStopWatch()
            it.reset()
            it.initializeGameWithSize(preferences = preferences)

            updateGridSize()
            updateSquares()

            binding.titleTextView.text = getString(R.string.random_game, ++gameNumber)
            binding.stopWatchTextView.text = (buildString {
                append("0:00:00")
            })
            isGameStarted = false
            stopwatchViewModel.setStartedGame(false)
        }
    }

    private fun levelSettingDialog() {
        if (!isAdded || context == null) return
        val wasStarted = isGameStarted
        if (wasStarted) stopwatchViewModel.pauseStopWatch()

        val builder = AlertDialog.Builder(requireContext())
        val edittext = EditText(requireContext())
        edittext.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        edittext.setTextColor(Color.BLACK)
        edittext.hint = (gameViewModel.game?.gridSize ?: DEFAULT_SIZE).toString()

        builder.setMessage(getString(R.string.type_number, MINIMUM_SIZE, MAXIMUM_SIZE))
        builder.setTitle(getString(R.string.grid_size_simple))
        builder.setView(edittext)
        builder.setPositiveButton(R.string.load) { dialog, _ ->
        val value = edittext.text.toString()
            if (value.matches("^[0-9]+$".toRegex())) {
                val newGridSize = Integer.parseInt(value)
                if (newGridSize in MINIMUM_SIZE..MAXIMUM_SIZE) {
                    if (newGridSize != gameViewModel.game?.gridSize) {
                        gameViewModel.game?.gridSize = newGridSize
                        loadNewGridGame(newGridSize)
                        Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        if (wasStarted) startStopWatch()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.type_number, MINIMUM_SIZE, MAXIMUM_SIZE),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (wasStarted) startStopWatch()
                }
            } else if (value.isNotEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.digits_only),
                    Toast.LENGTH_SHORT
                ).show()
                if (wasStarted) startStopWatch()
            } else {
                if (wasStarted) startStopWatch()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
            if (wasStarted) startStopWatch()
        }
        builder.show()
    }

    private fun loadNewGridGame(newSize: Int) {
        val activity = requireActivity()
        val intent = Intent(activity, MainActivity::class.java).apply {
            putExtra("Id", newSize)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        activity.finish()
    }

    private fun applySettingsChanges() {
        gameViewModel.game?.let { currentGame ->
            currentGame.isSolverVisualizationEnabled =
                preferences.getBoolean(getString(R.string.solver_visualization), false)
            currentGame.solverVisualizationDelay =
                preferences.getInt(getString(R.string.solver_delay), resources.getInteger(R.integer.solver_delay_default_value))
            binding.solutionButton.isVisible =
                preferences.getBoolean(getString(R.string.enable_solver), false)

            isShadowOnPathAllowed =
                preferences.getBoolean(getString(R.string.shadow_on_path), false)
            val color = preferences.getInt(getString(R.string.path_color), GridView.BLUE1)
            currentGame.currentColor = color
            currentGame.invalidate()
        }
    }
}