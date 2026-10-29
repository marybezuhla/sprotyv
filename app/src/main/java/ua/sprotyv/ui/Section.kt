package ua.sprotyv.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import ua.sprotyv.R

/**
 * The six sections of the home screen, in reading order (Hick's law: at most six choices).
 * "What to do now" is the one marked out: in an emergency it is the first choice.
 */
enum class Section(@StringRes val title: Int, @StringRes val hint: Int, val icon: ImageVector, val marked: Boolean = false) {
    ALARM(R.string.tile_alarm, R.string.tile_alarm_hint, Icons.Filled.Notifications),
    NOW(R.string.tile_now, R.string.tile_now_hint, Icons.Filled.Warning, marked = true),
    HOME_LIFE(R.string.tile_home_life, R.string.tile_home_life_hint, Icons.Filled.Home),
    SAFE(R.string.tile_safe, R.string.tile_safe_hint, Icons.AutoMirrored.Filled.Send),
    GUIDE(R.string.tile_guide, R.string.tile_guide_hint, Icons.Filled.Info),
    MINE(R.string.tile_mine, R.string.tile_mine_hint, Icons.Filled.Lock),
}
