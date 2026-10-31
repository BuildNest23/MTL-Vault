package com.example.tts.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeDrmTest {

    @Test
    fun tokenMatchesReferenceValues() {
        assertEquals(
            "2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2",
            EdgeDrm.generateSecMsGec(1_700_000_000.0)
        )
        assertEquals(
            "D9E0488BC4950B78-5548A1A263CBF97997518B9BC924F084E576FF530FBEE79AF1CDA23A05387F6D",
            EdgeDrm.generateSecMsGec(1_729_110_000.0)
        )
    }

    @Test
    fun tokenStaysTheSameInsideFiveMinuteWindow() {
        // Tokens are based on the time rounded down to 5 minutes.
        assertEquals(
            EdgeDrm.generateSecMsGec(1_700_000_090.0),
            EdgeDrm.generateSecMsGec(1_700_000_000.0)
        )
    }

    @Test
    fun muidIsThirtyTwoHexCharacters() {
        val muid = EdgeDrm.generateMuid()
        assertEquals(32, muid.length)
        assertTrue(muid.all { it in '0'..'9' || it in 'A'..'F' })
    }
}
