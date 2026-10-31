package ua.sprotyv.ui

import ua.sprotyv.content.Link

/**
 * Trip mode, for when the phone may end up in the wrong hands (leaving occupied areas, a threat of
 * capture or abduction, theft): the owner's data ("Mine" and everything that
 * opens it) and sources that could draw attention (front maps, the search for prisoners and the
 * missing, veterans' services, war crimes and human rights, deported children, papers from the
 * occupied areas, care for the war-wounded, the state's counter-disinformation centre) are off
 * every screen, list and tile. The built-in guide stays, and its search too.
 * Turning it on is one tap; turning it off asks for the phone's PIN. Called "trip mode" so that the
 * name itself says nothing. Nothing is deleted: the data stays encrypted on the phone, so anyone
 * who makes the owner unlock the phone and confirm the PIN can turn it off and see it.
 */
object Trip {
    private val HIDDEN_GROUPS = setOf("front", "search", "veterans")
    private val HIDDEN_LINKS = setOf(
        "warcrimes", "helsinki", "cpd",
        // Deported children, papers from the occupied areas, care for the war-wounded
        "bring-kids-back", "children-of-war", "minjust", "superhumans", "unbroken",
    )

    fun hides(route: Route): Boolean = route.personal || route == Route.Scan || route == Route.Resistance

    fun hides(link: Link): Boolean = link.group in HIDDEN_GROUPS || link.id in HIDDEN_LINKS
}
