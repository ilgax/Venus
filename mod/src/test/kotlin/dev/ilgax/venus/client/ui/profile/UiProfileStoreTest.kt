package dev.ilgax.venus.client.ui.profile

import org.junit.Test
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiProfileStoreTest {
    @Test
    fun `factory profile satisfies both grid layouts`() {
        UiProfileValidator.validateProfile(FactoryUiProfile.profile)
        assertEquals(12, UiLayoutMode.NORMAL.columns)
        assertEquals(6, UiLayoutMode.COMPACT.columns)
    }

    @Test
    fun `store round trips profiles and resolves server assignment`() {
        val folder = createTempDirectory("venus-ui-profiles").toFile()
        val store = UiProfileStore(folder)
        val profile = FactoryUiProfile.profile.copy(id = "custom", name = "Custom")
        val file =
            UiProfilesFile(
                activeProfileId = "custom",
                serverAssignments = mapOf("example.org:25565" to "custom"),
                profiles = listOf(profile),
            )

        store.save(file)
        val loaded = store.load()

        assertEquals(UiProfileRecovery.NONE, loaded.recovery)
        assertEquals(file, loaded.file)
        assertEquals(profile, store.resolve(loaded.file, "Example.org"))
    }

    @Test
    fun `corrupt primary recovers last good file`() {
        val folder = createTempDirectory("venus-ui-profiles").toFile()
        val store = UiProfileStore(folder)
        val first = FactoryUiProfile.profile.copy(id = "first", name = "First")
        val second = FactoryUiProfile.profile.copy(id = "second", name = "Second")
        store.save(UiProfilesFile(activeProfileId = "first", profiles = listOf(first)))
        store.save(UiProfilesFile(activeProfileId = "second", profiles = listOf(second)))
        folder.resolve("ui-profiles.json").writeText("not json")

        val loaded = store.load()

        assertEquals(UiProfileRecovery.BACKUP, loaded.recovery)
        assertEquals("first", loaded.file.activeProfileId)
    }

    @Test
    fun `profile code validates and round trips`() {
        val store = UiProfileStore(createTempDirectory("venus-ui-profiles").toFile())
        val exported = store.export(FactoryUiProfile.profile)

        assertTrue(exported.startsWith(UiProfileStore.EXPORT_PREFIX))
        assertEquals(FactoryUiProfile.profile, store.import(exported))
        assertFailsWith<IllegalArgumentException> { store.import("invalid") }
    }

    @Test
    fun `validator rejects overlapping modules`() {
        val factory = FactoryUiProfile.profile
        val placements = factory.normalLayout.placements.toMutableList()
        placements[1] = placements[1].copy(column = 0, row = 0)
        val invalid = factory.copy(id = "invalid", normalLayout = UiLayout(placements))

        assertFailsWith<IllegalArgumentException> { UiProfileValidator.validateProfile(invalid) }
    }
}
