package com.acepanel.client.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class FilePickerLauncher internal constructor(private val launchBlock: () -> Unit) {
    actual fun launch() = launchBlock()
}

private fun Context.fileName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index) ?: "upload.bin"
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "upload.bin"
}

@Composable
actual fun rememberFilePicker(onResult: (PickedFile?) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val name = context.fileName(uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            PickedFile(name, bytes)
        }.onSuccess(onResult).onFailure { onResult(null) }
    }
    return remember(launcher) {
        FilePickerLauncher { launcher.launch(arrayOf("*/*")) }
    }
}
