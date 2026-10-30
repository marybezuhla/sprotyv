package ua.sprotyv.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * A plain A4 document for printing: headings, text, lists and pictures, broken over pages.
 * Built with Android's own PdfDocument, no extra library. Black on white, large enough to read
 * without glasses; nothing hidden, no metadata beyond what Android writes for every PDF.
 */
class Pdf(private val footer: String) {
    sealed interface Part {
        data class Title(val text: String) : Part
        data class Heading(val text: String) : Part
        data class Text(val text: String, val bold: Boolean = false) : Part
        data class Field(val label: String, val value: String) : Part
        data class Picture(val bitmap: Bitmap, val sizePt: Int) : Part
        data object Gap : Part
        data object PageBreak : Part
    }

    private val parts = ArrayList<Part>()

    fun add(part: Part) = apply { parts += part }

    fun title(t: String) = add(Part.Title(t))
    fun heading(t: String) = add(Part.Heading(t))
    fun text(t: String, bold: Boolean = false) = apply { if (t.isNotBlank()) parts += Part.Text(t, bold) }
    fun field(label: String, value: String) = apply { if (value.isNotBlank()) parts += Part.Field(label, value) }
    fun gap() = add(Part.Gap)
    fun pageBreak() = add(Part.PageBreak)

    fun build(): ByteArray {
        val doc = PdfDocument()
        try {
            Writer(doc).run { parts.forEach(::draw); finish() }
            return ByteArrayOutputStream().use { out -> doc.writeTo(out); out.toByteArray() }
        } finally {
            doc.close()
        }
    }

    private inner class Writer(val doc: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        private val regular = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f; typeface = Typeface.DEFAULT }
        private val bold = TextPaint(regular).apply { typeface = Typeface.DEFAULT_BOLD }
        private val heading = TextPaint(bold).apply { textSize = 15f }
        private val title = TextPaint(bold).apply { textSize = 20f }
        private val small = TextPaint(regular).apply { textSize = 9f; color = Color.DKGRAY }

        private fun canvas(): Canvas = (page ?: newPage()).canvas

        private fun newPage(): PdfDocument.Page {
            page?.let { close(it) }
            number++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
            page = p
            y = MARGIN.toFloat()
            return p
        }

        private fun close(p: PdfDocument.Page) {
            p.canvas.drawText("$footer · $number", MARGIN.toFloat(), (HEIGHT - MARGIN / 2).toFloat(), small)
            doc.finishPage(p)
        }

        fun finish() {
            if (page == null) newPage()
            page?.let { close(it) }
            page = null
        }

        fun draw(part: Part) {
            when (part) {
                is Part.Title -> block(part.text, title, before = 0f, after = 10f, keepWithNext = 0f)
                is Part.Heading -> block(part.text, heading, before = 12f, after = 4f, keepWithNext = 40f)
                is Part.Text -> block(part.text, if (part.bold) bold else regular, before = 0f, after = 4f, keepWithNext = 0f)
                is Part.Field -> block("${part.label}: ${part.value}", regular, before = 0f, after = 3f, keepWithNext = 0f, boldPrefix = part.label.length + 1)
                is Part.Picture -> picture(part.bitmap, part.sizePt.toFloat())
                Part.Gap -> { canvas(); y += 8f }
                Part.PageBreak -> newPage()
            }
        }

        private fun bottom() = (HEIGHT - MARGIN).toFloat()

        private fun block(text: String, paint: TextPaint, before: Float, after: Float, keepWithNext: Float, boldPrefix: Int = 0) {
            canvas()
            y += before
            val content: CharSequence = if (boldPrefix > 0) {
                android.text.SpannableString(text).apply {
                    setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, boldPrefix.coerceAtMost(text.length), 0)
                }
            } else text
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, WIDTH - 2 * MARGIN)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()
            // A heading never stays alone at the foot of a page
            if (y + layout.getLineBottom(0) + keepWithNext > bottom()) newPage()
            var line = 0
            while (line < layout.lineCount) {
                // As many lines as fit on this page
                var last = line
                val top = layout.getLineTop(line).toFloat()
                while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= bottom()) last++
                val height = layout.getLineBottom(last) - top
                val c = canvas()
                c.save()
                c.translate(MARGIN.toFloat(), y - top)
                c.clipRect(0f, top, (WIDTH - 2 * MARGIN).toFloat(), top + height)
                layout.draw(c)
                c.restore()
                y += height
                line = last + 1
                if (line < layout.lineCount) newPage()
            }
            y += after
        }

        private fun picture(bitmap: Bitmap, size: Float) {
            canvas()
            if (y + size > bottom()) newPage()
            val left = MARGIN
            val dst = Rect(left, y.toInt(), (left + size).toInt(), (y + size).toInt())
            canvas().drawBitmap(bitmap, null, dst, Paint().apply { isFilterBitmap = false })
            y += size + 6f
        }
    }

    companion object {
        // A4 in points (1/72 inch)
        const val WIDTH = 595
        const val HEIGHT = 842
        const val MARGIN = 48
    }
}
