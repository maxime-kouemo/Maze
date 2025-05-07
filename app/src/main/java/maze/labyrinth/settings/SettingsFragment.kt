package maze.labyrinth.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import maze.labyrinth.R

/**
 * SettingsFragment is a fragment that displays the settings for the app.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Listen for color changes
        findPreference<ColorPreference>("maze_color")?.setOnPreferenceChangeListener { _, newValue ->
            sharedPreferences.edit().putInt(getString(R.string.path_color), newValue as Int).apply()
            true // return true to update the preference
        }

        // Update the summary of the algorithm selection when it changes
        findPreference<ListPreference>("solver_algorithm")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                sharedPreferences.edit().putString(getString(R.string.solving_algorithm), newValue.toString()).apply()
                true
            }
        }

        // Update the summary of the algorithm selection when it changes
        findPreference<CheckBoxPreference>("shadow_on_path")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                sharedPreferences.edit().putBoolean(getString(R.string.shadow_on_path), newValue as Boolean).apply()
                true
            }
        }
        
        // Enable Solution Button switch
        findPreference<androidx.preference.SwitchPreferenceCompat>("enable_solver")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                sharedPreferences.edit().putBoolean(getString(R.string.enable_solver), newValue as Boolean).apply()
                true
            }
        }

        // Enable Solver Visualization switch
        findPreference<androidx.preference.SwitchPreferenceCompat>("solver_visualization")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                sharedPreferences.edit().putBoolean(getString(R.string.solver_visualization), newValue as Boolean).apply()
                true
            }
        }

        // Solver Visualization Delay (SeekBarPreference)
        findPreference<androidx.preference.SeekBarPreference>("solver_delay")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                sharedPreferences.edit().putInt(getString(R.string.solver_delay), newValue as Int)
                    .apply()
                true
            }
        }
        

        // Helper function to enable/disable solver settings
        fun updateSolverSettingsEnabled(enabled: Boolean) {
            findPreference<ListPreference>("solver_algorithm")?.isEnabled = enabled
            findPreference<androidx.preference.SwitchPreferenceCompat>("solver_visualization")?.isEnabled = enabled
            findPreference<androidx.preference.SeekBarPreference>("solver_delay")?.isEnabled = enabled
        }

        // Get references
        val enableSolverPref = findPreference<androidx.preference.SwitchPreferenceCompat>("enable_solver")
        val solverAlgorithmPref = findPreference<ListPreference>("solver_algorithm")
        val solverVisualizationPref = findPreference<androidx.preference.SwitchPreferenceCompat>("solver_visualization")
        val solverDelayPref = findPreference<androidx.preference.SeekBarPreference>("solver_delay")

        // Initial setup: ensure dependent solver settings are enabled/disabled based on Solution Button's value
        enableSolverPref?.let {
            val enabled = it.isChecked
            updateSolverSettingsEnabled(enabled)
        }

        // Listen for changes on 'Enable Solution Button'
        enableSolverPref?.setOnPreferenceChangeListener { preference, newValue ->
            val enabled = newValue as Boolean
            updateSolverSettingsEnabled(enabled)
            // Also persist the value as before
            sharedPreferences.edit()
                .putBoolean(getString(R.string.enable_solver), enabled)
                .apply()
            true
        }

    }
}