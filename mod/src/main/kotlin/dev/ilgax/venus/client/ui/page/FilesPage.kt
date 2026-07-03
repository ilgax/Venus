package dev.ilgax.venus.client.ui.page

import dev.ilgax.venus.client.ui.component.VenusEmptyState
import dev.ilgax.venus.client.ui.core.Bounds
import dev.ilgax.venus.client.ui.core.ModalKind
import dev.ilgax.venus.client.ui.core.ToastKind
import dev.ilgax.venus.client.ui.core.VenusDimensions
import dev.ilgax.venus.client.ui.core.VenusSpacing
import dev.ilgax.venus.client.ui.core.VenusTheme
import dev.ilgax.venus.client.ui.render.VenusDraw
import dev.ilgax.venus.client.ui.widget.VenusList
import dev.ilgax.venus.client.ui.widget.VenusTextField
import dev.ilgax.venus.client.ui.widget.scrollbarForList
import dev.ilgax.venus.protocol.FileEntryPacket
import dev.ilgax.venus.protocol.FileRootPacket
import dev.ilgax.venus.state.SessionState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path

class FilesPage(
    private val requestRoots: () -> String,
    private val requestList: (String, String, Int) -> String,
    private val action: (String, String, String, String?, Boolean) -> String,
    private val upload: (String, String, String, Boolean) -> String,
    private val download: (String, String, String, Boolean) -> String,
    private val openEditor: (String, String) -> String,
    private val saveEditor: (String, String, String, String, Boolean) -> String,
    private val cancelTransfer: (String) -> Unit,
    private val confirm: (String, String, () -> Unit, ModalKind) -> Unit,
    private val toast: (ToastKind, String, String) -> Unit,
) : VenusPageContract {
    private var bounds = Bounds(0, 0, 0, 0)
    private val pathField = VenusTextField(Minecraft.getInstance().font, width = 200, placeholder = "Server directory")
    private val destinationField = VenusTextField(Minecraft.getInstance().font, width = 200, placeholder = "Server destination")
    private val localField = VenusTextField(Minecraft.getInstance().font, width = 200, placeholder = "Local source / destination")
    private val editor =
        MultiLineEditBox
            .builder()
            .setPlaceholder(Component.literal("File contents"))
            .setShowBackground(true)
            .setShowDecorations(true)
            .build(Minecraft.getInstance().font, 200, 120, Component.literal("File editor"))
            .apply {
                setCharacterLimit(1_048_576)
                setLineLimit(100_000)
                visible = false
            }
    private var list: VenusList? = null
    private var selectedRootId: String? = null
    private var currentPath = ""
    private var selectedPath: String? = null
    private var selectedKind: String? = null
    private var selectedEditable = false
    private var nextOffset: Int? = null
    private var requestedRoots = false
    private var lastListRequest: String? = null
    private var lastActionRequest: String? = null
    private var handledActionRequest: String? = null
    private var loadedEditorHash: String? = null
    private var buttonBounds: Map<String, Bounds> = emptyMap()

    init {
        pathField.editBox.setMaxLength(1024)
        destinationField.editBox.setMaxLength(1024)
        localField.editBox.setMaxLength(4096)
    }

    fun widgets(): List<AbstractWidget> = listOf(pathField.editBox, destinationField.editBox, localField.editBox, editor)

    override fun layout(contentBounds: Bounds) {
        bounds = contentBounds.inset(VenusDimensions.CONTENT_PADDING)
        val topY = bounds.y + 24
        pathField.layout(Bounds(bounds.x + 96, topY, bounds.width - 96 - 120, VenusDimensions.INPUT_HEIGHT))
        val listTop = topY + VenusDimensions.INPUT_HEIGHT + VenusSpacing.SM
        val listHeight = (bounds.height - 132).coerceAtLeast(60)
        list = VenusList(Bounds(bounds.x, listTop, bounds.width, listHeight))
        destinationField.layout(
            Bounds(bounds.x, listTop + listHeight + VenusSpacing.SM, bounds.width / 2 - 4, VenusDimensions.INPUT_HEIGHT),
        )
        localField.layout(
            Bounds(
                bounds.x + bounds.width / 2 + 4,
                listTop + listHeight + VenusSpacing.SM,
                bounds.width / 2 - 4,
                VenusDimensions.INPUT_HEIGHT,
            ),
        )
        editor.setX(bounds.x)
        editor.setY(bounds.y + 24)
        editor.setWidth(bounds.width)
        editor.setHeight((bounds.height - 58).coerceAtLeast(80))
    }

    override fun onEnter() {
        setFieldsVisible(true)
        if (SessionState.sessionActive && !requestedRoots) {
            requestRoots()
            requestedRoots = true
        }
    }

    override fun onLeave() {
        setFieldsVisible(false)
        editor.visible = false
    }

    override fun render(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (!SessionState.sessionActive) {
            requestedRoots = false
        } else if (shouldRequestFileRoots(SessionState.sessionActive, requestedRoots)) {
            requestRoots()
            requestedRoots = true
        }
        resolveActionResult()
        val editState = SessionState.fileEditor
        if (editState != null) {
            renderEditor(g, font, mouseX, mouseY, editState)
            return
        }
        editor.visible = false
        setFieldsVisible(true)
        VenusDraw.text(g, font, "Files", bounds.x, bounds.y, VenusTheme.TEXT, false)
        if (!SessionState.sessionActive) {
            setFieldsVisible(false)
            VenusEmptyState(bounds).apply { message = "Authenticate to manage files" }.render(g, font)
            return
        }
        val roots = SessionState.latestFileRoots?.roots.orEmpty()
        if (roots.isEmpty()) {
            setFieldsVisible(false)
            VenusEmptyState(bounds)
                .apply {
                    message = "File access is disabled"
                    subtext = "Configure at least one server file root"
                }.render(g, font)
            return
        }
        ensureRoot(roots)
        pathField.renderBackground(g, mouseX, mouseY)
        destinationField.renderBackground(g, mouseX, mouseY)
        localField.renderBackground(g, mouseX, mouseY)
        val localPreview = resolvedLocalPreview(localField.value)
        if (localPreview.isNotEmpty()) {
            VenusDraw.text(
                g,
                font,
                font.plainSubstrByWidth("Local: $localPreview", bounds.width),
                bounds.x,
                localField.bounds.bottom + 2,
                VenusTheme.TEXT_DISABLED,
                false,
            )
        }
        val root = roots.first { it.id == selectedRootId }
        val rootBounds = Bounds(bounds.x, bounds.y + 24, 90, VenusDimensions.INPUT_HEIGHT)
        drawButton(g, font, rootBounds, root.label, mouseX, mouseY)
        val refreshBounds = Bounds(bounds.right - 112, bounds.y + 24, 54, VenusDimensions.INPUT_HEIGHT)
        val upBounds = Bounds(bounds.right - 54, bounds.y + 24, 54, VenusDimensions.INPUT_HEIGHT)
        drawButton(g, font, refreshBounds, "Refresh", mouseX, mouseY)
        drawButton(g, font, upBounds, "Up", mouseX, mouseY)

        val entries = currentEntries()
        val list = list ?: return
        list.render(g, mouseX, mouseY, entries.size, scrollbarForList(list) { entries.size }, "Directory is empty") { index, row, hovered ->
            val entry = entries[index]
            val selected = entry.path == selectedPath
            VenusDraw.rect(
                g,
                row,
                if (selected) {
                    VenusTheme.ACTIVE
                } else if (hovered) {
                    VenusTheme.HOVER
                } else {
                    VenusTheme.SURFACE
                },
            )
            val prefix =
                when (entry.kind) {
                    "directory" -> "[D]"
                    "symlink" -> "[L]"
                    else -> "[F]"
                }
            VenusDraw.text(g, font, "$prefix ${entry.name}", row.x + 6, row.y + (row.height - font.lineHeight) / 2, VenusTheme.TEXT, false)
            val size = if (entry.kind == "file") formatBytes(entry.sizeBytes) else ""
            VenusDraw.textRight(g, font, size, row.right - 6, row.y + (row.height - font.lineHeight) / 2, VenusTheme.TEXT_MUTED, false)
        }

        val y = bounds.bottom - 28
        val labels = listOf("Open", "Upload", "Download", "New File", "New Folder", "Move", "Delete", "Next")
        val gap = 3
        val width = ((bounds.width - gap * (labels.size - 1)) / labels.size).coerceAtLeast(42)
        val buttons = linkedMapOf<String, Bounds>()
        labels.forEachIndexed { index, label ->
            val b = Bounds(bounds.x + index * (width + gap), y, width, 22)
            buttons[label] = b
            drawButton(g, font, b, label, mouseX, mouseY, label != "Next" || nextOffset != null, label == "Delete")
        }
        buttonBounds = buttons + mapOf("Root" to rootBounds, "Refresh" to refreshBounds, "Up" to upBounds)
        renderTransfer(g, font)
    }

    private fun renderEditor(
        g: GuiGraphics,
        font: Font,
        mouseX: Int,
        mouseY: Int,
        state: dev.ilgax.venus.state.FileEditorState,
    ) {
        setFieldsVisible(false)
        editor.visible = true
        if (loadedEditorHash != state.sha256) {
            editor.setValue(state.content)
            loadedEditorHash = state.sha256
        }
        VenusDraw.text(g, font, "Editing ${state.path}", bounds.x, bounds.y, VenusTheme.TEXT, false)
        val save = Bounds(bounds.right - 162, bounds.bottom - 26, 76, 22)
        val force = Bounds(bounds.right - 82, bounds.bottom - 26, 78, 22)
        val close = Bounds(bounds.x, bounds.bottom - 26, 64, 22)
        drawButton(g, font, close, "Close", mouseX, mouseY)
        drawButton(g, font, save, "Save", mouseX, mouseY)
        drawButton(g, font, force, "Force Save", mouseX, mouseY, danger = true)
        buttonBounds = mapOf("EditorClose" to close, "EditorSave" to save, "EditorForce" to force)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (button != 0) return false
        val x = mouseX.toInt()
        val y = mouseY.toInt()
        buttonBounds.entries.firstOrNull { it.value.contains(x, y) }?.let {
            handleButton(it.key)
            return true
        }
        val entries = currentEntries()
        val index = list?.hitTest(x, y, entries.size) ?: -1
        if (index >= 0) {
            val entry = entries[index]
            selectedPath = entry.path
            selectedKind = entry.kind
            selectedEditable = entry.editable
            destinationField.setValue(entry.path)
            return true
        }
        return false
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        val entries = currentEntries()
        val list = list ?: return false
        if (!list.bounds.contains(mouseX.toInt(), mouseY.toInt())) return false
        list.scroll(if (scrollY > 0) -1 else 1, entries.size)
        return true
    }

    private fun handleButton(name: String) {
        val root = selectedRootId ?: return
        when (name) {
            "Root" -> cycleRoot()
            "Refresh" -> navigate(pathField.value)
            "Up" -> navigate(currentPath.substringBeforeLast('/', ""))
            "Open" -> {
                val path = selectedPath ?: return
                if (selectedKind == "directory") {
                    navigate(path)
                } else if (selectedEditable) {
                    runRequest { openEditor(root, path) }
                } else {
                    toast(ToastKind.WARN, "Cannot edit file", "Only UTF-8 files up to 1 MiB can be opened in the editor.")
                }
            }
            "Upload" ->
                confirm("Upload file", "Upload and replace the server destination if it exists?", {
                    runRequest { upload(localField.value, root, destinationField.value, true) }
                }, ModalKind.WARN)
            "Download" -> {
                val path = selectedPath ?: return
                val overwrite = localTargetExists(localField.value)
                if (overwrite) {
                    confirm("Replace local file", "The selected local destination exists. Replace it?", {
                        runRequest { download(root, path, localField.value, true) }
                    }, ModalKind.WARN)
                } else {
                    runRequest { download(root, path, localField.value, false) }
                }
            }
            "New File" -> runAction("create_file", destinationField.value)
            "New Folder" -> runAction("create_directory", destinationField.value)
            "Move" -> {
                val source = selectedPath ?: return
                confirm("Move file", "Move and replace the destination if it exists?", {
                    runRequest { action(root, "move", source, destinationField.value, true) }
                }, ModalKind.WARN)
            }
            "Delete" -> {
                val path = selectedPath ?: return
                confirm("Delete file", "Permanently delete $path? Directories are deleted recursively.", {
                    runRequest { action(root, "delete", path, null, false) }
                }, ModalKind.DANGER)
            }
            "Next" ->
                nextOffset?.let {
                    lastListRequest = requestList(root, currentPath, it)
                    lastActionRequest = lastListRequest
                }
            "EditorClose" -> {
                SessionState.closeFileEditor()
                loadedEditorHash = null
            }
            "EditorSave" -> saveCurrentEditor(force = false)
            "EditorForce" ->
                confirm("Force save", "Overwrite the server file even if it changed?", {
                    saveCurrentEditor(force = true)
                }, ModalKind.DANGER)
            "CancelTransfer" ->
                SessionState.activeFileTransfers.lastOrNull { it.status == "running" }?.let {
                    cancelTransfer(
                        it.transferId,
                    )
                }
        }
    }

    private fun saveCurrentEditor(force: Boolean) {
        val state = SessionState.fileEditor ?: return
        runRequest { saveEditor(state.rootId, state.path, editor.value, state.sha256, force) }
    }

    private fun runAction(
        actionName: String,
        path: String,
    ) {
        val root = selectedRootId ?: return
        runRequest { action(root, actionName, path, null, false).also { lastActionRequest = it } }
    }

    private fun runRequest(block: () -> String) {
        runCatching(block)
            .onSuccess { lastActionRequest = it }
            .onFailure { toast(ToastKind.DANGER, "File request failed", it.message ?: "Invalid path") }
    }

    private fun ensureRoot(roots: List<FileRootPacket>) {
        if (selectedRootId !in roots.map { it.id }) {
            selectedRootId = roots.first().id
            navigate("")
        }
    }

    private fun cycleRoot() {
        val roots = SessionState.latestFileRoots?.roots.orEmpty()
        if (roots.isEmpty()) return
        val index = roots.indexOfFirst { it.id == selectedRootId }.coerceAtLeast(0)
        selectedRootId = roots[(index + 1) % roots.size].id
        navigate("")
    }

    private fun navigate(path: String) {
        val root = selectedRootId ?: return
        currentPath = path.replace('\\', '/').trim('/')
        pathField.setValue(currentPath)
        selectedPath = null
        selectedKind = null
        selectedEditable = false
        lastListRequest = requestList(root, currentPath, 0)
        lastActionRequest = lastListRequest
    }

    private fun currentEntries(): List<FileEntryPacket> {
        val packet = SessionState.latestFileList ?: return emptyList()
        if (!isCurrentFileList(packet, selectedRootId, currentPath, lastListRequest)) return emptyList()
        nextOffset = packet.nextOffset
        return packet.entries
    }

    private fun resolveActionResult() {
        val result = SessionState.latestFileActionResult ?: return
        if (result.requestId != lastActionRequest || result.requestId == handledActionRequest) return
        handledActionRequest = result.requestId
        toast(
            if (result.success) ToastKind.SUCCESS else ToastKind.DANGER,
            if (result.success) "File action complete" else "File action failed",
            result.message,
        )
        if (result.success) navigate(currentPath)
    }

    private fun setFieldsVisible(visible: Boolean) {
        pathField.setVisible(visible)
        destinationField.setVisible(visible)
        localField.setVisible(visible)
    }

    private fun renderTransfer(
        g: GuiGraphics,
        font: Font,
    ) {
        val transfer = SessionState.activeFileTransfers.lastOrNull() ?: return
        val text =
            "${transfer.direction}: ${formatBytes(transfer.transferredBytes)} / ${formatBytes(transfer.totalBytes)} " +
                "${formatBytes(transfer.bytesPerSecond)}/s ${transfer.status}"
        VenusDraw.textRight(g, font, text, bounds.right, bounds.y, VenusTheme.TEXT_MUTED, false)
        if (transfer.status == "running") {
            val cancel = Bounds(bounds.right - 48, bounds.y + 12, 48, 18)
            drawButton(g, font, cancel, "Cancel", -1, -1, danger = true)
            buttonBounds = buttonBounds + ("CancelTransfer" to cancel)
        }
    }

    private fun drawButton(
        g: GuiGraphics,
        font: Font,
        button: Bounds,
        label: String,
        mouseX: Int,
        mouseY: Int,
        enabled: Boolean = true,
        danger: Boolean = false,
    ) {
        val color =
            if (!enabled) {
                VenusTheme.SURFACE
            } else if (button.contains(mouseX, mouseY)) {
                VenusTheme.HOVER
            } else if (danger) {
                VenusTheme.DANGER_DIM
            } else {
                VenusTheme.RAISED
            }
        VenusDraw.rect(g, button, color)
        VenusDraw.border(g, button, if (danger) VenusTheme.DANGER else VenusTheme.BORDER)
        VenusDraw.textCentered(g, font, label, button, if (enabled) VenusTheme.TEXT else VenusTheme.TEXT_DISABLED, false)
    }

    private fun localTargetExists(raw: String): Boolean =
        runCatching {
            val path = Path.of(raw)
            val resolved =
                if (path.isAbsolute) {
                    path
                } else {
                    Minecraft
                        .getInstance()
                        .gameDirectory
                        .toPath()
                        .resolve(path)
                }
            Files.exists(resolved.toAbsolutePath().normalize())
        }.getOrDefault(false)

    private fun resolvedLocalPreview(raw: String): String =
        runCatching {
            if (raw.isBlank()) return@runCatching ""
            val path = Path.of(raw)
            (
                if (path.isAbsolute) {
                    path
                } else {
                    Minecraft
                        .getInstance()
                        .gameDirectory
                        .toPath()
                        .resolve(path)
                }
            ).toAbsolutePath()
                .normalize()
                .toString()
        }.getOrDefault("")

    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1_073_741_824 -> String.format("%.1f GiB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MiB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.1f KiB", bytes / 1024.0)
            else -> "$bytes B"
        }
}

internal fun isCurrentFileList(
    packet: dev.ilgax.venus.protocol.FileListPacket,
    rootId: String?,
    path: String,
    requestId: String?,
): Boolean = packet.rootId == rootId && packet.path == path && packet.requestId == requestId

internal fun shouldRequestFileRoots(
    sessionActive: Boolean,
    requestedRoots: Boolean,
): Boolean = sessionActive && !requestedRoots
