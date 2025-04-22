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
    }
}