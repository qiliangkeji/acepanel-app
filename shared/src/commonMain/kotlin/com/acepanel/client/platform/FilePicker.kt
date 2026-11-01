package com.acepanel.client.platform

import androidx.compose.runtime.Composable

data class PickedFile(
    val name: String,
    val bytes: ByteArray,
)

expect class FilePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberFilePicker(onResult: (PickedFile?) -> Unit): FilePickerLauncher
