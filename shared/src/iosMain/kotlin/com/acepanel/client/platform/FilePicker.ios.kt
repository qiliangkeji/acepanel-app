package com.acepanel.client.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class FilePickerLauncher internal constructor(private val launchBlock: () -> Unit) {
    actual fun launch() = launchBlock()
}

@Composable
actual fun rememberFilePicker(onResult: (PickedFile?) -> Unit): FilePickerLauncher {
    return remember { FilePickerLauncher { onResult(null) } }
}
