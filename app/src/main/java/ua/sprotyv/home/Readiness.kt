package ua.sprotyv.home

import ua.sprotyv.personal.FamilyPlan
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person

/**
 * "How ready are we?": four parts, each a count the owner can raise one step at a time.
 * Counts, not grades: an empty field is a next step, never a failure (so never red).
 */
data class Progress(val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done >= total
    val fraction: Float get() = if (total == 0) 0f else (done.coerceAtMost(total).toFloat() / total)
}

object Readiness {
    /** Blood group, allergies, medicines, conditions. "None" typed in counts as filled. */
    fun medical(card: MedicalCard?): Progress =
        Progress(listOfNotNull(card?.bloodGroup, card?.allergies, card?.medicines, card?.conditions).count { it.isNotBlank() }, 4)

    /** Someone to tell "I am safe", and someone to call in an emergency. */
    fun people(list: List<Person>): Progress =
        Progress(listOf(list.any { it.safeList }, list.any { it.ice }).count { it }, 2)

    /** Three meeting points and the go-between's phone. */
    fun plan(plan: FamilyPlan?): Progress =
        Progress(listOfNotNull(plan?.point1, plan?.point2, plan?.point3, plan?.goBetweenPhone).count { it.isNotBlank() }, 4)

    fun kit(done: Int, total: Int): Progress = Progress(done, total)
}
