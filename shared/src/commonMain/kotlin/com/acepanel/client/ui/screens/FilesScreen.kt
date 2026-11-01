package com.acepanel.client.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.FileClipboard
import com.acepanel.client.data.FileEntry
import com.acepanel.client.data.formatBytes
import com.acepanel.client.platform.rememberFilePicker
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceDialog
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.CircleAction
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.ErrorState
import com.acepanel.client.ui.components.InlineNotice
import com.acepanel.client.ui.components.LoadingState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.rememberScreenScrollState
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudDownload
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileArchive
import com.composables.icons.lucide.FileImage
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.acepanel.client.data.runSafely

private const val FILES_ROOT = "/"

/**
 * 父目录：/www/wwwroot → /www，/www → /，/ 仍然是 /。
 * 返回根目录时由调用方决定"再返回一次就退出页面"。
 */
private fun parentOf(target: String): String {
    val trimmed = target.trimEnd('/')
    val cut = trimmed.lastIndexOf('/')
    return if (cut <= 0) FILES_ROOT else trimmed.substring(0, cut)
}

/** 文件名（最后一段路径）。 */
private fun nameOf(full: String): String = full.trimEnd('/').substringAfterLast('/')

/** 目录 + 文件名拼接，根目录不需要重复斜杠。 */
private fun joinPath(dir: String, name: String): String =
    if (dir == FILES_ROOT || dir.isBlank()) "/$name" else dir.trimEnd('/') + "/" + name

private val archiveExtensions = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "rar", "7z")

private fun isArchive(name: String): Boolean =
    name.lowercase().endsWith(".tar.gz") || name.lowercase().endsWith(".tar.bz2") ||
        archiveExtensions.contains(name.substringAfterLast('.', "").lowercase())

private fun isImageName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

/**
 * 一次待执行的粘贴：目标目录 + 源/目标路径对 + 已经撞名的目标，
 * 用于在真正调用面板 copy/move 之前让用户确认是否覆盖。
 */
