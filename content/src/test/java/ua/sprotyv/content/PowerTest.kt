package ua.sprotyv.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTest {
    @Test fun twentyThousandIsThreeToFourPhoneCharges() {
        // The guide (20.3): a 20 000 mAh power bank gives 3-4 phone charges
        assertTrue(Charge.phoneCharges(Charge.bankWh(20_000)) in 3..4)
    }

    @Test fun stationHours() {
        assertEquals(42, Charge.hours(Charge.stationWh(500), 10))
        assertEquals(8, Charge.hours(Charge.stationWh(500), 50))
    }

    @Test fun minutesLeftNeverNegative() {
        assertEquals(60, Blackout.minutesLeft(180, Blackout.FRIDGE_HOURS))
        assertEquals(0, Blackout.minutesLeft(600, Blackout.FRIDGE_HOURS))
    }
}
