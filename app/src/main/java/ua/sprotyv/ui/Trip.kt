package ua.sprotyv.ui

import ua.sprotyv.content.Link

/**
 * Trip mode, for a checkpoint or a phone check: the owner's data ("Mine" and everything that
 * opens it) and sources that could draw attention (front maps, the search for prisoners and the
 * missing, veterans' services, reporting war crimes) are off every screen, list and tile.
 * Turning it on is one tap; turning it off asks for the phone's PIN. Called "trip mode" so that the
 * name itself says nothing. Nothing is deleted: the data stays encrypted on the phone, so anyone
 * who makes the owner unlock the phone and confirm the PIN can turn it off and see it.
 */
object Trip {
    private val HIDDEN_GROUPS = setOf("front", "search", "veterans")
    private val HIDDEN_LINKS = setOf("warcrimes")

    fun hides(route: Route): Boolean = route.personal || route == Route.Scan

    fun hides(link: Link): Boolean = link.group in HIDDEN_GROUPS || link.id in HIDDEN_LINKS
}