private data class PastePlan(
    val dir: String,
    val items: List<Pair<String, String>>,
    val conflicts: List<String>,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalEncodingApi::class, ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(store: AceStore) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(FILES_ROOT) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sort by remember { mutableStateOf("name") }
    var showNew by remember { mutableStateOf(false) }
    var showCompress by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FileContent?>(null) }
    var uploading by remember { mutableStateOf<String?>(null) }

    suspend fun load(target: String = path) {
        loading = true
        error = null
        runSafely {
            val page = store.api?.fileList(target, sort = sort)
            entries = page?.items ?: emptyList()
            total = page?.total?.toInt() ?: 0
            path = target
            selected = emptySet()
        }.onFailure { error = store.friendlyError(it) }
        loading = false
    }

    LaunchedEffect(store.api) { load("/") }
    LaunchedEffect(sort) { load(path) }

    // 返回键优先解释成"退到上一级目录"：还在子目录里就加载父目录，
    // 已经在根目录时返回 false，让全局把整个文件页面出栈。
    val upOneLevel: () -> Boolean = {
        if (path == FILES_ROOT) {
            false
        } else {
            scope.launch { load(parentOf(path)) }
            true
        }
    }
    val latestUpOneLevel by rememberUpdatedState(upOneLevel)
    DisposableEffect(Unit) {
        val handler: () -> Boolean = { latestUpOneLevel() }
        store.pageBackHandler = handler
        onDispose {
            if (store.pageBackHandler === handler) store.pageBackHandler = null
        }
    }

    // 页面重新进入时滚动位置由 ScrollStateMemory 恢复；只有页面内切换目录才回到顶部。
    val screenScroll = rememberScreenScrollState()
    var scrolledPath by remember { mutableStateOf(path) }
    LaunchedEffect(path) {
        if (path != scrolledPath) {
            scrolledPath = path
            screenScroll.scrollTo(0)
        }
    }

    val picker = rememberFilePicker { picked ->
        if (picked == null) return@rememberFilePicker
        uploading = picked.name
        scope.launch {
            runSafely { store.api?.uploadFile(path, picked.name, picked.bytes) }
                .onSuccess {
                    store.toast("${picked.name} 上传完成")
                    load(path)
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
            uploading = null
        }
    }

    val clipboard = LocalClipboardManager.current
    var selectionMode by remember { mutableStateOf(false) }
    var sheetEntry by remember { mutableStateOf<FileEntry?>(null) }
    var renameEntry by remember { mutableStateOf<FileEntry?>(null) }
    var permissionTargets by remember { mutableStateOf<List<FileEntry>?>(null) }
    var propertiesEntry by remember { mutableStateOf<FileEntry?>(null) }
    var pendingPaste by remember { mutableStateOf<PastePlan?>(null) }
    var busyAction by remember { mutableStateOf<String?>(null) }

    fun copyPath(target: String) {
        clipboard.setText(AnnotatedString(target))
        store.toast("路径已复制到剪贴板")
    }

    fun openEntry(entry: FileEntry) {
        if (entry.dir) {
            scope.launch { load(entry.full) }
            return
        }
        scope.launch {
            runSafely { store.api?.fileContent(entry.full) }
                .onSuccess { obj ->
                    if (obj == null) {
                        store.toast("服务器未返回文件内容", true)
                        return@onSuccess
                    }
                    val base64 = (obj["content"] as? JsonPrimitive)?.content
                    if (base64 == null) {
                        store.toast("服务器未返回文件内容", true)
                        return@onSuccess
                    }
                    val bytes = runSafely {
                        Base64.decode(base64.filterNot { it.isWhitespace() })
                    }.getOrNull()
                    if (bytes == null) {
                        store.toast("文件内容解码失败，可能文件过大或接口返回异常", true)
                        return@onSuccess
                    }
                    val mime = (obj["mime"] as? JsonPrimitive)?.content.orEmpty()
                    preview = FileContent(entry.full, bytes, mime)
                }
                .onFailure { failure ->
                    val message = store.friendlyError(failure)
                    if (message.contains("too large", true) || message.contains("过大")) {
                        runSafely { store.api?.fileTail(entry.full, 500) ?: emptyList() }
                            .onSuccess { lines ->
                                if (lines.isEmpty()) {
                                    store.toast(message, true)
                                } else {
                                    preview = FileContent(
                                        entry.full,
                                        lines.joinToString(separator = 10.toChar().toString()).encodeToByteArray(),
                                        "text/plain · 仅显示最后 500 行",
                                    )
                                }
                            }
                            .onFailure { store.toast(message, true) }
                    } else {
                        store.toast(message, true)
                    }
                }
        }
    }

    fun pasteInto(items: List<Pair<String, String>>, force: Boolean) {
        val clip = store.fileClipboard ?: return
        if (items.isEmpty()) {
            store.clearFileClipboard()
            pendingPaste = null
            store.toast("没有需要粘贴的项目")
            return
        }
        busyAction = if (clip.move) "移动中…" else "复制中…"
        pendingPaste = null
        scope.launch {
            runSafely {
                if (clip.move) store.api?.moveFiles(items, force) else store.api?.copyFiles(items, force)
            }.onSuccess {
                store.clearFileClipboard()
                store.toast(if (clip.move) "已移动 ${items.size} 项" else "已复制 ${items.size} 项")
                // 粘贴到子目录时仍停留在当前列表，避免用户被"传送"到别处
                load(path)
            }.onFailure { store.toast(store.friendlyError(it), true) }
            busyAction = null
        }
    }

    /** 粘贴前用面板的 exist 接口探测同名，冲突时弹覆盖确认，避免被服务端静默跳过。 */
    fun requestPaste(dir: String) {
        val clip = store.fileClipboard ?: return
        if (clip.paths.isEmpty()) return
        busyAction = "检查冲突…"
        scope.launch {
            val targets = clip.paths.map { it to joinPath(dir, nameOf(it)) }.filter { it.first != it.second }
            if (targets.isEmpty()) {
                busyAction = null
                store.toast("目标就是当前目录，无需粘贴")
                return@launch
            }
            val existing = runSafely { store.api?.fileExist(targets.map { it.second }) }.getOrNull().orEmpty()
            busyAction = null
            val conflicts = targets.filterIndexed { index, pair -> existing.getOrElse(index) { false } }
            if (conflicts.isNotEmpty()) {
                pendingPaste = PastePlan(dir, targets, conflicts.map { nameOf(it.second) })
            } else {
                pasteInto(targets, force = false)
            }
        }
    }

    fun doRename(entry: FileEntry, newName: String) {
        val target = joinPath(parentOf(entry.full), newName)
        renameEntry = null
        busyAction = "重命名…"
        scope.launch {
            runSafely { store.api?.moveFiles(listOf(entry.full to target)) }
                .onSuccess {
                    store.toast("已重命名为 $newName")
                    load(path)
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
            busyAction = null
        }
    }

    fun doPermission(targets: List<FileEntry>, mode: String, owner: String, group: String) {
        busyAction = "设置权限…"
        scope.launch {
            var failed = 0
            targets.forEach { entry ->
                runSafely { store.api?.setFilePermission(entry.full, mode, owner, group) }.onFailure { failed++ }
            }
            busyAction = null
            permissionTargets = null
            store.toast(
                if (failed == 0) "已更新 ${targets.size} 项权限" else "有 $failed 项权限设置失败",
                failed > 0,
            )
            load(path)
        }
    }

    fun doDelete(targets: List<FileEntry>) {
        if (targets.isEmpty()) return
        sheetEntry = null
        busyAction = "删除中…"
        scope.launch {
            var failed = 0
            targets.forEach { entry ->
                runSafely { store.api?.deleteFile(entry.full) }.onFailure { failed++ }
            }
            selected = selected - targets.map { it.full }.toSet()
            busyAction = null
            store.toast(
                if (failed == 0) "已删除 ${targets.size} 项" else "有 $failed 项删除失败",
                failed > 0,
            )
            load(path)
        }
    }

    fun doUncompress(entry: FileEntry) {
        sheetEntry = null
        busyAction = "解压中…"
        scope.launch {
            runSafely { store.api?.uncompressFile(entry.full, parentOf(entry.full)) }
                .onSuccess {
                    store.toast("已解压到 ${parentOf(entry.full)}")
                    load(path)
                }
                .onFailure { store.toast(store.friendlyError(it), true) }
            busyAction = null
        }
    }

    fun toggleSelection(entry: FileEntry) {
        selectionMode = true
        selected = if (selected.contains(entry.full)) selected - entry.full else selected + entry.full
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "文件",
            subtitle = path,
            onBack = { if (!upOneLevel()) store.closePage() },
            actions = {
                CircleAction(Lucide.Upload, { picker.launch() }, background = colors.accent, tint = Color.White, contentDescription = "上传文件")
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(screenScroll)
                .padding(start = 26.dp, end = 26.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Breadcrumb(path) { target -> scope.launch { load(target) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileTool("上传", Lucide.Upload, Modifier.weight(1f)) { picker.launch() }
                FileTool("新建", Lucide.Plus, Modifier.weight(1f)) { showNew = true }
                FileTool(
                    if (selectionMode) "取消多选" else "多选",
                    if (selectionMode) Lucide.X else Lucide.Square,
                    Modifier.weight(1f),
                    active = selectionMode,
                ) {
                    selectionMode = !selectionMode
                    if (!selectionMode) selected = emptySet()
                }
                FileTool("压缩", Lucide.Archive, Modifier.weight(1f)) { showCompress = true }
                FileTool("更多", Lucide.Ellipsis, Modifier.weight(1f)) { showMore = true }
            }
            store.fileClipboard?.let { clip ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InlineNotice(
                        (if (clip.move) "待移动 " else "待复制 ") + "${clip.paths.size} 项 → $path",
                        modifier = Modifier.weight(1f),
                        color = colors.accent,
                        background = colors.accentSoft,
                        icon = if (clip.move) Lucide.Folder else Lucide.Copy,
                    )
                    SoftPillButton(
                        busyAction?.takeIf { it.contains("复制") || it.contains("移动") || it.contains("冲突") }
                            ?: "粘贴",
                        { requestPaste(path) },
                        height = 38.dp,
                        tint = colors.text2,
                        background = colors.fill,
                    )
                    CircleAction(Lucide.X, { store.clearFileClipboard() }, size = 38.dp, iconSize = 15.dp, contentDescription = "取消粘贴")
                }
            }
            if (uploading != null) {
                InlineNotice("正在上传 ${uploading}…", color = colors.accent, background = colors.accentSoft, icon = Lucide.Upload)
            }
            // 复制 / 移动的进度显示在剪贴板条的按钮上，这里只提示其它耗时操作
            busyAction?.takeIf { !it.contains("复制") && !it.contains("移动") && !it.contains("冲突") }?.let {
                InlineNotice(it, color = colors.accent, background = colors.accentSoft, icon = Lucide.Ellipsis)
            }
            if (selectionMode) {
                InlineNotice(
                    if (selected.isEmpty()) "点按文件可多选，长按任意文件直接打开操作菜单"
                    else "已选 ${selected.size} 项，可用压缩 / 更多里的批量操作",
                    color = colors.text2,
                    background = colors.fill,
                    icon = Lucide.ListChecks,
                )
            }
            when {
                loading -> LoadingState(rows = 5)
                error != null -> ErrorState(error!!, onRetry = { scope.launch { load(path) } })
                entries.isEmpty() -> EmptyState(
                    title = "这个目录是空的",
                    subtitle = "上传文件或新建文件夹开始管理",
                    icon = Lucide.Folder,
                    actionText = "上传文件",
                    onAction = { picker.launch() },
                )
                else -> AceCard(padding = 0.dp) {
                    entries.forEachIndexed { index, entry ->
                        FileRow(
                            entry = entry,
                            selected = selected.contains(entry.full),
                            marked = store.fileClipboard?.paths?.contains(entry.full) == true,
                            // 多选模式下点按即勾选，普通模式下点按打开；长按与右侧按钮都弹操作菜单
                            onClick = { if (selectionMode) toggleSelection(entry) else openEntry(entry) },
                            onLongClick = { sheetEntry = entry },
                            onMore = { sheetEntry = entry },
                        )
                        if (index != entries.lastIndex) {
                            Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(1.dp).background(colors.divider))
                        }
                    }
                }
            }
            if (!loading) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AceText(
                        "共 $total 项" + if (selected.isNotEmpty()) " · 已选 ${selected.size} 项" else "",
                        style = AceType.caption,
                        color = colors.text3,
                        modifier = Modifier.weight(1f),
                    )
                    AceText(
                        when (sort) {
                            "modify" -> "按修改时间排序"
                            "size" -> "按大小排序"
                            else -> "按名称排序"
                        },
                        style = AceType.caption.copy(fontWeight = FontWeight.Medium),
                        color = colors.accent,
                        modifier = Modifier.clickable {
                            sort = when (sort) {
                                "name" -> "modify"
                                "modify" -> "size"
                                else -> "name"
                            }
                        },
                    )
                }
            }
        }
    }

    if (showNew) {
        NewEntryDialog(store, path, { showNew = false }) {
            showNew = false
            scope.launch { load(path) }
        }
    }
    if (showCompress) {
        CompressFilesDialog(store, path, entries.filter { selected.contains(it.full) }.map { it.full }, { showCompress = false }) {
            showCompress = false
            scope.launch { load(path) }
        }
    }
    val selectedEntries = selected.mapNotNull { full -> entries.firstOrNull { it.full == full } }
    if (showMore) {
        MoreDialog(
            selected = selectedEntries,
            clipboard = store.fileClipboard,
            onDismiss = { showMore = false },
            onClearSelection = { selected = emptySet(); showMore = false },
            onCopy = { store.markFiles(false, selectedEntries.map { it.full }); showMore = false },
            onMove = { store.markFiles(true, selectedEntries.map { it.full }); showMore = false },
            onPermission = { permissionTargets = selectedEntries; showMore = false },
            onPasteHere = { requestPaste(path); showMore = false },
            onClearClipboard = { store.clearFileClipboard() },
            onDelete = { doDelete(selectedEntries); showMore = false },
            onRemoteDownload = { url, name ->
                scope.launch {
                    runSafely {
                        store.api?.postData(
                            "/api/file/remote_download",
                            buildJsonObject {
                                put("path", joinPath(path, name))
                                put("url", url)
                            },
                        )
                    }.onSuccess {
                        store.toast("已开始远程下载")
                        showMore = false
                        load(path)
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            },
        )
    }
    sheetEntry?.let { entry ->
        FileActionSheet(
            entry = entry,
            clipboard = store.fileClipboard,
            onDismiss = { sheetEntry = null },
            onOpen = { sheetEntry = null; openEntry(entry) },
            onUncompress = { doUncompress(entry) },
            onCopy = {
                store.markFiles(false, listOf(entry.full))
                sheetEntry = null
                store.toast("已复制 1 项，进入目标目录后粘贴")
            },
            onMove = {
                store.markFiles(true, listOf(entry.full))
                sheetEntry = null
                store.toast("已标记移动，进入目标目录后粘贴")
            },
            onPasteInto = {
                sheetEntry = null
                requestPaste(entry.full)
            },
            onRename = { renameEntry = entry; sheetEntry = null },
            onPermission = { permissionTargets = listOf(entry); sheetEntry = null },
            onCopyPath = { copyPath(entry.full); sheetEntry = null },
            onTerminal = { store.openFileTerminal(if (entry.dir) entry.full else parentOf(entry.full)) },
            onProperties = { propertiesEntry = entry; sheetEntry = null },
            onSelect = { toggleSelection(entry); sheetEntry = null },
            onDelete = { doDelete(listOf(entry)) },
        )
    }
    renameEntry?.let { entry ->
        RenameDialog(
            entry = entry,
            onDismiss = { renameEntry = null },
            onSubmit = { newName -> doRename(entry, newName) },
        )
    }
    permissionTargets?.let { targets ->
        PermissionDialog(
            entries = targets,
            onDismiss = { permissionTargets = null },
            onSubmit = { mode, owner, group -> doPermission(targets, mode, owner, group) },
        )
    }
    propertiesEntry?.let { entry ->
        PropertiesDialog(
            store = store,
            entry = entry,
            onDismiss = { propertiesEntry = null },
            onCopyPath = { copyPath(entry.full) },
        )
    }
    pendingPaste?.let { plan ->
        val clip = store.fileClipboard
        AceDialog(title = "同名文件已存在", onDismiss = { pendingPaste = null }) {
            AceText(
                "${plan.dir} 里已有 ${plan.conflicts.size} 个同名项，" +
                    (if (clip?.move == true) "移动" else "复制") + "会跳过它们。是否覆盖？",
                style = AceType.bodySmall,
                color = colors.text2,
            )
            Spacer(Modifier.height(6.dp))
            plan.conflicts.take(4).forEach { label ->
                AceText("· $label", style = AceType.caption, color = colors.text3)
            }
            if (plan.conflicts.size > 4) {
                AceText("… 等共 ${plan.conflicts.size} 项", style = AceType.caption, color = colors.text3)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPillButton("取消", { pendingPaste = null }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                SoftPillButton(
                    "跳过",
                    {
                        pendingPaste = null
                        pasteInto(plan.items.filter { !plan.conflicts.contains(nameOf(it.second)) }, force = false)
                    },
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                    tint = colors.text2,
                    background = colors.fill,
                )
                PrimaryPillButton("覆盖", { pasteInto(plan.items, force = true) }, modifier = Modifier.weight(1f), height = 44.dp)
            }
        }
    }
    preview?.let { file ->
        FilePreviewDialog(
            file = file,
            onDismiss = { preview = null },
            onSave = { content ->
                scope.launch {
                    runSafely {
                        store.api?.saveFile(file.path, content)
                    }.onSuccess {
                        store.toast("文件已保存")
                        preview = null
                    }.onFailure { store.toast(store.friendlyError(it), true) }
                }
            },
        )
    }
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val colors = AceTheme.colors
    val parts = path.trim('/').split("/").filter { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AceText(
            "根目录",
            style = AceType.caption,
            color = if (parts.isEmpty()) colors.text1 else colors.text3,
            modifier = Modifier.clickable { onNavigate("/") },
        )
        var current = ""
        parts.forEachIndexed { index, part ->
            current += "/$part"
            AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 12.dp)
            AceText(
                part,
                style = AceType.caption.copy(fontWeight = if (index == parts.lastIndex) FontWeight.SemiBold else FontWeight.Normal),
                color = if (index == parts.lastIndex) colors.text1 else colors.text3,
                modifier = Modifier.clickable { onNavigate(current) },
            )
        }
    }
}

@Composable
private fun FileTool(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AceTheme.colors
    AceCard(
        modifier = modifier.clickable(onClick = onClick),
        padding = 12.dp,
        color = if (active) colors.accentSoft else colors.surface,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            AceIcon(icon, tint = colors.accent, size = 20.dp)
            AceText(label, style = AceType.caption.copy(fontSize = 10.5.sp), color = colors.text1, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = AceTheme.colors
    val icon = when {
        entry.dir -> Lucide.Folder
        entry.name.endsWith(".zip") || entry.name.endsWith(".tar.gz") || entry.name.endsWith(".tgz") -> Lucide.FileArchive
        entry.name.endsWith(".png") || entry.name.endsWith(".jpg") || entry.name.endsWith(".webp") -> Lucide.FileImage
        entry.name.endsWith(".txt") || entry.name.endsWith(".conf") || entry.name.endsWith(".log") || entry.name.endsWith(".env") -> Lucide.FileText
        else -> Lucide.File
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accentSoft.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AceIcon(icon, tint = if (entry.dir) colors.accent else colors.text2, size = 19.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AceText(
                entry.name,
                style = AceType.listPrimary,
                color = colors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AceText(
                entry.modify,
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                color = colors.text3,
                maxLines = 1,
            )
        }
        when {
            selected -> Box(
                Modifier.size(18.dp).clip(CircleShape).background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.Check, tint = Color.White, size = 11.dp)
            }
            // 已在复制 / 移动暂存区里的源文件，给一个明确的标记
            marked -> AceText(
                if (entry.dir) "待移动" else "已标记",
                style = AceType.caption,
                color = colors.accent,
                maxLines = 1,
            )
            else -> AceText(
                entry.size.ifBlank { "—" },
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 76.dp),
            )
        }
        // 长按之外的入口：桌面端没有长按，也靠这个按钮打开操作菜单
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            AceIcon(Lucide.EllipsisVertical, tint = colors.text3, size = 16.dp)
        }
    }
}

private data class FileContent(val path: String, val bytes: ByteArray, val mime: String) {
    val extension: String get() = path.substringAfterLast('.', "").lowercase()
    val isImage: Boolean
        get() = extension in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp") ||
            (mime.startsWith("image/", true) && extension != "svg")
    val isText: Boolean
        get() = !isImage && looksLikeText(bytes, mime)
}

private fun looksLikeText(bytes: ByteArray, mime: String): Boolean {
    val lower = mime.lowercase()
    if (lower.startsWith("text/")) return true
    if (listOf("json", "xml", "javascript", "yaml", "toml", "x-sh", "shell").any { lower.contains(it) }) return true
    if (lower.startsWith("image/") || lower.startsWith("audio/") || lower.startsWith("video/") ||
        lower.contains("zip") || lower.contains("gzip") || lower.contains("pdf") || lower.contains("octet-stream") && bytes.any { it == 0.toByte() }
    ) {
        return false
    }
    if (bytes.isEmpty()) return true
    val sample = bytes.copyOfRange(0, minOf(bytes.size, 4096))
    if (sample.any { it == 0.toByte() }) return false
    val control = sample.count { byte ->
        val value = byte.toInt() and 0xff
        value < 9 || value in 14..31
    }
    return control * 100 / sample.size < 2
}

private fun hexPreview(bytes: ByteArray, limit: Int = 192): String {
    val data = bytes.copyOfRange(0, minOf(bytes.size, limit))
    return data.toList().chunked(16).joinToString("\n") { row ->
        row.joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

@Composable
private fun FilePreviewDialog(file: FileContent, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = AceTheme.colors
    val initialText = remember(file.path) { if (file.isText) file.bytes.decodeToString() else "" }
    val bitmap = remember(file.path) {
        if (file.isImage) runSafely { file.bytes.decodeToImageBitmap() }.getOrNull() else null
    }
    var content by remember(file.path) { mutableStateOf(initialText) }
    var editing by remember(file.path) { mutableStateOf(false) }
    AceDialog(title = file.path.substringAfterLast('/'), onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AceText(
                file.mime.ifBlank { when {
                    file.extension.isNotBlank() -> ".${file.extension}"
                    else -> "application/octet-stream"
                } },
                style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.text3,
                modifier = Modifier.weight(1f),
            )
            AceText(formatBytes(file.bytes.size.toLong()), style = AceType.caption, color = colors.text3)
        }
        Spacer(Modifier.height(10.dp))
        when {
            bitmap != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = file.path.substringAfterLast('/'),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 304.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            file.isText || editing -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (editing) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.text1),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = content.ifBlank { "（空文件）" },
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = colors.text1),
                        )
                    }
                }
            }
            else -> {
                InlineNotice(
                    "这是二进制文件，无法以文本方式预览。可通过终端、SFTP 或下载到本地后查看。",
                    color = colors.warning,
                    background = colors.warningSoft,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.terminal)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = hexPreview(file.bytes).ifBlank { "<空文件>" },
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = colors.terminalText,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (file.isText) {
                SoftPillButton(if (editing) "取消编辑" else "编辑", {
                    if (editing) content = initialText else editing = true
                }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                if (editing) {
                    PrimaryPillButton("保存", { onSave(content) }, modifier = Modifier.weight(1f), height = 44.dp)
                }
            } else {
                PrimaryPillButton("关闭", onDismiss, height = 44.dp)
            }
        }
    }
}

