package dev.ilgax.venus.stats

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsCollectorTest {
    @Test
    fun `stat precision is limited to one decimal`() {
        assertEquals(19.8, StatsCollector.roundToOneDecimal(19.84))
        assertEquals(19.9, StatsCollector.roundToOneDecimal(19.85))
        assertEquals(20.0, StatsCollector.roundToOneDecimal(20.0))
        assertEquals(2.4, StatsCollector.roundToOneDecimal(2.449))
    }
}
