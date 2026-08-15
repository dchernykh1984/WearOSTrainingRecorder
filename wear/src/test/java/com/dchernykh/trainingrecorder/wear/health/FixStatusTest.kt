package com.dchernykh.trainingrecorder.wear.health

import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.LocationAvailability
import com.dchernykh.trainingrecorder.core.sensor.FixStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FixStatusTest {
    @Test
    fun aRealFixIsAcquiredHoweverItIsHeld() {
        // Tethered means the position is coming by way of the phone. To a rider
        // watching the indicator that is still a tracked ride.
        assertEquals(FixStatus.ACQUIRED, fixStatusOf(LocationAvailability.ACQUIRED_TETHERED))
        assertEquals(FixStatus.ACQUIRED, fixStatusOf(LocationAvailability.ACQUIRED_UNTETHERED))
    }

    @Test
    fun stillLookingIsItsOwnAnswer() {
        // The whole reason for a third colour: the first minute of a ride is
        // spent here, and showing "no" throughout it either strands a rider
        // waiting or sends them off believing nothing is being recorded.
        assertEquals(FixStatus.ACQUIRING, fixStatusOf(LocationAvailability.ACQUIRING))
    }

    @Test
    fun everythingElseIsNoFixIncludingTheUnknown() {
        assertEquals(FixStatus.NONE, fixStatusOf(LocationAvailability.UNAVAILABLE))
        assertEquals(FixStatus.NONE, fixStatusOf(LocationAvailability.NO_GNSS))
        assertEquals(FixStatus.NONE, fixStatusOf(LocationAvailability.UNKNOWN))
        assertEquals(FixStatus.NONE, fixStatusOf(null))
        // A state this build does not recognise is not one to reassure anyone
        // with - including one that is not about location at all.
        assertEquals(FixStatus.NONE, fixStatusOf(DataTypeAvailability.AVAILABLE))
    }
}