@Composable
private fun NewEntryDialog(store: AceStore, path: String, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var isDir by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AceDialog(title = "新建", onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("文件", { isDir = false }, height = 38.dp, background = if (!isDir) colors.accentSoft else colors.fill, tint = if (!isDir) colors.accent else colors.text2)
            SoftPillButton("文件夹", { isDir = true }, height = 38.dp, background = if (isDir) colors.accentSoft else colors.fill, tint = if (isDir) colors.accent else colors.text2)
        }
        Spacer(Modifier.height(12.dp))
        AceTextField(name, { name = it }, placeholder = if (isDir) "文件夹名称" else "文件名，如 app.conf")
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            InlineNotice(error!!, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(if (busy) "创建中…" else "创建", {
            if (busy) return@PrimaryPillButton
            if (name.isBlank()) {
                error = "请输入名称"
                return@PrimaryPillButton
            }
            busy = true
            error = null
            val target = path.trimEnd('/') + "/" + name.trim()
            scope.launch {
                runSafely { store.api?.createFile(target, isDir) }
                    .onSuccess { onCreated() }
                    .onFailure { error = store.friendlyError(it) }
                busy = false
            }
        }, height = 44.dp)
    }
}

@Composable
private fun CompressFilesDialog(
    store: AceStore,
    path: String,
    selectedPaths: List<String>,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("archive.tar.gz") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AceDialog(title = "压缩文件", onDismiss = onDismiss) {
        AceText(
            if (selectedPaths.isEmpty()) "先用上方“多选”勾选文件，或在长按菜单里点“加入多选”。" else "将 ${selectedPaths.size} 个文件压缩为归档文件。",
            style = AceType.bodySmall,
            color = colors.text2,
        )
        Spacer(Modifier.height(12.dp))
        AceTextField(name, { name = it }, placeholder = "archive.tar.gz")
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            InlineNotice(error!!, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton(if (busy) "压缩中…" else "开始压缩", {
            if (busy) return@PrimaryPillButton
            if (selectedPaths.isEmpty()) {
                error = "请先选择文件"
                return@PrimaryPillButton
            }
            busy = true
            scope.launch {
                val file = path.trimEnd('/') + "/" + name.trim()
                runSafely { store.api?.compressFiles(path, selectedPaths, file) }
                    .onSuccess { onCreated() }
                    .onFailure { error = store.friendlyError(it) }
                busy = false
            }
        }, height = 44.dp)
    }
}

@Composable
private fun MoreDialog(
    selected: List<FileEntry>,
    clipboard: FileClipboard?,
    onDismiss: () -> Unit,
    onClearSelection: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onPermission: () -> Unit,
    onPasteHere: () -> Unit,
    onClearClipboard: () -> Unit,
    onDelete: () -> Unit,
    onRemoteDownload: (String, String) -> Unit,
) {
    val colors = AceTheme.colors
    var remote by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AceDialog(title = "文件操作", onDismiss = onDismiss, scrollable = true) {
        if (!remote) {
            val hasSelection = selected.isNotEmpty()
            if (clipboard != null) {
                MoreAction(
                    "粘贴到当前目录",
                    (if (clipboard.move) "移动 " else "复制 ") + "${clipboard.paths.size} 项",
                    icon = Lucide.Download,
                    onClick = onPasteHere,
                )
                MoreAction("清空暂存", "放弃未粘贴的 ${clipboard.paths.size} 项", icon = Lucide.X, onClick = onClearClipboard)
            }
            MoreAction("复制所选", if (hasSelection) "${selected.size} 项" else "先长按/勾选文件", icon = Lucide.Copy, enabled = hasSelection, onClick = onCopy)
            MoreAction("移动所选", if (hasSelection) "${selected.size} 项" else "先长按/勾选文件", icon = Lucide.Folder, enabled = hasSelection, onClick = onMove)
            MoreAction("批量改权限", if (hasSelection) "${selected.size} 项" else "先长按/勾选文件", icon = Lucide.KeyRound, enabled = hasSelection, onClick = onPermission)
            MoreAction("清除选择", "已选 ${selected.size} 项", icon = Lucide.ListChecks, onClick = onClearSelection)
            MoreAction(
                "删除所选",
                if (selected.isEmpty()) "长按文件后选择" else "删除 ${selected.size} 个文件",
                icon = Lucide.Trash2,
                danger = true,
                enabled = hasSelection,
                onClick = onDelete,
            )
            MoreAction("远程下载", "从 URL 下载到当前目录", icon = Lucide.CloudDownload, onClick = { remote = true })
        } else {
            AceText("文件 URL", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(6.dp))
            AceTextField(url, { url = it }, placeholder = "https://example.com/file.tar.gz")
            Spacer(Modifier.height(10.dp))
            AceText("保存文件名", style = AceType.caption, color = colors.text3)
            Spacer(Modifier.height(6.dp))
            AceTextField(name, { name = it }, placeholder = "file.tar.gz")
            Spacer(Modifier.height(16.dp))
            PrimaryPillButton("开始下载", {
                if (url.isNotBlank() && name.isNotBlank()) onRemoteDownload(url.trim(), name.trim())
            }, height = 44.dp)
        }
    }
}

@Composable
private fun MoreAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Lucide.ChevronRight,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = AceTheme.colors
    val tint = when {
        !enabled -> colors.text3.copy(alpha = 0.5f)
        danger -> colors.error
        else -> colors.text1
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            // 长按菜单有 10 项，行高从 11dp 收到 8dp 才能不靠滚动就露出「删除」
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AceIcon(icon, tint = if (danger && enabled) colors.error else colors.text2, size = 17.dp)
        Column(Modifier.weight(1f)) {
            AceText(title, style = AceType.listPrimary, color = tint, maxLines = 1)
            AceText(subtitle, style = AceType.caption, color = colors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 长按 / 右侧按钮弹出的操作菜单，条目与 Web 控制台的文件右键菜单保持一致。 */
@Composable
private fun FileActionSheet(
    entry: FileEntry,
    clipboard: FileClipboard?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUncompress: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onPasteInto: () -> Unit,
    onRename: () -> Unit,
    onPermission: () -> Unit,
    onCopyPath: () -> Unit,
    onTerminal: () -> Unit,
    onProperties: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AceTheme.colors
    var confirmDelete by remember(entry.full) { mutableStateOf(false) }
    AceDialog(title = entry.name, onDismiss = onDismiss, scrollable = true) {
        AceText(entry.full, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        if (confirmDelete) {
            InlineNotice("删除后不可恢复，确认删除 ${entry.name}？", color = colors.error, background = colors.errorSoft, icon = Lucide.Trash2)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftPillButton("取消", { confirmDelete = false }, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
                PrimaryPillButton("确认删除", onDelete, modifier = Modifier.weight(1f), height = 44.dp)
            }
        } else {
            when {
                entry.dir -> MoreAction("打开", "进入 ${entry.name}", icon = Lucide.Folder, onClick = onOpen)
                isImageName(entry.name) -> MoreAction("预览", "查看图片", icon = Lucide.Eye, onClick = onOpen)
                isArchive(entry.name) -> {
                    MoreAction("查看内容", "按文本预览", icon = Lucide.Eye, onClick = onOpen)
                    MoreAction("解压", "解压到 ${parentOf(entry.full)}", icon = Lucide.Archive, onClick = onUncompress)
                }
                else -> MoreAction("查看 / 编辑", "在线编辑文本内容", icon = Lucide.Pencil, onClick = onOpen)
            }
            MoreAction("复制", "标记后到目标目录粘贴", icon = Lucide.Copy, onClick = onCopy)
            MoreAction("移动", "剪切到目标目录", icon = Lucide.Folder, onClick = onMove)
            if (clipboard != null && entry.dir) {
                MoreAction("粘贴到此处", "粘贴 ${clipboard.paths.size} 项到 ${entry.name}", icon = Lucide.Download, onClick = onPasteInto)
            }
            MoreAction("重命名", entry.name, icon = Lucide.Pencil, onClick = onRename)
            MoreAction(
                "权限",
                "${entry.mode.ifBlank { "—" }} · ${entry.owner.ifBlank { "—" }}:${entry.group.ifBlank { "—" }}",
                icon = Lucide.KeyRound,
                onClick = onPermission,
            )
            MoreAction("复制路径", entry.full, icon = Lucide.Link, onClick = onCopyPath)
            MoreAction(
                "终端",
                if (entry.dir) "在 ${entry.name} 打开面板终端" else "在 ${parentOf(entry.full)} 打开面板终端",
                icon = Lucide.Terminal,
                onClick = onTerminal,
            )
            MoreAction("属性", "大小 / 归属 / 时间", icon = Lucide.ListChecks, onClick = onProperties)
            MoreAction("加入多选", "勾选后可批量压缩、改权限", icon = Lucide.Square, onClick = onSelect)
            MoreAction("删除", "从服务器移除", icon = Lucide.Trash2, danger = true, onClick = { confirmDelete = true })
        }
    }
}

@Composable
private fun RenameDialog(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val colors = AceTheme.colors
    var name by remember(entry.full) { mutableStateOf(entry.name) }
    var error by remember(entry.full) { mutableStateOf<String?>(null) }
    AceDialog(title = "重命名", onDismiss = onDismiss) {
        AceText(parentOf(entry.full), style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        AceTextField(name, { name = it; error = null }, placeholder = "新的名称")
        Spacer(Modifier.height(8.dp))
        AceText(
            "新名称：${joinPath(parentOf(entry.full), name.ifBlank { "…" })}",
            style = AceType.caption,
            color = colors.text3,
            maxLines = 2,
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            InlineNotice(error!!, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton("保存", {
            val trimmed = name.trim()
            when {
                trimmed.isEmpty() -> error = "请输入新名称"
                trimmed.contains('/') -> error = "名称不能包含 /"
                trimmed == entry.name -> onDismiss()
                else -> onSubmit(trimmed)
            }
        }, height = 44.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionDialog(
    entries: List<FileEntry>,
    onDismiss: () -> Unit,
    onSubmit: (mode: String, owner: String, group: String) -> Unit,
) {    val colors = AceTheme.colors
    val first = entries.firstOrNull()
    // 面板的 mode 是 4 位八进制（如 0644），去掉前导 0 既保留 setuid 位又更贴近用户习惯
    var mode by remember(entries) {
        mutableStateOf((first?.mode ?: "").trimStart('0').ifBlank { if (first?.dir == true) "755" else "644" })
    }
    var owner by remember(entries) { mutableStateOf((first?.owner ?: "").ifBlank { "root" }) }
    var group by remember(entries) { mutableStateOf((first?.group ?: "").ifBlank { "root" }) }
    var error by remember(entries) { mutableStateOf<String?>(null) }
    val presets = listOf("755", "644", "700", "775", "600", "777")
    AceDialog(title = if (entries.size > 1) "批量设置权限" else "设置权限", onDismiss = onDismiss, scrollable = true) {
        AceText(
            if (entries.size > 1) "将应用到 ${entries.size} 个条目。" else (first?.full ?: ""),
            style = AceType.caption,
            color = colors.text3,
            maxLines = 2,
        )
        Spacer(Modifier.height(12.dp))
        AceText("权限（八进制）", style = AceType.caption, color = colors.text3)
        Spacer(Modifier.height(6.dp))
        AceTextField(mode, { mode = it.filter { ch -> ch.isDigit() }.take(4); error = null }, placeholder = "755")
        Spacer(Modifier.height(8.dp))
        // 6 个预设在一屏宽度里放不下，换行而不是横向裁切
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { preset ->
                SoftPillButton(
                    preset,
                    { mode = preset },
                    height = 32.dp,
                    tint = if (mode == preset) colors.accent else colors.text2,
                    background = if (mode == preset) colors.accentSoft else colors.fill,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                AceText("所有者", style = AceType.caption, color = colors.text3)
                Spacer(Modifier.height(6.dp))
                AceTextField(owner, { owner = it.trim() }, placeholder = "root")
            }
            Column(Modifier.weight(1f)) {
                AceText("用户组", style = AceType.caption, color = colors.text3)
                Spacer(Modifier.height(6.dp))
                AceTextField(group, { group = it.trim() }, placeholder = "root")
            }
        }
        if (first != null && first.modeStr.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            AceText("当前：${first.modeStr}", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            InlineNotice(error!!, color = colors.error, background = colors.errorSoft)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryPillButton("应用", {
            val normalized = mode.ifBlank { "" }
            when {
                normalized.length !in 3..4 || normalized.any { !it.isDigit() } -> error = "权限需要 3-4 位八进制数字"
                owner.isBlank() || group.isBlank() -> error = "所有者与用户组不能为空"
                else -> onSubmit(normalized, owner, group)
            }
        }, height = 44.dp)
    }
}

@Composable
private fun PropertiesDialog(
    store: AceStore,
    entry: FileEntry,
    onDismiss: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    var detail by remember(entry.full) { mutableStateOf(entry) }
    var size by remember(entry.full) { mutableStateOf(entry.size) }
    var loadingSize by remember(entry.full) { mutableStateOf(entry.dir) }
    var error by remember(entry.full) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.full) {
        runSafely { store.api?.fileInfo(entry.full) }.onSuccess { info -> detail = info ?: detail }
        runSafely { store.api?.fileSize(entry.full) }.onSuccess { value ->
            if (!value.isNullOrBlank()) size = value
            loadingSize = false
        }.onFailure {
            loadingSize = false
            error = store.friendlyError(it)
        }
    }
    AceDialog(title = "属性", onDismiss = onDismiss, scrollable = true) {
        PropertyRow("名称", detail.name)
        PropertyRow("路径", detail.full)
        PropertyRow("类型", when {
            detail.dir -> "目录"
            detail.symlink -> "符号链接"
            else -> "文件"
        })
        PropertyRow("大小", if (loadingSize) "计算中…" else size.ifBlank { "—" })
        PropertyRow("权限", "${detail.mode}  ${detail.modeStr.ifBlank { "—" }}")
        PropertyRow("归属", "${detail.owner}:${detail.group}")
        PropertyRow("修改时间", detail.modify.ifBlank { "—" })
        if (detail.symlink && detail.link.isNotBlank()) PropertyRow("指向", detail.link)
        if (detail.immutable) PropertyRow("防篡改", "已开启")
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            InlineNotice(error!!, color = colors.warning, background = colors.warningSoft)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("复制路径", onCopyPath, modifier = Modifier.weight(1f), height = 44.dp, background = colors.fill)
            PrimaryPillButton("关闭", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    val colors = AceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AceText(label, style = AceType.caption, color = colors.text3, modifier = Modifier.width(66.dp))
        AceText(
            value.ifBlank { "—" },
            style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.text1,
            modifier = Modifier.weight(1f),
        )
    }
}
