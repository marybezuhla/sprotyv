package ua.sprotyv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Sprotyv has no internet: feedback opens a new, already filled-in issue on the project's GitHub
 * page in the browser. The person sees and edits everything there before sending; nothing from
 * "Mine" is ever added.
 */
object Feedback {
    private const val NEW_ISSUE = "https://github.com/Marybezu/sprotyv/issues/new"

    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    fun open(context: Context, title: String, body: String) {
        val uri = Uri.parse(NEW_ISSUE).buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body.take(6000))
            .build()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
    }
}

/**
 * If the app crashes, a short technical description (the error's type and where in the code) is
 * kept on the phone. Settings then offer to send it, showing the whole text first. Never a dialog
 * at start: the app may be opened in danger. Exception messages are left out, because they can
 * contain typed text.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    private class Handler(val context: Context, val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, e: Throwable) {
            runCatching { File(context.filesDir, FILE).writeText(describe(context, e)) }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun describe(context: Context, e: Throwable): String = buildString {
        appendLine("Sprotyv ${Feedback.version(context)} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 3) {
            appendLine((if (depth == 0) "" else "Caused by: ") + t.javaClass.name)
            t.stackTrace.take(12).forEach { appendLine("  at $it") }
            t = t.cause
            depth++
        }
    }.take(3000)

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
