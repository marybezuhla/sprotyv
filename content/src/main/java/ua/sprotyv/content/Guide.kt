package ua.sprotyv.content

import org.json.JSONArray
import org.json.JSONObject

/**
 * The offline guide, read from guide.json (made by scripts/build_guide.py from the open
 * "Довідник виживання", CC BY-SA 4.0). Bold is kept as **…** inside the text.
 */
data class Guide(
    val title: String,
    val version: String,
    val source: String,
    val license: String,
    val changes: String,
    val chapters: List<Chapter>,
) {
    /** A section by its number ("7.2", "Б.3"), or null. */
    fun section(num: String): Pair<Chapter, Section>? {
        for (c in chapters) for (s in c.sections) if (s.num == num) return c to s
        return null
    }
}

data class Chapter(val id: String, val label: String, val title: String, val intro: List<Block>, val sections: List<Section>)

data class Section(val num: String, val title: String, val blocks: List<Block>)

sealed interface Block {
    data class Paragraph(val text: String, val intro: Boolean = false) : Block
    data class Heading(val text: String) : Block
    data class Items(val kind: Kind, val items: List<Item>) : Block {
        enum class Kind { STEPS, CHECK, BULLETS }
    }
    data class Table(val rows: List<Row>) : Block
    data class Figure(val pictures: List<String>, val caption: String) : Block
    data class Callout(val kind: Kind, val blocks: List<Block>) : Block {
        /** CRITICAL is the only kind drawn as danger. */
        enum class Kind { CRITICAL, WARNING, TIP, NOTE }
    }
}

data class Item(val text: String, val sub: List<Item> = emptyList())

data class Row(val head: Boolean, val cells: List<String>)

object GuideReader {
    private val PICTURE = Regex("fig_[0-9a-f]{16}")

    fun parse(json: String): Guide {
        val o = JSONObject(json)
        val chapters = o.getJSONArray("chapters")
        return Guide(
            title = o.getString("title"),
            version = o.optString("version", ""),
            source = o.getString("source"),
            license = o.getString("license"),
            changes = o.optString("changes", ""),
            chapters = (0 until chapters.length()).map { chapter(chapters.getJSONObject(it)) },
        )
    }

    private fun chapter(o: JSONObject): Chapter {
        val sections = o.getJSONArray("sections")
        return Chapter(
            id = o.getString("id"),
            label = o.optString("label", ""),
            title = o.getString("title"),
            intro = blocks(o.getJSONArray("intro")),
            sections = (0 until sections.length()).map {
                val s = sections.getJSONObject(it)
                Section(s.optString("num", ""), s.getString("title"), blocks(s.getJSONArray("blocks")))
            },
        )
    }

    private fun blocks(a: JSONArray): List<Block> = (0 until a.length()).map { block(a.getJSONObject(it)) }

    private fun block(o: JSONObject): Block = when (val t = o.getString("t")) {
        "p" -> Block.Paragraph(o.getString("text"))
        "intro" -> Block.Paragraph(o.getString("text"), intro = true)
        "h" -> Block.Heading(o.getString("text"))
        "list" -> Block.Items(
            when (o.getString("kind")) {
                "steps" -> Block.Items.Kind.STEPS
                "check" -> Block.Items.Kind.CHECK
                else -> Block.Items.Kind.BULLETS
            },
            items(o.getJSONArray("items")),
        )
        "table" -> {
            val rows = o.getJSONArray("rows")
            Block.Table((0 until rows.length()).map {
                val r = rows.getJSONObject(it)
                val cells = r.getJSONArray("cells")
                Row(r.optBoolean("head", false), (0 until cells.length()).map { i -> cells.getString(i) })
            })
        }
        "figure" -> {
            val p = o.getJSONArray("pictures")
            val names = (0 until p.length()).map { p.getString(it) }
            // Only names the build made: nothing else is ever opened as a file
            names.forEach { require(it.matches(PICTURE)) { "Bad picture name" } }
            Block.Figure(names, o.optString("caption", ""))
        }
        "callout" -> Block.Callout(
            when (o.getString("kind")) {
                "critical" -> Block.Callout.Kind.CRITICAL
                "warning" -> Block.Callout.Kind.WARNING
                "tip" -> Block.Callout.Kind.TIP
                else -> Block.Callout.Kind.NOTE
            },
            blocks(o.getJSONArray("blocks")),
        )
        else -> throw IllegalArgumentException("Unknown block $t")
    }

    private fun items(a: JSONArray): List<Item> = (0 until a.length()).map {
        val i = a.getJSONObject(it)
        Item(i.getString("text"), i.optJSONArray("sub")?.let(::items) ?: emptyList())
    }
}

/** Plain text without the ** marks, for search and for reading aloud. */
fun plain(text: String): String = text.replace("**", "")

/** One search hit: where it is and a short piece of text around the match. */
data class Hit(val chapter: Chapter, val section: Section, val snippet: String)

/**
 * Offline search over the whole guide. Case is ignored, and so are the apostrophe variants and
 * "ґ"/"г", which people type differently. All words must be found in the same section.
 */
object GuideSearch {
    const val MAX_HITS = 50

    fun normalize(s: String): String = s.lowercase()
        .replace('ʼ', '\'').replace('’', '\'').replace('`', '\'')
        .replace('ґ', 'г').replace('ё', 'е')

    fun search(guide: Guide, query: String): List<Hit> {
        val words = normalize(query).split(Regex("\\s+")).filter { it.length >= 2 }.take(6)
        if (words.isEmpty()) return emptyList()
        val hits = ArrayList<Hit>()
        for (c in guide.chapters) for (s in c.sections) {
            val text = plain(s.title + "\n" + textOf(s.blocks))
            val norm = normalize(text)
            if (words.all { norm.contains(it) }) {
                val at = norm.indexOf(words[0])
                val from = maxOf(0, at - 60)
                val to = minOf(text.length, at + 120)
                val snippet = (if (from > 0) "…" else "") + text.substring(from, to).replace('\n', ' ').trim() + (if (to < text.length) "…" else "")
                hits += Hit(c, s, snippet)
                if (hits.size >= MAX_HITS) return hits
            }
        }
        // Title matches first: they are what people mean most often
        return hits.sortedByDescending { h -> words.all { normalize(h.section.title).contains(it) } }
    }

    private fun textOf(blocks: List<Block>): String = buildString {
        for (b in blocks) when (b) {
            is Block.Paragraph -> appendLine(b.text)
            is Block.Heading -> appendLine(b.text)
            is Block.Items -> b.items.forEach { appendItem(it) }
            is Block.Table -> b.rows.forEach { appendLine(it.cells.joinToString(" · ")) }
            is Block.Figure -> appendLine(b.caption)
            is Block.Callout -> append(textOf(b.blocks))
        }
    }

    private fun StringBuilder.appendItem(i: Item) {
        appendLine(i.text)
        i.sub.forEach { appendItem(it) }
    }
}
