package dev.ilgax.venus.backend

import kotlin.test.Test
import kotlin.test.assertEquals

class BackendConfigTest {
    @Test
    fun `defaults match current Venus config defaults`() {
        val config = BackendConfig()

        assertEquals(1, config.maxUsers)
        assertEquals(60, config.authTimeoutSeconds)
    }
}
