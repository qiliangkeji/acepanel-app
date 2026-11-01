package com.acepanel.client.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class FilePickerLauncher internal constructor(private val launchBlock: () -> Unit) {
    actual fun launch() = launchBlock()
}

@Composable
actual fun rememberFilePicker(onResult: (PickedFile?) -> Unit): FilePickerLauncher {
    val current = rememberUpdatedState(onResult)
    return remember {
        FilePickerLauncher {
            try {
                val dialog = FileDialog(null as Frame?, "选择要上传的文件", FileDialog.LOAD)
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) {
                    val selected = File(dir, file)
                    current.value(PickedFile(selected.name, selected.readBytes()))
                } else {
                    current.value(null)
                }
            } catch (t: Throwable) {
                current.value(null)
            }
        }
    }
}
