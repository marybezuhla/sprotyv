package ua.sprotyv.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Everything built in, read once from the app's own files. No network, nothing personal.
 * The guide and the cards are read separately, so a fault in one never hides the other;
 * whatever could not be read is null and the screen says so.
 */
class Content(
    private val guideOrNull: Guide?,
    private val cardsOrNull: List<Card>?,
    private val kidCardsOrNull: List<Card>? = null,
    private val linksOrNull: List<Link>? = null,
    private val practiceOrNull: List<Practice>? = null,
    /** The Ministry of Health's first-aid procedures (order No. 441); null if they could not be read. */
    val moz441: Moz441? = null,
    /** The Constitution of Ukraine in full; null if it could not be read. */
    val constitution: Constitution? = null,
    /** "Why resist": stories from history; null if they could not be read. */
    val history: History? = null,
    /** "New place": steps after leaving home; null if they could not be read. */
    val newPlace: NewPlace? = null,
    /** The Rules of the Road in full; null if they could not be read. */
    val pdr: Pdr? = null,
) {
    val guideBroken get() = guideOrNull == null
    val cardsBroken get() = cardsOrNull == null
    val guide: Guide get() = guideOrNull ?: EMPTY_GUIDE
    val cards: List<Card> get() = cardsOrNull ?: emptyList()

    /** The child's cards (6-10 years), kept apart so they never mix into the adult lists. */
    val kidCards: List<Card> get() = kidCardsOrNull ?: emptyList()
    val kidCardsBroken get() = kidCardsOrNull == null

    /** "Situation": trusted places to look, opened outside Sprotyv. */
    val links: List<Link> get() = linksOrNull ?: emptyList()
    val linksBroken get() = linksOrNull == null

    /** "What would you do?": practice situations for the cards. Empty if it could not be read. */
    val practice: List<Practice> get() = practiceOrNull ?: emptyList()

    fun card(id: String): Card? = cards.firstOrNull { it.id == id } ?: kidCards.firstOrNull { it.id == id }

    companion object {
        @Volatile private var loaded: Content? = null
        @Volatile private var loadedLang: String = ""

        /** The app's language now: "en" reads the English files where there are any. */
        private fun lang(context: Context): String = context.resources.configuration.locales[0].language

        /**
         * A content file in the app's language: assets/en/<name> for English when it exists,
         * otherwise the Ukrainian original.
         */
        private fun open(context: Context, lang: String, name: String): java.io.InputStream {
            val assets = context.applicationContext.assets
            if (lang == "en") runCatching { return assets.open("en/$name") }
            return assets.open(name)
        }

        /** Reads the guide and cards once per language; call off the main thread. */
        fun get(context: Context): Content {
            val lang = lang(context)
            loaded?.takeIf { loadedLang == lang }?.let { return it }
            return synchronized(this) { loaded?.takeIf { loadedLang == lang } ?: read(context, lang).also { loaded = it; loadedLang = lang } }
        }

        private fun read(context: Context, lang: String): Content = run {
                val assets = context.applicationContext.assets
                val guide = try {
                    open(context, lang, "guide.json").bufferedReader().use { GuideReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val cards = try {
                    open(context, lang, "cards.json").bufferedReader().use { CardsReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val kids = try {
                    open(context, lang, "kid_cards.json").bufferedReader().use { CardsReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val links = try {
                    assets.open("links.json").bufferedReader().use { LinksReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val practice = try {
                    open(context, lang, "practice.json").bufferedReader().use { PracticeReader.parse(it.readText(), cards.orEmpty() + kids.orEmpty()) }
                } catch (e: Exception) {
                    null
                }
                val moz = try {
                    assets.open("moz441.json").bufferedReader().use { Moz441Reader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val constitution = try {
                    assets.open("constitution.json").bufferedReader().use { ConstitutionReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val history = try {
                    open(context, lang, "history.json").bufferedReader().use { HistoryReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val newPlace = try {
                    open(context, lang, "new_place.json").bufferedReader().use { NewPlaceReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val pdr = try {
                    assets.open("pdr.json").bufferedReader().use { PdrReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                Content(guide, cards, kids, links, practice, moz, constitution, history, newPlace, pdr)
            }

        private val EMPTY_GUIDE = Guide("", "", "", "", "", emptyList())

        /** A figure of the guide; null if it cannot be read. Name checked by [GuideReader]. */
        fun picture(context: Context, name: String): Bitmap? = try {
            context.applicationContext.assets.open("figures/$name.png").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
}
