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

        private fun read(context: Context, lang: String): Content {
            // One file at a time: a file that cannot be read (out of memory on a small phone
            // included) leaves only its own part empty, never the first-aid cards
            fun <T> load(name: String, localized: Boolean, parse: (String) -> T): T? = try {
                val stream = if (localized) open(context, lang, name) else context.applicationContext.assets.open(name)
                stream.bufferedReader().use { parse(it.readText()) }
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            val cards = load("cards.json", true, CardsReader::parse)
            val kids = load("kid_cards.json", true, CardsReader::parse)
            return Content(
                load("guide.json", true, GuideReader::parse),
                cards,
                kids,
                load("links.json", true, LinksReader::parse),
                load("practice.json", true) { PracticeReader.parse(it, cards.orEmpty() + kids.orEmpty()) },
                load("moz441.json", false, Moz441Reader::parse),
                load("constitution.json", false, ConstitutionReader::parse),
                load("history.json", true, HistoryReader::parse),
                load("new_place.json", true, NewPlaceReader::parse),
                load("pdr.json", false, PdrReader::parse),
            )
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
