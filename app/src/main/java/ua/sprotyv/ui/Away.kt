package ua.sprotyv.ui

/**
 * Sprotyv itself is about to cover its screen with a system window (the PIN window, a file picker,
 * the camera). Leaving for it does not close the owner's data or lock settings: with "Mine" locked,
 * closing would ask for the PIN again on return and lose what the window was for. Cleared as soon
 * as Sprotyv is back on screen.
 */
object Away {
    @Volatile var expected = false
        private set

    fun forOwnWindow() { expected = true }

    fun back() { expected = false }
}
