package ua.sprotyv.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Everything built in, read once from the app's own files. No network, nothing personal. */
class Content(val guide: Guide, val cards: List<Card>) {
    fun card(id: String): Card? = cards.firstOrNull { it.id == id }

    companion object {
        @Volatile private var loaded: Content? = null

        /** Reads the guide and cards once; call off the main thread. */
        fun get(context: Context): Content = loaded ?: synchronized(this) {
            loaded ?: run {
                val assets = context.applicationContext.assets
                val guide = assets.open("guide.json").bufferedReader().use { GuideReader.parse(it.readText()) }
                val cards = assets.open("cards.json").bufferedReader().use { CardsReader.parse(it.readText()) }
                Content(guide, cards).also { loaded = it }
            }
        }

        /** A figure of the guide; null if it cannot be read. Name checked by [GuideReader]. */
        fun picture(context: Context, name: String): Bitmap? = try {
            context.applicationContext.assets.open("figures/$name.png").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
}
