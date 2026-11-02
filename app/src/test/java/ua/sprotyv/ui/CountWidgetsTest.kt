package ua.sprotyv.ui

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountWidgetsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun aTapMadeWhileSavingIsNeitherLostNorCountedTwice() {
        repeat(3) { CountWidgets.plus(app, "donated") }
        val read = CountWidgets.peekPending(app)
        assertEquals(mapOf("donated" to 3), read)
        // A tap lands between reading and storing
        CountWidgets.plus(app, "donated")
        CountWidgets.settle(app, read)
        assertEquals(mapOf("donated" to 1), CountWidgets.peekPending(app))
        CountWidgets.settle(app, CountWidgets.peekPending(app))
        assertTrue(CountWidgets.peekPending(app).isEmpty())
    }

    @Test fun unknownCountsAreIgnored() {
        CountWidgets.plus(app, "meals")
        assertTrue(CountWidgets.peekPending(app).isEmpty())
    }

    @Test fun forgettingCopiesKeepsWaitingTaps() {
        CountWidgets.set(app, 7, "blood", showName = false)
        CountWidgets.plus(app, "blood")
        CountWidgets.forgetCopies(app)
        assertEquals(null, CountWidgets.get(app, 7))
        assertEquals(mapOf("blood" to 1), CountWidgets.peekPending(app))
    }

    @Test fun everyWidgetCountIsKeptByTheEncryptedSteps() {
        val all = CountWidgets.COUNTERS.map { it.first }
        val s = Steps.parse(Steps(counts = all.associateWith { 2 }).json())
        assertEquals(all.toSet(), s.counts.keys)
    }

    @Test fun unsavedChangesAreAskedAbout() {
        EditGuard.clear()
        assertFalse(EditGuard.isDirty)
        EditGuard.mark("medical", true)
        assertTrue(EditGuard.isDirty)
        EditGuard.mark("medical", false)
        assertFalse(EditGuard.isDirty)
    }
}
