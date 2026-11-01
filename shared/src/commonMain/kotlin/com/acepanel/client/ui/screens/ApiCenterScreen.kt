package com.acepanel.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.AceApi
import com.acepanel.client.data.AceStore
import com.acepanel.client.data.ApiCatalog
import com.acepanel.client.data.ApiField
import com.acepanel.client.data.ApiJson
import com.acepanel.client.data.ApiRawResult
import com.acepanel.client.data.ApiRoute
import com.acepanel.client.data.SessionExpiredException
import com.acepanel.client.ui.components.AceCard
import com.acepanel.client.ui.components.AceIcon
import com.acepanel.client.ui.components.AceSwitch
import com.acepanel.client.ui.components.AceText
import com.acepanel.client.ui.components.AceTextField
import com.acepanel.client.ui.components.EmptyState
import com.acepanel.client.ui.components.MobilePageHeader
import com.acepanel.client.ui.components.rememberScreenLazyListState
import com.acepanel.client.ui.components.PrimaryPillButton
import com.acepanel.client.ui.components.SoftPillButton
import com.acepanel.client.ui.components.SoftTag
import com.acepanel.client.ui.components.StatusPill
import com.acepanel.client.ui.theme.AceColors
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.acepanel.client.ui.theme.LocalDesktopLayout
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Search
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.acepanel.client.data.runSafely

private val PrettyJson = Json { prettyPrint = true }

private fun prettyJson(text: String): String {
    if (text.isBlank()) return text
    return runSafely {
        PrettyJson.encodeToString(ApiJson.parseToJsonElement(text))
    }.getOrDefault(text)
}

private fun fieldHint(field: ApiField): String = when {
    field.options.isNotEmpty() -> "可选值：" + field.options.joinToString(" / ")
    field.type == "number" -> "数字"
    field.type == "boolean" -> "true / false"
    field.type == "array" -> "逗号分隔，或输入 JSON 数组"
    field.type == "object" -> "输入 JSON 对象"
    else -> "文本"
}

@Composable
fun ApiCenterScreen(store: AceStore) {
    var selected by remember { mutableStateOf<ApiRoute?>(null) }
    val route = selected
    if (route == null) {
        ApiRouteList(store) { selected = it }
    } else {
        ApiRouteDetail(store, route) { selected = null }
    }
}

