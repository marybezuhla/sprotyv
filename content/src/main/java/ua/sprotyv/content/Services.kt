package ua.sprotyv.content

/**
 * Emergency numbers, built in so they work with no network. Hotlines are added only with a
 * source and a check date, and every number is checked again before each release.
 */
enum class Service(val number: String) {
    UNIFIED("112"),
    RESCUE("101"),
    POLICE("102"),
    AMBULANCE("103"),
    GAS("104"),
}
