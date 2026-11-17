package ua.sprotyv.ui

import android.annotation.SuppressLint
import android.content.SharedPreferences

/**
 * Written to disk at once, not later: a running timer (since when, a tourniquet's time), a tap on
 * a widget or a step moved into the encrypted data must survive the app being closed by Android a
 * moment after. If the write fails (a full disk), Android keeps the value in memory and tries again
 * with the next write; there is nothing better to do here, so the result is not looked at.
 */
@SuppressLint("ApplySharedPref")
internal fun SharedPreferences.Editor.saveNow() {
    commit()
}