@Composable
private fun ApiRouteList(store: AceStore, onOpen: (ApiRoute) -> Unit) {
    val colors = AceTheme.colors
    var query by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("全部") }
    var tag by remember { mutableStateOf("全部") }
    val tags = remember {
        buildList {
            add("全部")
            ApiCatalog.routes.forEach { route ->
                route.tags.forEach { value -> if (value !in this) add(value) }
            }
        }
    }
    val filtered = remember(query, method, tag) {
        ApiCatalog.routes.filter { route ->
            (method == "全部" || route.method == method) &&
                (tag == "全部" || tag in route.tags) &&
                (
                    query.isBlank() ||
                        route.path.contains(query, true) ||
                        route.summary.contains(query, true) ||
                        route.tags.any { it.contains(query, true) }
                    )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = "接口中心",
            subtitle = "${filtered.size} / ${ApiCatalog.routes.size} 个接口 · 面板路由表在线调试",
            onBack = store::closePage,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
        ) {
            AceTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索：路径 / 名称 / 模块",
                height = 46.dp,
                textStyle = AceType.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("全部", "GET", "POST", "PUT", "DELETE").forEach { value ->
                    FilterChip(value, method == value) { method = value }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { value ->
                    FilterChip(value, tag == value, subtle = true) { tag = value }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = rememberScreenLazyListState(),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.method + it.path }) { route ->
                    ApiRouteRow(route) { onOpen(route) }
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            title = "没有匹配的接口",
                            subtitle = "换个关键词、请求方法或模块试试",
                            icon = Lucide.Braces,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, subtle: Boolean = false, onClick: () -> Unit) {
    val colors = AceTheme.colors
    val background = when {
        selected && !subtle -> colors.text1
        selected -> colors.accentSoft
        else -> colors.fill
    }
    val foreground = when {
        selected && !subtle -> colors.surface
        selected -> colors.accent
        else -> colors.text2
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        AceText(label, style = AceType.tag, color = foreground, maxLines = 1)
    }
}

@Composable
private fun ApiRouteRow(route: ApiRoute, onClick: () -> Unit) {
    val colors = AceTheme.colors
    AceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MethodBadge(route.method)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AceText(
                    route.path,
                    style = AceType.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text1,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (route.summary.isNotBlank()) {
                        AceText(route.summary, style = AceType.caption, color = colors.text3, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (route.multipart) {
                        AceText("文件", style = AceType.caption, color = colors.warning)
                    } else if (route.websocket) {
                        AceText("WS", style = AceType.caption, color = colors.accent)
                    } else if (route.tags.isNotEmpty()) {
                        AceText(
                            route.tags.first(),
                            style = AceType.caption,
                            color = colors.text3,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 112.dp),
                        )
                    }
                }
            }
            AceIcon(Lucide.ChevronRight, tint = colors.text3, size = 16.dp)
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = methodColor(method)
    val colors = AceTheme.colors
    Box(
        modifier = Modifier
            .widthIn(min = 58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AceText(
            method,
            style = AceType.tag.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun methodColor(method: String): Color {
    val colors = AceTheme.colors
    return when (method) {
        "GET" -> colors.success
        "POST" -> colors.accent
        "PUT", "PATCH" -> colors.warning
        "DELETE" -> colors.error
        else -> colors.text2
    }
}

private class ApiCallState(route: ApiRoute) {
    val pathValues = mutableStateMapOf<String, String>()
    val queryValues = mutableStateMapOf<String, String>()
    val bodyValues = mutableStateMapOf<String, String>()
    var rawMode by mutableStateOf(false)
    var rawBody by mutableStateOf("")
    var result by mutableStateOf<ApiRawResult?>(null)
    var error by mutableStateOf<String?>(null)
    var running by mutableStateOf(false)

    init {
        route.queryFields.forEach { field ->
            queryValues[field.name] = when {
                field.type == "boolean" -> "false"
                field.name == "page" -> "1"
                field.name == "limit" -> "20"
                else -> ""
            }
        }
        route.bodyFields.filter { it.type == "boolean" }.forEach { bodyValues[it.name] = "" }
    }
}

@Composable
private fun ApiRouteDetail(store: AceStore, route: ApiRoute, onBack: () -> Unit) {
    val colors = AceTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val desktop = LocalDesktopLayout.current
    val state = remember(route) { ApiCallState(route) }
    val blocked = route.multipart || route.websocket

    fun runRequest() {
        if (state.running || blocked) return
        val client = store.api
        state.error = null
        if (client == null) {
            state.error = "尚未登录面板"
            return
        }
        scope.launch {
            state.running = true
            state.result = null
            try {
                var requestPath = route.path
                route.pathFields.forEach { name ->
                    val value = state.pathValues[name]?.trim().orEmpty()
                    if (value.isEmpty()) throw IllegalArgumentException("请填写路径参数 {$name}")
                    requestPath = requestPath.replace("{$name}", value.encodeURLPathPart())
                }
                val params = buildMap {
                    route.queryFields.forEach { field ->
                        val value = state.queryValues[field.name]?.trim().orEmpty()
                        if (value.isNotEmpty()) put(field.name, value)
                    }
                }
                route.queryFields.firstOrNull { it.required && state.queryValues[it.name].isNullOrBlank() }?.let {
                    throw IllegalArgumentException("请填写必填查询参数 ${it.name}")
                }
                val body: JsonElement? = when {
                    route.bodyFields.isEmpty() -> null
                    state.rawMode -> state.rawBody.trim().takeIf { it.isNotEmpty() }?.let {
                        runSafely { ApiJson.parseToJsonElement(it) }
                            .getOrElse { throw IllegalArgumentException("请求体 JSON 格式不正确") }
                    }
                    else -> {
                        route.bodyFields.firstOrNull {
                            it.required && state.bodyValues[it.name].isNullOrBlank()
                        }?.let { throw IllegalArgumentException("请填写必填参数 ${it.name}") }
                        buildBody(route, state.bodyValues)
                    }
                }
                state.result = client.callRaw(route.method, requestPath, params, body)
            } catch (t: Throwable) {
                state.error = if (t is IllegalArgumentException) {
                    t.message?.takeIf { it.isNotBlank() } ?: "参数不正确"
                } else {
                    store.friendlyError(t)
                }
                if (t is SessionExpiredException) store.handleSessionExpired()
            } finally {
                state.running = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        MobilePageHeader(
            title = route.summary.ifBlank { "接口调试" },
            subtitle = "${route.method} ${route.path}",
            onBack = onBack,
        )
        if (desktop) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RouteInfoCard(route)
                    RequestForm(route, state, colors)
                    Spacer(Modifier.height(4.dp))
                    RunButton(route, state, blocked, colors, ::runRequest)
                    Spacer(Modifier.height(24.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ResponseCard(route, state, colors) {
                        clipboard.setText(AnnotatedString(it))
                        store.toast("响应已复制")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RouteInfoCard(route)
                RequestForm(route, state, colors)
                Spacer(Modifier.height(4.dp))
                RunButton(route, state, blocked, colors, ::runRequest)
                ResponseCard(route, state, colors) {
                    clipboard.setText(AnnotatedString(it))
                    store.toast("响应已复制")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RouteInfoCard(route: ApiRoute) {
    val colors = AceTheme.colors
    AceCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MethodBadge(route.method)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AceText(route.summary.ifBlank { "未命名接口" }, style = AceType.listStrong, color = colors.text1)
                AceText(
                    route.path,
                    style = AceType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = colors.text3,
                )
            }
        }
        if (route.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                route.tags.forEach { SoftTag(it) }
            }
        }
        if (route.multipart) {
            Spacer(Modifier.height(12.dp))
            WarningNote("此接口需要 multipart 文件上传，请到对应的业务页面（如文件 / 备份 / 导入）操作。")
        }
        if (route.websocket) {
            Spacer(Modifier.height(12.dp))
            WarningNote("此接口是 WebSocket 长连接，请在终端、SSH 或对应监控页面使用。")
        }
    }
}

@Composable
private fun WarningNote(text: String) {
    val colors = AceTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.warningSoft)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AceIcon(Lucide.CircleAlert, tint = colors.warning, size = 16.dp)
        AceText(text, style = AceType.caption, color = colors.warning, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RequestForm(route: ApiRoute, state: ApiCallState, colors: AceColors) {
    if (route.pathFields.isNotEmpty()) {
        AceCard(padding = 16.dp) {
            AceText("路径参数", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
            Spacer(Modifier.height(10.dp))
            route.pathFields.forEachIndexed { index, name ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                AceText("{${name}}", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                Spacer(Modifier.height(5.dp))
                AceTextField(
                    value = state.pathValues[name].orEmpty(),
                    onValueChange = { state.pathValues[name] = it },
                    placeholder = "路径参数 $name",
                    height = 42.dp,
                    textStyle = AceType.bodySmall,
                )
            }
        }
    }
    if (route.queryFields.isNotEmpty()) {
        FieldsCard("查询参数", route.queryFields, state.queryValues, colors)
    }
    if (route.bodyFields.isNotEmpty()) {
        AceCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AceText("请求体", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
                    AceText("JSON", style = AceType.caption, color = colors.text3)
                }
                AceText("直接编辑 JSON", style = AceType.caption, color = colors.text2)
                Spacer(Modifier.width(8.dp))
                AceSwitch(state.rawMode) { enabled ->
                    state.rawMode = enabled
                    if (enabled) {
                        state.rawBody = runSafely {
                            buildBody(route, state.bodyValues).toString()
                        }.getOrDefault("")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.rawMode) {
                RawJsonEditor(state.rawBody, { state.rawBody = it }, colors)
            } else {
                route.bodyFields.forEachIndexed { index, field ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    FieldEditor(field, state.bodyValues, colors)
                }
            }
        }
    }
    if (route.pathFields.isEmpty() && route.queryFields.isEmpty() && route.bodyFields.isEmpty()) {
        AceCard(padding = 16.dp) {
            AceText("此接口无需参数", style = AceType.bodySmall, color = colors.text3)
        }
    }
}

@Composable
private fun FieldsCard(title: String, fields: List<ApiField>, values: MutableMap<String, String>, colors: AceColors) {
    AceCard(padding = 16.dp) {
        AceText(title, style = AceType.section.copy(fontSize = 14.sp), color = colors.text1)
        Spacer(Modifier.height(12.dp))
        fields.forEachIndexed { index, field ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            FieldEditor(field, values, colors)
        }
    }
}

@Composable
private fun FieldEditor(field: ApiField, values: MutableMap<String, String>, colors: AceColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 参数名不限宽时（例如 enable_ssl_verification）会把后面的类型说明和开关一起挤出去
        AceText(
            field.name,
            style = AceType.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = colors.text1,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        AceText(
            buildString {
                append(field.type)
                if (field.required) append(" · 必填")
            },
            style = AceType.caption,
            color = if (field.required) colors.accent else colors.text3,
            modifier = Modifier.weight(1f),
        )
        if (field.type == "boolean") {
            AceSwitch(values[field.name] == "true") { checked ->
                values[field.name] = if (checked) "true" else "false"
            }
        }
    }
    if (field.type != "boolean") {
        Spacer(Modifier.height(6.dp))
        AceTextField(
            value = values[field.name].orEmpty(),
            onValueChange = { values[field.name] = it },
            placeholder = fieldHint(field),
            height = 42.dp,
            textStyle = AceType.bodySmall,
        )
        if (field.options.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                field.options.forEach { option ->
                    val selected = values[field.name] == option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) colors.accentSoft else colors.fill)
                            .clickable { values[field.name] = option }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        AceText(
                            option,
                            style = AceType.tag.copy(fontFamily = FontFamily.Monospace),
                            color = if (selected) colors.accent else colors.text2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RawJsonEditor(value: String, onChange: (String) -> Unit, colors: AceColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.terminal)
            .padding(12.dp),
    ) {
        if (value.isEmpty()) {
            AceText("{ }", style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.terminalDim)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 116.dp),
            textStyle = AceType.caption.copy(fontFamily = FontFamily.Monospace, color = colors.terminalText, fontSize = 12.sp),
            cursorBrush = SolidColor(colors.terminalGreen),
        )
    }
}

@Composable
private fun RunButton(
    route: ApiRoute,
    state: ApiCallState,
    blocked: Boolean,
    colors: AceColors,
    onRun: () -> Unit,
) {
    if (blocked) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(colors.fill)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AceText(
                if (route.websocket) "WebSocket 接口请使用对应功能页" else "文件上传接口请使用对应功能页",
                style = AceType.caption,
                color = colors.text3,
            )
        }
    } else {
        PrimaryPillButton(
            text = if (state.running) "请求中…" else "发送请求",
            onClick = onRun,
            enabled = !state.running,
            loading = state.running,
            height = 44.dp,
            leadingIcon = Lucide.Play,
        )
    }
}

@Composable
private fun ResponseCard(route: ApiRoute, state: ApiCallState, colors: AceColors, onCopy: (String) -> Unit) {
    val result = state.result
    AceCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AceText("响应", style = AceType.section.copy(fontSize = 14.sp), color = colors.text1, modifier = Modifier.weight(1f))
            if (result != null) {
                val statusColor = when {
                    result.status in 200..299 -> colors.success
                    result.status in 400..499 -> colors.warning
                    else -> colors.error
                }
                StatusPill(
                    "${result.status}",
                    statusColor,
                    if (result.status in 200..299) colors.successSoft else if (result.status in 400..499) colors.warningSoft else colors.errorSoft,
                    dot = false,
                )
                AceText("${result.durationMs} ms", style = AceType.caption, color = colors.text3)
                SoftPillButton(
                    "复制",
                    onClick = { onCopy(result.body) },
                    height = 30.dp,
                    leadingIcon = Lucide.Copy,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.errorSoft)
                        .padding(12.dp),
                ) {
                    AceText(state.error!!, style = AceType.bodySmall, color = colors.error)
                }
            }
            result == null -> {
                AceText(
                    "填写参数后发送请求，这里会显示状态码、耗时和原始响应。",
                    style = AceType.caption,
                    color = colors.text3,
                )
            }
            else -> {
                val formatted = prettyJson(result.body).take(60000)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 460.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.terminal)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        AceText(
                            formatted.ifBlank { "<空响应>" },
                            style = AceType.caption.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            ),
                            color = colors.terminalText,
                        )
                        if (result.body.length > formatted.length) {
                            Spacer(Modifier.height(8.dp))
                            AceText("响应过长，仅展示前 60000 个字符，可使用复制按钮获取完整内容。", style = AceType.caption, color = colors.terminalDim)
                        }
                    }
                }
            }
        }
    }
}

private fun buildBody(route: ApiRoute, values: Map<String, String>): JsonObject = buildJsonObject {
    route.bodyFields.forEach { field ->
        val raw = values[field.name]?.trim().orEmpty()
        if (raw.isEmpty()) return@forEach
        when (field.type) {
            "number" -> {
                val number = raw.toLongOrNull() ?: raw.toDoubleOrNull()
                if (number == null) throw IllegalArgumentException("${field.name} 需要数字")
                if (number is Long) put(field.name, number) else put(field.name, number as Double)
            }
            "boolean" -> {
                val value = raw.toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("${field.name} 需要 true / false")
                put(field.name, value)
            }
            "array" -> {
                val element = if (raw.startsWith("[")) {
                    runSafely { ApiJson.parseToJsonElement(raw) }
                        .getOrElse { throw IllegalArgumentException("${field.name} 的 JSON 数组格式不正确") }
                } else {
                    JsonArray(
                        raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                            .map { value ->
                                val number = value.toLongOrNull()
                                if (number != null) JsonPrimitive(number) else JsonPrimitive(value)
                            },
                    )
                }
                put(field.name, element)
            }
            "object" -> {
                val element = runSafely { ApiJson.parseToJsonElement(raw) }
                    .getOrElse { throw IllegalArgumentException("${field.name} 需要 JSON 对象") }
                if (element !is JsonObject) throw IllegalArgumentException("${field.name} 需要 JSON 对象")
                put(field.name, element)
            }
            else -> put(field.name, raw)
        }
    }
}
