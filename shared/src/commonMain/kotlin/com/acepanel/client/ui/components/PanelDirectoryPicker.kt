package com.acepanel.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.FileEntry
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import com.acepanel.client.data.runSafely

/**
 * 面板文件系统目录选择器。所有需要填写目录的表单共用，避免用户手输错误路径。
 */
@Composable
fun PanelDirectoryPickerDialog(
    store: AceStore,
    initialPath: String = "/",
    title: String = "选择目录",
    allowFileSelection: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = AceTheme.colors
    val start = remember(initialPath) {
        initialPath.trim().ifBlank { "/" }.let { if (it.startsWith("/")) it else "/$it" }.trimEnd('/').ifBlank { "/" }
    }
    var path by remember { mutableStateOf(start) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var movedUp by remember { mutableStateOf(false) }

    LaunchedEffect(path, store.api) {
        loading = true
        error = null
        runSafely { store.api?.fileList(path)?.items ?: emptyList() }
            .onSuccess {
                entries = it.sortedWith(
                    compareByDescending<FileEntry> { entry -> entry.dir }
                        .thenBy { entry -> entry.name.lowercase() },
                )
            }
            .onFailure {
                if (path != "/" && !movedUp) {
                    movedUp = true
                    path = path.substringBeforeLast('/').ifBlank { "/" }
                } else {
                    error = store.friendlyError(it)
                    entries = emptyList()
                }
            }
        loading = false
    }

    fun parent(path: String): String = path.trimEnd('/').substringBeforeLast('/').ifBlank { "/" }

    AceDialog(title = title, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleAction(
                Lucide.ChevronLeft,
                { path = parent(path) },
                background = colors.fill,
                tint = colors.text2,
                size = 32.dp,
                iconSize = 16.dp,
                contentDescription = "上一级",
            )
            AceText(
                path,
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text2,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        AceText(
            if (allowFileSelection) "文件夹可进入，文件可直接选择" else "仅可选择文件夹，文件仅供查看",
            style = AceType.caption.copy(fontSize = 10.5.sp),
            color = colors.text3,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface2)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                loading -> AceText("正在读取目录…", style = AceType.caption, color = colors.text3, modifier = Modifier.padding(14.dp))
                error != null -> AceText(error!!, style = AceType.caption, color = colors.error, modifier = Modifier.padding(14.dp))
                entries.isEmpty() -> AceText("当前目录为空", style = AceType.caption, color = colors.text3, modifier = Modifier.padding(14.dp))
                else -> entries.forEach { entry ->
                    val selectableFile = !entry.dir && allowFileSelection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = entry.dir || selectableFile) {
                                if (entry.dir) path = entry.full else onSelect(entry.full)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Spacer(Modifier.size(12.dp))
                        AceIcon(
                            if (entry.dir) Lucide.Folder else Lucide.File,
                            tint = if (entry.dir) colors.accent else colors.text3,
                            size = 17.dp,
                        )
                        Column(Modifier.weight(1f).padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AceText(
                                entry.name,
                                style = AceType.bodySmall,
                                color = if (entry.dir) colors.text1 else colors.text2,
                                maxLines = 1,
                            )
                            if (!entry.dir) {
                                AceText(
                                    listOf("文件", entry.size.ifBlank { "—" }).joinToString(" · "),
                                    style = AceType.caption.copy(fontSize = 10.5.sp),
                                    color = colors.text3,
                                )
                            }
                        }
                        if (entry.dir) {
                            AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 13.dp, modifier = Modifier.padding(end = 12.dp))
                        } else if (allowFileSelection) {
                            AceText("选择", style = AceType.caption, color = colors.accent, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton("选择此目录", { onSelect(path) }, height = 44.dp)
            }
        }
    }
}
