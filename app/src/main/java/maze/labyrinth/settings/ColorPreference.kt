package maze.labyrinth.settings

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import maze.labyrinth.R
import maze.labyrinth.databinding.PreferenceColorBinding
import maze.labyrinth.table.GridView

/**
 * Custom preference for selecting a color.
 *
 * @param context The context of the activity.
 * @param attrs The attribute set for the preference.
 */
class ColorPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private var currentColor: Int = 0
    private var colorPickerDialog: AlertDialog? = null

    init {
        layoutResource = R.layout.preference_color
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val binding = PreferenceColorBinding.bind(holder.itemView)
        currentColor = getCurrentColor(context)
        binding.colorPreview.setBackgroundColor(currentColor)
    }

    override fun onClick() {
        launchColorPikerDialog()
    }

    /**
     * Get the current color from the shared preferences.
     */
    private fun getCurrentColor(context: Context?): Int {
        context ?: return GridView.BLUE1
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getInt(context.getString(R.string.path_color), GridView.BLUE1)
    }

    private fun launchColorPikerDialog() {
        colorPickerDialog?.show() ?: run {
            colorPickerDialog = ColorPickerDialog.Builder(context)
                .setTitle(title.toString())
                .setPreferenceName(key)
                .setPositiveButton(context.getString(android.R.string.ok),
                    ColorEnvelopeListener { envelope, _ ->
                        val color = envelope.color
                        persistInt(color)
                        currentColor = color
                        notifyChanged()
                        super.callChangeListener(color)
                    }
                )
                .setNegativeButton(context.getString(R.string.cancel)) { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        }
    }
}