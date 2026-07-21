package dev.ilgax.venus.client.ui.module

import dev.ilgax.venus.client.ui.profile.UiProfile
import dev.ilgax.venus.client.ui.profile.UiProfileController

data class UiScreenServices(
    val sendConsoleCommand: (String) -> Unit,
    val subscribeLogs: () -> Unit,
    val requestPlayerList: () -> Unit,
    val requestPlayerDetail: (String) -> Unit,
    val sendPlayerAction: (String, String, Any?) -> String,
    val subscribeStats: () -> Unit,
    val requestFileRoots: () -> String,
    val requestFileList: (String, String, Int) -> String,
    val sendFileAction: (String, String, String, String?, Boolean) -> String,
    val uploadFile: (String, String, String, Boolean) -> String,
    val downloadFile: (String, String, String, Boolean) -> String,
    val openFileEditor: (String, String) -> String,
    val saveEditedFile: (String, String, String, String, Boolean) -> String,
    val cancelFileTransfer: (String) -> Unit,
    val profiles: UiProfileController,
    val openEditor: (UiProfile) -> Unit = {},
)
