package maze.labyrinth.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the state of a stopwatch.
 */
class StopwatchViewModel : ViewModel() {

    private val _stopwatchState = MutableStateFlow(StopwatchState())
    val stopwatchState: StateFlow<StopwatchState> = _stopwatchState.asStateFlow()

    private var timerJob: Job? = null

    fun startStopWatch() {
        if (_stopwatchState.value.isRunning) return
        _stopwatchState.value = _stopwatchState.value.copy(
            isRunning = true,
            startTime = SystemClock.uptimeMillis()
        )
        timerJob = viewModelScope.launch {
            while (_stopwatchState.value.isRunning) {
                val timeInMilliseconds = SystemClock.uptimeMillis() - _stopwatchState.value.startTime
                val updatedTime = _stopwatchState.value.timeSwapBuff + timeInMilliseconds
                _stopwatchState.value = _stopwatchState.value.copy(
                    elapsedTime = timeInMilliseconds,
                    updatedTime = updatedTime
                )
                delay(10L) // Update every 10 milliseconds
            }
        }
    }

    fun pauseStopWatch() {
        timerJob?.cancel()
        _stopwatchState.value = _stopwatchState.value.copy(
            isRunning = false,
            timeSwapBuff = _stopwatchState.value.updatedTime
        )
    }

    fun resetStopWatch() {
        timerJob?.cancel()
        _stopwatchState.value = StopwatchState()
    }

    fun setStartedGame(isGameStarted: Boolean) {
        _stopwatchState.value = _stopwatchState.value.copy(isGameStarted = isGameStarted)
        if (isGameStarted) {
            // Resume stopwatch only if game was already started and we are resuming
            if (_stopwatchState.value.timeSwapBuff > 0 || _stopwatchState.value.elapsedTime > 0) {
                startStopWatch()
            }
        } else {
            pauseStopWatch()
        }
    }
}