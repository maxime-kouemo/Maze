package maze.labyrinth

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import maze.labyrinth.databinding.ActivityMainBinding
import maze.labyrinth.game.GameFragment
import maze.labyrinth.settings.SettingsFragment


/**
 * Project: Labyrinth
 * Package: maze.labyrinth.MainActivity
 * Created by mamboa on 2016-05-11.
 * Description:
 * - Represents the activity displaying the main view
 */
class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding
    private var gridSize = 5

    companion object {
        private const val DEFAULT_SIZE = 5
        const val INTENT_EXTRA_GRID_SIZE = "Id"
        private const val TAG_GAME_FRAGMENT = "GAME_FRAGMENT_TAG"
        private const val TAG_SETTINGS_FRAGMENT = "SETTINGS_FRAGMENT_TAG"
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force portrait mode
        if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gridSize = intent.getIntExtra(INTENT_EXTRA_GRID_SIZE, DEFAULT_SIZE)

        if (savedInstanceState == null) {
            showGameFragment()
        }

        onBackPressedDispatcher.addCallback(
            this@MainActivity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when (getCurrentVisibleFragment()) {
                        is GameFragment -> {
                            // Show exit confirmation for game
                            showLeaveConfirmationDialog()
                        }
                        is SettingsFragment -> {
                            // Return to game
                            showGameFragment()
                        }
                        else -> {
                            // Default behavior
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }

                }
            })
    }

    private inline fun <reified T : Fragment> replaceFragment(
        createNewFragment: () -> T,
        tag: String,
        addToBackStack: Boolean = false
    ) {
        supportFragmentManager.beginTransaction().apply {
            // Try to find existing fragment
            val existingFragment = supportFragmentManager.findFragmentByTag(tag) as? T

            // Use existing fragment or create new one
            val fragmentToUse = existingFragment ?: createNewFragment()

            // Show the target fragment
            if (fragmentToUse.isAdded) {
                show(fragmentToUse)
            } else {
                add(R.id.game_fragment_container, fragmentToUse, tag)
            }

            // Hide all other fragments
            supportFragmentManager.fragments.forEach {
                if (it != fragmentToUse && it.isAdded) {
                    hide(it)
                }
            }

            // Optionally add to back stack
            if (addToBackStack) {
                addToBackStack(tag)
            }

            setReorderingAllowed(true)
        }.commit()
    }

    private fun showGameFragment() {
        // Customize the ActionBar
        supportActionBar?.apply {
            title = getString(R.string.app_name)
            setDisplayHomeAsUpEnabled(false)
            setHomeButtonEnabled(false) // Often handled by NavController now
            setDisplayShowHomeEnabled(true)
            setDisplayUseLogoEnabled(true)
            setLogo(R.mipmap.ic_launcher)
            setDisplayShowTitleEnabled(true)
        }
        replaceFragment(
            createNewFragment = { GameFragment.newInstance(gridSize) },
            tag = TAG_GAME_FRAGMENT
        )
    }

    private fun showSettingsFragment() {
        // Customize the ActionBar
        supportActionBar?.apply {
            title = getString(R.string.title_activity_settings)
            setDisplayHomeAsUpEnabled(true) // Show the Up button (optional)
            setDisplayShowHomeEnabled(false)
        }
        replaceFragment(
            createNewFragment = { SettingsFragment() },
            tag = TAG_SETTINGS_FRAGMENT,
            addToBackStack = true
        )
    }

    // Inflate the menu (including the settings item)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu) // Inflate the menu resource
        return true
    }

    // Handle menu item selections
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_color_picker_dialog -> {
                showSettingsFragment()
                true // Indicate item was handled
            }

            android.R.id.home -> {
                showGameFragment()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getCurrentVisibleFragment(): Fragment? {
        return supportFragmentManager.fragments.firstOrNull { fragment ->
            fragment.isAdded && !fragment.isHidden && fragment.view?.isVisible == true
        }
    }

    private fun showLeaveConfirmationDialog() {
        AlertDialog.Builder(this@MainActivity)
            .setTitle(R.string.leave)
            .setMessage(R.string.leave_question)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.cancel, null) // Simple cancel
            .show()
    }
}