package maze.labyrinth.utils

import android.content.Intent
import android.os.Parcelable
import androidx.core.content.IntentCompat

inline fun <reified T : Parcelable> Intent.parcelable2(key: String): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)