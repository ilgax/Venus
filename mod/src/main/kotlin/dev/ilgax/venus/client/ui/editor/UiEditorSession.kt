package dev.ilgax.venus.client.ui.editor

import dev.ilgax.venus.client.ui.module.UiModuleRegistry
import dev.ilgax.venus.client.ui.profile.UiLayout
import dev.ilgax.venus.client.ui.profile.UiLayoutMode
import dev.ilgax.venus.client.ui.profile.UiModuleInstance
import dev.ilgax.venus.client.ui.profile.UiModuleType
import dev.ilgax.venus.client.ui.profile.UiPageDefinition
import dev.ilgax.venus.client.ui.profile.UiPlacement
import dev.ilgax.venus.client.ui.profile.UiProfile
import dev.ilgax.venus.client.ui.profile.UiProfileController
import dev.ilgax.venus.client.ui.profile.UiProfileValidator
import java.util.UUID

class UiEditorSession(
    source: UiProfile,
    private val controller: UiProfileController,
) {
    val original: UiProfile = source
    var draft: UiProfile = if (source.id == "factory") controller.editableCopy(source) else source
        private set

    private val undo = ArrayDeque<UiProfile>()
    private val redo = ArrayDeque<UiProfile>()

    val canUndo: Boolean
        get() = undo.isNotEmpty()
    val canRedo: Boolean
        get() = redo.isNotEmpty()

    fun change(transform: (UiProfile) -> UiProfile): Boolean {
        val candidate = transform(draft)
        if (candidate == draft || runCatching { UiProfileValidator.validateProfile(candidate) }.isFailure) return false
        undo.addLast(draft)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        draft = candidate
        redo.clear()
        return true
    }

    fun undo(): Boolean {
        val previous = undo.removeLastOrNull() ?: return false
        redo.addLast(draft)
        draft = previous
        return true
    }

    fun redo(): Boolean {
        val next = redo.removeLastOrNull() ?: return false
        undo.addLast(draft)
        draft = next
        return true
    }

    fun apply(): UiProfile {
        controller.saveProfile(draft)
        return draft
    }

    fun renameProfile(name: String): Boolean = change { it.copy(name = name.trim().take(UiProfileValidator.MAX_TEXT_LENGTH)) }

    fun renamePage(
        pageId: String,
        title: String,
    ): Boolean =
        change { profile ->
            profile.copy(pages = profile.pages.map { if (it.id == pageId) it.copy(title = title.trim()) else it })
        }

    fun renameModule(
        moduleId: String,
        title: String,
    ): Boolean =
        change { profile ->
            profile.copy(modules = profile.modules.map { if (it.id == moduleId) it.copy(title = title.trim()) else it })
        }

    fun move(
        moduleId: String,
        mode: UiLayoutMode,
        column: Int,
        row: Int,
    ): Boolean = updatePlacement(moduleId, mode) { it.copy(column = column, row = row) }

    fun resize(
        moduleId: String,
        mode: UiLayoutMode,
        width: Int,
        height: Int,
    ): Boolean = updatePlacement(moduleId, mode) { it.copy(width = width, height = height) }

    fun addPage(): UiPageDefinition? {
        val index = draft.pages.size + 1
        val page = UiPageDefinition("page-${shortId()}", "Page $index", "dashboard", draft.pages.size)
        return if (change { it.copy(pages = it.pages + page) }) page else null
    }

    fun deletePage(pageId: String): Boolean {
        if (draft.pages.size <= 1) return false
        val normalIds =
            draft.normalLayout.placements
                .filter { it.pageId == pageId }
                .mapTo(mutableSetOf()) { it.moduleId }
        val compactIds =
            draft.compactLayout.placements
                .filter { it.pageId == pageId }
                .mapTo(mutableSetOf()) { it.moduleId }
        val removed = normalIds + compactIds
        return change { profile ->
            profile.copy(
                pages = profile.pages.filterNot { it.id == pageId }.mapIndexed { index, page -> page.copy(order = index) },
                modules = profile.modules.filterNot { it.id in removed },
                normalLayout = UiLayout(profile.normalLayout.placements.filterNot { it.moduleId in removed }),
                compactLayout = UiLayout(profile.compactLayout.placements.filterNot { it.moduleId in removed }),
            )
        }
    }

    fun addModule(
        type: UiModuleType,
        pageId: String,
    ): UiModuleInstance? {
        if (type.singleton && draft.modules.any { it.type == type }) return null
        val module = UiModuleInstance("module-${shortId()}", type, UiModuleRegistry.builtIn.descriptor(type).displayName)
        val normal = findPlacement(draft.normalLayout, module, pageId, UiLayoutMode.NORMAL) ?: return null
        val compact = findPlacement(draft.compactLayout, module, pageId, UiLayoutMode.COMPACT) ?: return null
        return if (
            change {
                it.copy(
                    modules = it.modules + module,
                    normalLayout = UiLayout(it.normalLayout.placements + normal),
                    compactLayout = UiLayout(it.compactLayout.placements + compact),
                )
            }
        ) {
            module
        } else {
            null
        }
    }

    fun deleteModule(moduleId: String): Boolean =
        change {
            it.copy(
                modules = it.modules.filterNot { module -> module.id == moduleId },
                normalLayout = UiLayout(it.normalLayout.placements.filterNot { placement -> placement.moduleId == moduleId }),
                compactLayout = UiLayout(it.compactLayout.placements.filterNot { placement -> placement.moduleId == moduleId }),
            )
        }

    private fun updatePlacement(
        moduleId: String,
        mode: UiLayoutMode,
        transform: (UiPlacement) -> UiPlacement,
    ): Boolean =
        change { profile ->
            if (mode == UiLayoutMode.NORMAL) {
                profile.copy(
                    normalLayout =
                        UiLayout(
                            profile.normalLayout.placements.map {
                                if (it.moduleId ==
                                    moduleId
                                ) {
                                    transform(it)
                                } else {
                                    it
                                }
                            },
                        ),
                )
            } else {
                profile.copy(
                    compactLayout =
                        UiLayout(
                            profile.compactLayout.placements.map {
                                if (it.moduleId ==
                                    moduleId
                                ) {
                                    transform(it)
                                } else {
                                    it
                                }
                            },
                        ),
                )
            }
        }

    private fun findPlacement(
        layout: UiLayout,
        module: UiModuleInstance,
        pageId: String,
        mode: UiLayoutMode,
    ): UiPlacement? {
        val minimum = UiModuleRegistry.builtIn.descriptor(module.type).minimum(mode)
        val existing = layout.placements.filter { it.pageId == pageId && it.visible }
        for (row in 0 until MAX_GRID_ROWS) {
            for (column in 0..mode.columns - minimum.width) {
                val candidate = UiPlacement(module.id, pageId, column, row, minimum.width, minimum.height)
                if (existing.none { candidate.overlaps(it) }) return candidate
            }
        }
        return null
    }

    private fun UiPlacement.overlaps(other: UiPlacement): Boolean =
        column < other.column + other.width &&
            column + width > other.column &&
            row < other.row + other.height &&
            row + height > other.row

    private fun shortId(): String = UUID.randomUUID().toString().take(8)

    private companion object {
        const val MAX_HISTORY = 100
        const val MAX_GRID_ROWS = 128
    }
}
