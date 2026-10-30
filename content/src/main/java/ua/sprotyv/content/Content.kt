package ua.sprotyv.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Everything built in, read once from the app's own files. No network, nothing personal.
 * The guide and the cards are read separately, so a fault in one never hides the other;
 * whatever could not be read is null and the screen says so.
 */
class Content(private val guideOrNull: Guide?, private val cardsOrNull: List<Card>?, private val kidCardsOrNull: List<Card>? = null) {
    val guideBroken get() = guideOrNull == null
    val cardsBroken get() = cardsOrNull == null
    val guide: Guide get() = guideOrNull ?: EMPTY_GUIDE
    val cards: List<Card> get() = cardsOrNull ?: emptyList()

    /** The child's cards (6-10 years), kept apart so they never mix into the adult lists. */
    val kidCards: List<Card> get() = kidCardsOrNull ?: emptyList()
    val kidCardsBroken get() = kidCardsOrNull == null

    fun card(id: String): Card? = cards.firstOrNull { it.id == id } ?: kidCards.firstOrNull { it.id == id }

    companion object {
        @Volatile private var loaded: Content? = null

        /** Reads the guide and cards once; call off the main thread. */
        fun get(context: Context): Content = loaded ?: synchronized(this) {
            loaded ?: run {
                val assets = context.applicationContext.assets
                val guide = try {
                    assets.open("guide.json").bufferedReader().use { GuideReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val cards = try {
                    assets.open("cards.json").bufferedReader().use { CardsReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                val kids = try {
                    assets.open("kid_cards.json").bufferedReader().use { CardsReader.parse(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                Content(guide, cards, kids).also { loaded = it }
            }
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
