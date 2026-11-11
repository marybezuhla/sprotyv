package ua.sprotyv.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tourniquet rules: numbers, the day limit, a clock moved back, moving the time, the hourly reminders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TourniquetsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    @Before fun clean() {
        app.getSharedPreferences("tourniquet", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun numbersGoUpAndAreNotReusedWhileAnyIsOn() {
        assertEquals(1, Tourniquets.add(app))
        assertEquals(2, Tourniquets.add(app))
        Tourniquets.remove(app, 2)
        assertEquals("a number handed over is not given again while others are on", 3, Tourniquets.add(app))
        Tourniquets.remove(app, 1)
        Tourniquets.remove(app, 3)
        assertEquals("all handed over: counting starts again", 1, Tourniquets.add(app))
    }

    @Test fun noMoreThanTheLimit() {
        repeat(Tourniquets.MAX) { assertTrue(Tourniquets.add(app) != null) }
        assertNull(Tourniquets.add(app))
        assertEquals(Tourniquets.MAX, Tourniquets.all(app).size)
    }

    @Test fun aDayOldOneIsGone() {
        val now = System.currentTimeMillis()
        Tourniquets.add(app, now - day - 1_000)
        assertTrue(Tourniquets.all(app, now).isEmpty())
        assertNull(Tourniquets.expires(app, now))
    }

    @Test fun aClockMovedALittleBackKeepsItOn() {
        val now = System.currentTimeMillis()
        Tourniquets.add(app, now + 5 * min)
        assertEquals(1, Tourniquets.all(app, now).size)
        // Far into the future is not a clock moved a little: it goes
        clean()
        Tourniquets.add(app, now + 15 * min)
        assertTrue(Tourniquets.all(app, now).isEmpty())
    }

    @Test fun theTimeMovesBackButNeverIntoTheFutureOrPastADay() {
        val now = System.currentTimeMillis()
        val n = Tourniquets.add(app, now)!!
        Tourniquets.shift(app, n, -30, now)
        assertEquals(now - 30 * min, Tourniquets.earliest(app, now))
        Tourniquets.shift(app, n, 60, now)
        assertEquals(now, Tourniquets.earliest(app, now))
        Tourniquets.shift(app, n, -2_000, now)
        assertEquals(now - day + min, Tourniquets.earliest(app, now))
    }

    @Test fun eachFullHourIsSaidOnceAndTheLatestOneWins() {
        val start = System.currentTimeMillis()
        Tourniquets.add(app, start)
        assertTrue(Tourniquets.dueHours(app, start + 59 * min).isEmpty())
        assertEquals(listOf(1), Tourniquets.dueHours(app, start + 61 * min).map { it.second })
        assertTrue("said once", Tourniquets.dueHours(app, start + 70 * min).isEmpty())
        // A late alarm says the hour it is now, not every hour it missed
        assertEquals(listOf(3), Tourniquets.dueHours(app, start + 3 * hour + min).map { it.second })
    }

    @Test fun theSingleTimeKeptBeforeBecomesNumberOne() {
        val now = System.currentTimeMillis()
        app.getSharedPreferences("tourniquet", Context.MODE_PRIVATE).edit().putLong("at", now - hour).commit()
        val list = Tourniquets.all(app, now)
        assertEquals(1, list.size)
        assertEquals(1, list.first().n)
        assertEquals(now - hour, list.first().at)
    }

    @Test fun aDamagedEntryIsSkippedAndTheOthersStay() {
        val now = System.currentTimeMillis()
        app.getSharedPreferences("tourniquet", Context.MODE_PRIVATE).edit()
            .putString("list", """[{"n":4,"at":${now - hour}},{"broken":true}]""").commit()
        val list = Tourniquets.all(app, now)
        assertEquals(listOf(4), list.map { it.n })
    }
}
