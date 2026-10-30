package ua.sprotyv.content

/**
 * When the power goes: how long the fridge and freezer keep food cold with the door shut
 * (FoodSafety.gov, the US food-safety site: about 4 hours, a full freezer about 48, half-full
 * about 24; the card's own "24-48 hours" agrees). Not reviewed by a specialist yet.
 */
object Blackout {
    const val FRIDGE_HOURS = 4
    const val FREEZER_FULL_HOURS = 48
    const val FREEZER_HALF_HOURS = 24
    const val SOURCE = "FoodSafety.gov"
    const val CHECKED = "2026-10-31"

    /** Minutes left before [limitHours], never below zero. */
    fun minutesLeft(elapsedMinutes: Long, limitHours: Int): Long = (limitHours * 60L - elapsedMinutes).coerceAtLeast(0)
}

/**
 * "My charge": roughly how long a power bank or a charging station runs common things.
 * Rough on purpose and said so: a power bank's rating is at 3.7 V and about two thirds reaches a
 * phone (so 20 000 mAh gives 3-4 phone charges, as the guide says in 20.3); a station gives about
 * 85 % through its sockets. The watts of each thing are shown, so anyone can check their own.
 */
object Charge {
    private const val BANK_VOLTS = 3.7
    private const val BANK_SHARE = 0.65
    private const val STATION_SHARE = 0.85
    /** One full charge of a typical phone, watt-hours. */
    const val PHONE_WH = 15.0

    val BANKS = listOf(10_000, 20_000, 30_000)
    val STATIONS = listOf(300, 500, 1000, 2000)

    /** Things a person might run, with typical watts. */
    enum class Thing(val watts: Int, val onBank: Boolean) { ROUTER(10, false), LAMP(5, true), LAPTOP(50, false) }

    fun bankWh(mAh: Int): Double = mAh * BANK_VOLTS / 1000 * BANK_SHARE
    fun stationWh(wh: Int): Double = wh * STATION_SHARE

    fun phoneCharges(usableWh: Double): Int = (usableWh / PHONE_WH).toInt()
    fun hours(usableWh: Double, watts: Int): Int = (usableWh / watts).toInt()
}
