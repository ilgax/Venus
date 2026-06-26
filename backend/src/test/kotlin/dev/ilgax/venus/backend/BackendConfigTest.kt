package dev.ilgax.venus.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendConfigTest {
    @Test
    fun `defaults match current Venus config defaults`() {
        val config = BackendConfig()

        assertEquals(1, config.maxUsers)
        assertEquals(60, config.authTimeoutSeconds)
    }

    @Test
    fun `rejects non-positive values`() {
        assertFailsWith<IllegalArgumentException> {
            BackendConfig(maxUsers = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig(authTimeoutSeconds = 0)
        }
    }
}
