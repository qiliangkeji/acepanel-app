package com.acepanel.client.data

import com.acepanel.client.platform.createAceHttpClient
import com.acepanel.client.platform.currentTimeMillis
import com.acepanel.client.platform.rsaEncryptSha512
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.timeout
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

class ApiException(val statusCode: Int, message: String) : Exception(message)
class SessionExpiredException(message: String = "登录状态已过期，请重新登录") : Exception(message)

data class ApiRawResult(val status: Int, val body: String, val durationMs: Long)

val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

inline fun <reified T> JsonElement.decode(): T = ApiJson.decodeFromJsonElement<T>(this)

inline fun <reified T> JsonElement?.decodeOrNull(): T? = this?.let {
    runCatching { ApiJson.decodeFromJsonElement<T>(it) }.getOrNull()
}

class AceApi(
    baseUrl: String,
    initialCookies: Map<String, String> = emptyMap(),
    private val onCookiesChanged: (Map<String, String>) -> Unit = {},
    private val onSessionExpired: (() -> Unit)? = null,
    private val entrance: String = "",
    private val userAgent: String = "",
) {
    val baseUrl: String = baseUrl.trimEnd('/')
    private val entrancePath: String = run {
        val raw = entrance.trim().trimEnd('/')
        when {
            raw.isEmpty() || raw == "/" -> ""
            raw.startsWith("/") -> raw
            else -> "/$raw"
        }
    }
    private val client: HttpClient = createAceHttpClient()

    /**
     * 请求现在会真正跑在 IO 线程池上，多个响应可能同时写 Cookie。
     * 这里用「整体替换引用」的写时复制方式，避免并发修改同一个 LinkedHashMap。
     */
    @Volatile
    private var cookies: Map<String, String> = initialCookies.toMap()

    val cookieSnapshot: Map<String, String> get() = cookies
    val isHttps: Boolean get() = baseUrl.startsWith("https://", ignoreCase = true)

    private fun cookieHeader(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun collectCookies(response: io.ktor.client.statement.HttpResponse) {
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie)
        if (setCookies.isNullOrEmpty()) return
        var next: MutableMap<String, String>? = null
        setCookies.forEach { raw ->
            val first = raw.substringBefore(';').trim()
            val idx = first.indexOf('=')
            if (idx <= 0) return@forEach
            val name = first.substring(0, idx).trim()
            val value = first.substring(idx + 1).trim()
            val expired = value.isEmpty() || raw.contains("Max-Age=0", ignoreCase = true) ||
                raw.contains("expires=Thu, 01 Jan 1970", ignoreCase = true)
            val box = next ?: cookies.toMutableMap().also { next = it }
            if (expired) {
                box.remove(name)
            } else if (box[name] != value) {
                box[name] = value
            }
        }
        next?.let {
            cookies = it.toMap()
            onCookiesChanged(cookies)
        }
    }

    @PublishedApi
    internal suspend fun rawRequest(
        method: HttpMethod,
        path: String,
        params: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
    ): String = withContext(Dispatchers.IO) {
        suspend fun execute(): io.ktor.client.statement.HttpResponse = client.request("$baseUrl$path") {
            this.method = method
            params.forEach { (key, value) -> if (value != null) parameter(key, value) }
            header(HttpHeaders.Accept, "application/json")
            if (userAgent.isNotBlank()) header(HttpHeaders.UserAgent, userAgent)
            val cookie = cookieHeader()
            if (cookie.isNotEmpty()) header(HttpHeaders.Cookie, cookie)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val response = try {
            execute()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (method == HttpMethod.Get) {
                delay(350)
                execute()
            } else {
                throw t
            }
        }
        collectCookies(response)
        val text = response.bodyAsText()
        if (response.status.value == 401) {
            onSessionExpired?.invoke()
            throw SessionExpiredException(parseError(text).ifBlank { "登录状态已过期，请重新登录" })
        }
        if (!response.status.isSuccess()) {
            val message = when (response.status.value) {
                418 -> "面板安全入口校验失败（HTTP 418）。请在地址中带上安全入口，或检查绑定域名 / IP / UA 设置。"
                else -> parseError(text).take(400).ifBlank { "请求失败（${response.status.value}）" }
            }
            throw ApiException(response.status.value, message)
        }
        text
    }

    /**
     * AcePanel 的安全入口需要先访问入口地址，为当前会话写入 verify_entrance 标记。
     * 若入口路径错误或绑定域名/IP/UA 不匹配，面板会返回 HTTP 418 HTML。
     */
    suspend fun visitEntrance() {
        if (entrancePath.isEmpty()) return
        withContext(Dispatchers.IO) {
            val response = client.request("$baseUrl$entrancePath") {
                method = HttpMethod.Get
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                if (userAgent.isNotBlank()) header(HttpHeaders.UserAgent, userAgent)
                val cookie = cookieHeader()
                if (cookie.isNotEmpty()) header(HttpHeaders.Cookie, cookie)
            }
            collectCookies(response)
            val status = response.status.value
            runCatching { response.bodyAsText() }
            if (status == 418 || status == 404 || status == 401) {
                throw ApiException(
                    status,
                    "安全入口校验失败：请确认地址包含正确的入口路径（例如 /0a2vc7），且当前网络 IP 与被绑定的域名 / UA 符合面板设置。",
                )
            }
            if (status >= 500) {
                throw ApiException(status, "面板暂时不可用（HTTP $status）")
            }
        }
    }

    private fun parseError(text: String): String = runCatching {
        val obj = ApiJson.parseToJsonElement(text) as? JsonObject ?: return@runCatching text
        obj["msg"]?.jsonPrimitive?.content ?: text
    }.getOrDefault(text)

    @PublishedApi
    internal fun checkMsg(msg: String?) {
        val text = msg ?: ""
        if (text.isNotEmpty() && text != "success" && !text.equals("ok", true)) {
            throw ApiException(400, text)
        }
    }

    /** 解析成通用 JsonElement 树，仅接口中心等需要原样查看响应的地方使用。 */
    private suspend fun unwrap(text: String): JsonElement? {
        if (text.isBlank()) return null
        return withContext(Dispatchers.Default) {
            val env = runCatching { ApiJson.decodeFromString<Envelope>(text) }.getOrNull()
                ?: throw ApiException(500, "无法解析服务器响应")
            checkMsg(env.msg)
            env.data
        }
    }

    /**
     * 直接从响应文本流式解码成目标类型：不建 JsonElement 树，也不做 toString() 二次序列化。
     * 解析在 [Dispatchers.Default] 上执行，UI 线程不会被大响应卡住。
     */
    internal suspend inline fun <reified T> requestTyped(
        method: HttpMethod,
        path: String,
        params: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
    ): T? {
        val text = rawRequest(method, path, params, body)
        if (text.isBlank()) return null
        return withContext(Dispatchers.Default) {
            val env = try {
                ApiJson.decodeFromString<TypedEnvelope<T>>(text)
            } catch (serialization: SerializationException) {
                throw ApiException(500, "服务器返回的数据格式无法解析：${serialization.message?.take(120)}")
            }
            checkMsg(env.msg)
            env.data
        }
    }

    internal suspend inline fun <reified T> getTyped(
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): T? = requestTyped<T>(HttpMethod.Get, path, params, null)

    internal suspend inline fun <reified T> postTyped(
        path: String,
        body: JsonElement? = null,
    ): T? = requestTyped<T>(HttpMethod.Post, path, emptyMap(), body)

    internal suspend inline fun <reified T> putTyped(
        path: String,
        body: JsonElement? = null,
    ): T? = requestTyped<T>(HttpMethod.Put, path, emptyMap(), body)

    internal suspend inline fun <reified T> deleteTyped(
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): T? = requestTyped<T>(HttpMethod.Delete, path, params, null)

    suspend fun getData(path: String, params: Map<String, String?> = emptyMap()): JsonElement? =
        unwrap(rawRequest(HttpMethod.Get, path, params))

    suspend fun postData(path: String, body: JsonElement? = null): JsonElement? =
        unwrap(rawRequest(HttpMethod.Post, path, body = body))

    suspend fun putData(path: String, body: JsonElement? = null): JsonElement? =
        unwrap(rawRequest(HttpMethod.Put, path, body = body))

    suspend fun deleteData(path: String, params: Map<String, String?> = emptyMap()): JsonElement? =
        unwrap(rawRequest(HttpMethod.Delete, path, params))

    suspend fun deleteWithBody(path: String, body: JsonElement): JsonElement? =
        unwrap(rawRequest(HttpMethod.Delete, path, body = body))

    /**
     * 接口中心使用的原样调用：不解析统一响应信封，也不因 HTTP 错误码抛异常，
     * 便于在 App 内查看面板返回的真实状态码与响应体。
     */
    suspend fun callRaw(
        method: String,
        path: String,
        params: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
    ): ApiRawResult {
        val httpMethod = when (method.uppercase()) {
            "GET" -> HttpMethod.Get
            "POST" -> HttpMethod.Post
            "PUT" -> HttpMethod.Put
            "PATCH" -> HttpMethod.Patch
            "DELETE" -> HttpMethod.Delete
            else -> throw ApiException(400, "不支持的请求方法：$method")
        }
        val started = currentTimeMillis()
        val response = client.request("$baseUrl$path") {
            this.method = httpMethod
            params.forEach { (key, value) -> if (value != null) parameter(key, value) }
            header(HttpHeaders.Accept, "application/json")
            if (userAgent.isNotBlank()) header(HttpHeaders.UserAgent, userAgent)
            if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookieHeader())
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        collectCookies(response)
        val text = response.bodyAsText()
        if (response.status.value == 401) onSessionExpired?.invoke()
        return ApiRawResult(response.status.value, text, currentTimeMillis() - started)
    }

    // ---------- Auth ----------

    suspend fun fetchPublicKey(): String = getData("/api/user/key")?.let {
        if (it is JsonPrimitive) it.content else it.toString()
    } ?: error("服务器未返回登录公钥")

    suspend fun getCaptcha(): CaptchaInfo = getTyped<CaptchaInfo>("/api/user/captcha") ?: CaptchaInfo()

    suspend fun isLogin(): Boolean = getData("/api/user/is_login")?.let {
        (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
    } ?: false

    suspend fun isTwoFa(username: String): Boolean = getData(
        "/api/user/is_2fa",
        mapOf("username" to username),
    )?.let { (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false } ?: false

    suspend fun login(
        username: String,
        password: String,
        passCode: String = "",
        captchaCode: String = "",
        safeLogin: Boolean = false,
    ): UserInfo {
        visitEntrance()
        val publicKey = fetchPublicKey()
        val encryptedUsername = rsaEncryptSha512(username, publicKey)
        val encryptedPassword = rsaEncryptSha512(password, publicKey)
        val body = buildJsonObject {
            put("username", encryptedUsername)
            put("password", encryptedPassword)
            put("pass_code", passCode)
            put("safe_login", safeLogin)
            put("captcha_code", captchaCode)
        }
        postData("/api/user/login", body)
        return userInfo()
    }

    suspend fun userInfo(): UserInfo = getTyped<UserInfo>("/api/user/info") ?: UserInfo()

    suspend fun logout() {
        runCatching { postData("/api/user/logout") }
        cookies = emptyMap()
        onCookiesChanged(emptyMap())
    }

    // ---------- Home ----------

    suspend fun panelInfo(): PanelInfo = getTyped<PanelInfo>("/api/home/panel") ?: PanelInfo()

    suspend fun systemInfo(): SystemInfo = getTyped<SystemInfo>("/api/home/system_info") ?: SystemInfo()

    suspend fun countInfo(): CountInfo = getTyped<CountInfo>("/api/home/count_info") ?: CountInfo()

    suspend fun currentInfo(nets: List<String> = emptyList(), disks: List<String> = emptyList()): CurrentInfo {
        val body = buildJsonObject {
            put("nets", JsonArray(nets.map { JsonPrimitive(it) }))
            put("disks", JsonArray(disks.map { JsonPrimitive(it) }))
        }
        return postTyped<CurrentInfo>("/api/home/current", body) ?: CurrentInfo()
    }

    suspend fun homeApps(): List<HomeApp> =
        getTyped<List<HomeApp>>("/api/home/apps")
            ?.map { app -> app.copy(installed = true, installedVersion = app.version.ifBlank { app.installedVersion }) }
            ?: emptyList()

    suspend fun installedEnvironment(): InstalledEnvironment =
        getTyped<InstalledEnvironment>("/api/home/installed_environment") ?: InstalledEnvironment()

    suspend fun checkUpdate(): JsonElement? = getData("/api/home/check_update")

    suspend fun restartPanel() {
        postData("/api/home/restart")
    }

    // ---------- Monitor ----------

    /**
     * 监控曲线。7 天区间约 1 万条记录，必须走流式类型化解码（见 [requestTyped]），
     * 且 [MonitorDetail] 刻意不解析 top_processes，否则 UI 线程会被拖到 ANR。
     */
    suspend fun monitorList(startMs: Long, endMs: Long): MonitorDetail =
        getTyped<MonitorDetail>(
            "/api/monitor/list",
            mapOf("start" to startMs.toString(), "end" to endMs.toString()),
        ) ?: MonitorDetail()

    suspend fun monitorSetting(): JsonObject? = getData("/api/monitor/setting").obj()

    suspend fun updateMonitorSetting(enabled: Boolean, days: Int, interval: Int, alertDays: Int) {
        postData(
            "/api/monitor/setting",
            buildJsonObject {
                put("enabled", enabled)
                put("days", days)
                put("interval", interval)
                put("alert_days", alertDays)
            },
        )
    }

    suspend fun clearMonitorData() {
        postData("/api/monitor/clear")
    }

    suspend fun alerts(page: Int = 1, limit: Int = 20): Page<Alert> =
        getTyped<Page<Alert>>("/api/alert/record", mapOf("page" to page.toString(), "limit" to limit.toString()))
            ?: Page()

    // ---------- SSH ----------

    suspend fun sshHosts(page: Int = 1, limit: Int = 100): Page<SshHost> =
        getTyped<Page<SshHost>>("/api/ssh", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun createSshHost(body: JsonObject): JsonElement? = postData("/api/ssh", body)

    suspend fun deleteSshHost(id: Long) {
        deleteData("/api/ssh/$id")
    }

    // ---------- Apps ----------

    suspend fun appCategories(): List<Lv> =
        getTyped<List<Lv>>("/api/app/categories") ?: emptyList()

    suspend fun appList(category: String? = null, query: String? = null, installedOnly: Boolean = false): Page<HomeApp> {
        val params = buildMap {
            put("page", "1")
            put("limit", "200")
            if (!category.isNullOrBlank()) put("category", category)
            if (!query.isNullOrBlank()) put("query", query)
            if (installedOnly) put("installed", "true")
        }
        return getTyped<Page<HomeApp>>("/api/app/list", params) ?: Page()
    }

    suspend fun appInstall(slug: String, channel: String) {
        postData(
            "/api/app/install",
            buildJsonObject {
                put("slug", slug)
                put("channel", channel)
            },
        )
    }

    suspend fun appUninstall(slug: String) {
        postData("/api/app/uninstall", buildJsonObject { put("slug", slug) })
    }

    suspend fun appUpdate(slug: String) {
        postData("/api/app/update", buildJsonObject { put("slug", slug) })
    }

    // ---------- Websites ----------

    suspend fun websites(typ: String? = null, page: Int = 1, limit: Int = 200): Page<Website> {
        // /api/website 的 WebsiteList.type 是必填项（all/proxy/static/php），省略会返回 422。
        val params = buildMap {
            put("type", typ?.takeIf { it.isNotBlank() } ?: "all")
            put("page", page.toString())
            put("limit", limit.toString())
        }
        return getTyped<Page<Website>>("/api/website", params) ?: Page()
    }

    suspend fun websiteStatus(id: Long, status: Boolean) {
        postData(
            "/api/website/$id/status",
            buildJsonObject {
                put("id", id)
                put("status", status)
            },
        )
    }

    suspend fun deleteWebsite(id: Long, deletePath: Boolean = false, deleteDb: Boolean = false) {
        deleteData(
            "/api/website/$id",
            mapOf("path" to deletePath.toString(), "db" to deleteDb.toString()),
        )
    }

    suspend fun createWebsite(
        name: String,
        domains: List<String>,
        type: String,
        path: String = "",
        php: Int = 0,
        proxy: String = "",
        listens: List<String> = listOf("80"),
        remark: String = "",
    ): JsonElement? {
        return postData(
            "/api/website",
            buildJsonObject {
                put("name", name)
                put("type", type)
                put("domains", kotlinx.serialization.json.JsonArray(domains.map { JsonPrimitive(it) }))
                put("listens", kotlinx.serialization.json.JsonArray(listens.map { JsonPrimitive(it) }))
                if (path.isNotBlank()) put("path", path)
                if (php > 0) put("php", php)
                if (proxy.isNotBlank()) put("proxy", proxy)
                if (remark.isNotBlank()) put("remark", remark)
            },
        )
    }

    // ---------- Databases ----------

    suspend fun databases(typ: String? = null, page: Int = 1, limit: Int = 300): Page<DatabaseItem> {
        val params = buildMap {
            put("page", page.toString())
            put("limit", limit.toString())
            if (!typ.isNullOrBlank()) put("type", typ)
        }
        return getTyped<Page<DatabaseItem>>("/api/database", params) ?: Page()
    }

    suspend fun databaseServers(): Page<DatabaseServer> =
        getTyped<Page<DatabaseServer>>("/api/database_server", mapOf("page" to "1", "limit" to "100")) ?: Page()

    suspend fun databaseUsers(): Page<DatabaseUser> =
        getTyped<Page<DatabaseUser>>("/api/database_user", mapOf("page" to "1", "limit" to "100")) ?: Page()

    suspend fun createDatabase(body: JsonObject): JsonElement? = postData("/api/database", body)

    suspend fun deleteDatabase(serverId: Long, name: String) {
        // 与 Web 控制台一致：DELETE /api/database?server_id=..&name=..
        deleteData("/api/database", mapOf("server_id" to serverId.toString(), "name" to name))
    }

    // ---------- Files ----------

    suspend fun fileList(path: String, page: Int = 1, limit: Int = 200, sort: String = "name"): Page<FileEntry> =
        getTyped<Page<FileEntry>>(
            "/api/file/list",
            mapOf(
                "path" to path,
                "page" to page.toString(),
                "limit" to limit.toString(),
                "sort" to sort,
            ),
        ) ?: Page()

    suspend fun fileContent(path: String): JsonObject? = getData("/api/file/content", mapOf("path" to path)).obj()

    suspend fun saveFile(path: String, content: String) {
        postData(
            "/api/file/save",
            buildJsonObject {
                put("path", path)
                put("content", content)
            },
        )
    }

    suspend fun deleteFile(path: String) {
        postData("/api/file/delete", buildJsonObject { put("path", path) })
    }

    suspend fun createFile(path: String, dir: Boolean) {
        postData(
            "/api/file/create",
            buildJsonObject {
                put("path", path)
                put("dir", dir)
            },
        )
    }

    suspend fun uploadFile(directory: String, fileName: String, bytes: ByteArray): JsonElement? {
        val target = directory.trimEnd('/') + "/" + fileName
        val response = client.post("$baseUrl/api/file/upload") {
            header(HttpHeaders.Accept, "application/json")
            if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookieHeader())
            setBody(
                io.ktor.client.request.forms.MultiPartFormDataContent(
                    io.ktor.client.request.forms.formData {
                        append("path", target)
                        append("force", "false")
                        append(
                            "file",
                            bytes,
                            io.ktor.http.Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                append(HttpHeaders.ContentType, "application/octet-stream")
                            },
                        )
                    },
                ),
            )
            timeout {
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
        }
        collectCookies(response)
        val text = response.bodyAsText()
        if (response.status.value == 401) {
            onSessionExpired?.invoke()
            throw SessionExpiredException()
        }
        if (!response.status.isSuccess()) throw ApiException(response.status.value, parseError(text))
        return unwrap(text)
    }

    suspend fun compressFiles(dir: String, paths: List<String>, file: String) {
        postData(
            "/api/file/compress",
            buildJsonObject {
                put("dir", dir)
                put("paths", kotlinx.serialization.json.JsonArray(paths.map { JsonPrimitive(it) }))
                put("file", file)
            },
        )
    }

    /**
     * 复制 / 移动走同一个请求体：面板端 []request.FileControl，
     * 即 [{source, target, force}] 数组，见 internal/service/file.go 的 Copy/Move。
     */
    private fun fileControls(items: List<Pair<String, String>>, force: Boolean): JsonArray =
        JsonArray(items.map { (source, target) ->
            buildJsonObject {
                put("source", source)
                put("target", target)
                put("force", force)
            }
        })

    /** 复制：items 为 (源路径, 目标路径) 列表，force 控制同名是否覆盖。 */
    suspend fun copyFiles(items: List<Pair<String, String>>, force: Boolean = false) {
        postData("/api/file/copy", fileControls(items, force))
    }

    /** 移动 / 重命名（重命名就是源与目标同目录、文件名不同的移动）。 */
    suspend fun moveFiles(items: List<Pair<String, String>>, force: Boolean = false) {
        postData("/api/file/move", fileControls(items, force))
    }

    /** 批量判断路径是否存在，面板接收路径数组、返回同序的 bool 数组。 */
    suspend fun fileExist(paths: List<String>): List<Boolean> {
        if (paths.isEmpty()) return emptyList()
        val result = postData("/api/file/exist", JsonArray(paths.map { JsonPrimitive(it) })).arr()
        return result?.map { (it as? JsonPrimitive)?.content?.toBoolean() ?: false } ?: emptyList()
    }

    /** 单个文件的详细信息，字段与列表项一致，用于属性面板。 */
    suspend fun fileInfo(path: String): FileEntry? =
        getTyped<FileEntry>("/api/file/info", mapOf("path" to path))

    /**
     * 目录大小需要单独统计，返回面板格式化好的字符串。
     * 面板对文件返回 {"size":"1.2 KB"}，对目录直接返回裸字符串（du -sb 的汇总结果），
     * 两种形态都要兼容，否则属性弹窗里目录大小会一直是空。
     */
    suspend fun fileSize(path: String): String {
        return when (val data = getData("/api/file/size", mapOf("path" to path))) {
            is JsonPrimitive -> data.content
            is JsonObject -> (data["size"] as? JsonPrimitive)?.content ?: ""
            else -> ""
        }
    }

    /** 权限：mode 为八进制字符串（如 755），owner/group 为名称。 */
    suspend fun setFilePermission(path: String, mode: String, owner: String, group: String) {
        postData(
            "/api/file/permission",
            buildJsonObject {
                put("path", path)
                put("mode", mode)
                put("owner", owner)
                put("group", group)
            },
        )
    }

    suspend fun uncompressFile(file: String, path: String) {
        postData(
            "/api/file/un_compress",
            buildJsonObject {
                put("file", file)
                put("path", path)
            },
        )
    }

    // ---------- Containers ----------

    suspend fun containers(page: Int = 1, limit: Int = 200): Page<ContainerItem> =
        getTyped<Page<ContainerItem>>("/api/container/container", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun containerAction(id: String, action: String) {
        postData("/api/container/container/$id/$action")
    }

    suspend fun deleteContainer(id: String) {
        deleteData("/api/container/container/$id")
    }

    suspend fun createContainer(name: String, image: String): JsonElement? = postData(
        "/api/container/container",
        buildJsonObject {
            put("name", name)
            put("image", image)
            put("publish_all_ports", true)
            put("restart_policy", "unless-stopped")
        },
    )

    suspend fun containerImages(page: Int = 1, limit: Int = 200): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/container/image", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun containerVolumes(page: Int = 1, limit: Int = 200): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/container/volume", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun containerNetworks(page: Int = 1, limit: Int = 200): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/container/network", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    // ---------- Security ----------

    suspend fun firewallStatus(): JsonElement? = getData("/api/firewall/status")

    suspend fun firewallRules(page: Int = 1, limit: Int = 200): Page<FirewallRule> =
        getTyped<Page<FirewallRule>>("/api/firewall/rule", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun ipRules(page: Int = 1, limit: Int = 200): Page<IpRule> =
        getTyped<Page<IpRule>>("/api/firewall/ip_rule", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun deleteIpRule(rule: IpRule) {
        deleteWithBody(
            "/api/firewall/ip_rule",
            buildJsonObject {
                put("family", rule.family.ifBlank { if (':' in rule.address) "ipv6" else "ipv4" })
                put("protocol", rule.protocol.ifBlank { "tcp/udp" })
                put("address", rule.address)
                put("strategy", rule.strategy.ifBlank { "drop" })
                put("direction", rule.direction.ifBlank { "in" })
            },
        )
    }

    suspend fun createFirewallRule(
        portStart: Int,
        portEnd: Int,
        protocol: String,
        strategy: String,
        direction: String = "in",
        family: String = "ipv4",
        address: String = "",
    ) {
        postData(
            "/api/firewall/rule",
            buildJsonObject {
                put("type", "normal")
                put("family", family)
                put("port_start", portStart)
                put("port_end", portEnd)
                put("protocol", protocol)
                put("address", address)
                put("strategy", strategy)
                put("direction", direction)
            },
        )
    }

    suspend fun tamperStatus(): JsonElement? = getData("/api/tamper/status")

    suspend fun scanSummary(): JsonObject? = getData("/api/firewall/scan/summary").obj()

    // ---------- Cron ----------

    suspend fun cronTasks(page: Int = 1, limit: Int = 100): Page<CronTask> =
        getTyped<Page<CronTask>>("/api/cron", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun cronStatus(id: Long, status: Boolean) {
        postData("/api/cron/$id/status", buildJsonObject { put("id", id); put("status", status) })
    }

    suspend fun fileTail(path: String, limit: Int = 200): List<String> {
        val obj = getData(
            "/api/file/tail",
            mapOf("path" to path, "limit" to limit.toString()),
        ).obj() ?: return emptyList()
        val lines = obj["lines"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return lines.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    suspend fun deleteCron(id: Long) {
        deleteData("/api/cron/$id")
    }

    suspend fun createCron(name: String, time: String, script: String) {
        postData(
            "/api/cron",
            buildJsonObject {
                put("name", name)
                put("type", "shell")
                put("time", time)
                put("script", script)
                put("keep", 7)
                put("flock", true)
                put("storage", 0)
            },
        )
    }

    // ---------- Projects ----------

    suspend fun projects(page: Int = 1, limit: Int = 100): Page<Project> =
        getTyped<Page<Project>>("/api/project", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun createProject(
        name: String,
        type: String,
        description: String,
        rootDir: String,
        workingDir: String,
        execStart: String,
        user: String,
    ): JsonElement? = postData(
        "/api/project",
        buildJsonObject {
            put("name", name)
            put("type", type)
            put("description", description)
            put("root_dir", rootDir)
            put("working_dir", workingDir)
            put("exec_start", execStart)
            put("user", user)
            put("restart", "always")
        },
    )

    suspend fun deleteProject(id: Long) {
        deleteData("/api/project/$id")
    }

    suspend fun systemctlAction(service: String, action: String) {
        postData("/api/systemctl/$action", buildJsonObject { put("service", service) })
    }

    // ---------- Backups ----------

    suspend fun backups(type: String, page: Int = 1, limit: Int = 100): Page<BackupFile> =
        getTyped<Page<BackupFile>>(
            "/api/backup/$type",
            mapOf("page" to page.toString(), "limit" to limit.toString()),
        ) ?: Page()

    suspend fun backupStorage(): Page<BackupStorage> =
        getTyped<Page<BackupStorage>>("/api/backup_storage", mapOf("page" to "1", "limit" to "100")) ?: Page()

    suspend fun createBackup(type: String, target: String, storage: Int = 0) {
        postData(
            "/api/backup/$type",
            buildJsonObject {
                put("type", type)
                put("target", target)
                put("storage", storage)
            },
        )
    }

    suspend fun deleteBackup(type: String, file: String) {
        deleteData("/api/backup/$type/delete", mapOf("type" to type, "file" to file))
    }

    suspend fun backupStorages(): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/backup_storage", mapOf("page" to "1", "limit" to "100")) ?: Page()

    suspend fun createBackupStorage(type: String, name: String, info: JsonObject) {
        postData(
            "/api/backup_storage",
            buildJsonObject {
                put("type", type)
                put("name", name)
                put("info", info)
            },
        )
    }

    suspend fun deleteBackupStorage(id: Long) {
        deleteData("/api/backup_storage/$id")
    }

    suspend fun restoreBackup(type: String, file: String, target: String) {
        postData(
            "/api/backup/$type/restore",
            buildJsonObject {
                put("type", type)
                put("file", file)
                put("target", target)
            },
        )
    }

    // ---------- Certificates ----------

    suspend fun certs(page: Int = 1, limit: Int = 100): Page<Cert> =
        getTyped<Page<Cert>>("/api/cert/cert", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun certRenew(id: Long) {
        postData("/api/cert/cert/$id/renew", buildJsonObject { put("id", id) })
    }

    suspend fun createCert(
        domains: List<String>,
        type: String = "P256",
        autoRenewal: Boolean = true,
        accountId: Long = 0,
        dnsId: Long = 0,
    ): JsonElement? = postData(
        "/api/cert/cert",
        buildJsonObject {
            put("type", type)
            put("domains", kotlinx.serialization.json.JsonArray(domains.map { JsonPrimitive(it) }))
            put("auto_renewal", autoRenewal)
            put("account_id", accountId)
            put("dns_id", dnsId)
            put("website_ids", kotlinx.serialization.json.JsonArray(emptyList()))
        },
    )

    suspend fun obtainCertAuto(id: Long) {
        postData("/api/cert/cert/$id/obtain_auto", buildJsonObject { put("id", id) })
    }

    suspend fun obtainCertSelfSigned(id: Long) {
        postData("/api/cert/cert/$id/obtain_self_signed", buildJsonObject { put("id", id) })
    }

    suspend fun deleteCert(id: Long) {
        deleteData("/api/cert/cert/$id")
    }

    suspend fun certAccounts(): Page<CertAccount> =
        getTyped<Page<CertAccount>>("/api/cert/account", mapOf("page" to "1", "limit" to "100")) ?: Page()

    suspend fun certDns(): Page<CertDns> =
        getTyped<Page<CertDns>>("/api/cert/dns", mapOf("page" to "1", "limit" to "100")) ?: Page()

    suspend fun createCertAccount(ca: String, email: String, keyType: String, kid: String, hmacEncoded: String) {
        postData(
            "/api/cert/account",
            buildJsonObject {
                put("ca", ca)
                put("email", email)
                put("key_type", keyType)
                put("kid", kid)
                put("hmac_encoded", hmacEncoded)
            },
        )
    }

    suspend fun deleteCertAccount(id: Long) {
        deleteData("/api/cert/account/$id")
    }

    suspend fun createCertDns(type: String, name: String, data: JsonObject) {
        postData(
            "/api/cert/dns",
            buildJsonObject {
                put("type", type)
                put("name", name)
                put("data", data)
            },
        )
    }

    suspend fun deleteCertDns(id: Long) {
        deleteData("/api/cert/dns/$id")
    }

    suspend fun deployCert(id: Long, websiteIds: List<Long>, enableHttps: Boolean) {
        postData(
            "/api/cert/cert/$id/deploy",
            buildJsonObject {
                put("id", id)
                put("website_ids", JsonArray(websiteIds.map(::JsonPrimitive)))
                put("enable_https", enableHttps)
            },
        )
    }

    // ---------- Environments ----------

    suspend fun environments(typ: String? = null): List<EnvironmentItem> {
        val params = if (typ.isNullOrBlank()) emptyMap() else mapOf("type" to typ)
        return getTyped<List<EnvironmentItem>>("/api/environment/list", params) ?: emptyList()
    }

    suspend fun installEnvironment(type: String, slug: String) {
        postData(
            "/api/environment/install",
            buildJsonObject {
                put("type", type)
                put("slug", slug)
            },
        )
    }

    suspend fun uninstallEnvironment(type: String, slug: String) {
        postData(
            "/api/environment/uninstall",
            buildJsonObject {
                put("type", type)
                put("slug", slug)
            },
        )
    }

    suspend fun phpModules(version: String): List<JsonObject> =
        getTyped<List<JsonObject>>("/api/environment/php/$version/modules") ?: emptyList()

    suspend fun updateEnvironment(type: String, slug: String) {
        postData(
            "/api/environment/update",
            buildJsonObject {
                put("type", type)
                put("slug", slug)
            },
        )
    }

    // ---------- Logs ----------

    suspend fun logs(type: String = "app", limit: Int = 200, date: String? = null): List<LogEntry> {
        val params = buildMap {
            put("type", type)
            put("limit", limit.toString())
            if (!date.isNullOrBlank()) put("date", date)
        }
        return getTyped<List<LogEntry>>("/api/log/list", params) ?: emptyList()
    }

    suspend fun sshLogs(limit: Int = 100): List<LogEntry> =
        getTyped<List<LogEntry>>("/api/log/ssh", mapOf("limit" to limit.toString())) ?: emptyList()

    suspend fun logDates(type: String): List<String> =
        getTyped<List<String>>("/api/log/dates", mapOf("type" to type)) ?: emptyList()

    // ---------- Settings ----------

    suspend fun setting(): JsonObject? = getData("/api/setting").obj()

    suspend fun userTokens(): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/user_tokens", mapOf("user_id" to "1", "page" to "1", "limit" to "100")) ?: Page()

    // ---------- Tasks ----------

    suspend fun taskStatus(): JsonObject? = getData("/api/task/status").obj()

    suspend fun tasks(page: Int = 1, limit: Int = 100): Page<TaskInfo> =
        getTyped<Page<TaskInfo>>("/api/task", mapOf("page" to page.toString(), "limit" to limit.toString())) ?: Page()

    suspend fun taskDetail(id: Long): JsonObject? = getData("/api/task/$id").obj()

    suspend fun cancelTask(id: Long) {
        postData("/api/task/$id/cancel")
    }

    suspend fun deleteTask(id: Long) {
        deleteData("/api/task/$id")
    }

    // ---------- Users & account security ----------

    suspend fun users(page: Int = 1, limit: Int = 100): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/users", mapOf("page" to "$page", "limit" to "$limit")) ?: Page()

    suspend fun createUser(username: String, password: String, email: String) {
        postData(
            "/api/users",
            buildJsonObject {
                put("username", username)
                put("password", password)
                put("email", email)
            },
        )
    }

    suspend fun deleteUser(id: Long) {
        deleteData("/api/users/$id")
    }

    suspend fun updateUserUsername(id: Long, username: String) {
        postData("/api/users/$id/username", buildJsonObject { put("username", username) })
    }

    suspend fun updateUserEmail(id: Long, email: String) {
        postData("/api/users/$id/email", buildJsonObject { put("email", email) })
    }

    suspend fun updateUserPassword(id: Long, password: String) {
        postData("/api/users/$id/password", buildJsonObject { put("password", password) })
    }

    suspend fun userTokens(userId: Long, page: Int = 1, limit: Int = 100): Page<JsonObject> =
        getTyped<Page<JsonObject>>(
            "/api/user_tokens",
            mapOf("user_id" to "$userId", "page" to "$page", "limit" to "$limit"),
        ) ?: Page()

    /** expiredAt 为毫秒时间戳（与服务端 * time.Millisecond 的换算一致）。 */
    suspend fun createUserToken(userId: Long, ips: List<String>, expiredAt: Long): JsonObject? =
        postData(
            "/api/user_tokens",
            buildJsonObject {
                put("user_id", userId)
                put("ips", JsonArray(ips.map(::JsonPrimitive)))
                put("expired_at", expiredAt)
            },
        ).obj()

    suspend fun updateUserToken(id: Long, ips: List<String>, expiredAt: Long) {
        putData(
            "/api/user_tokens/$id",
            buildJsonObject {
                put("ips", JsonArray(ips.map(::JsonPrimitive)))
                put("expired_at", expiredAt)
            },
        )
    }

    suspend fun deleteUserToken(id: Long) {
        deleteData("/api/user_tokens/$id")
    }

    suspend fun passkeys(userId: Long): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/user_passkeys", mapOf("user_id" to "$userId")) ?: Page()

    suspend fun passkeysSupported(): Boolean =
        runCatching { getData("/api/user_passkeys/supported")?.let { it == JsonPrimitive(true) || it.toString() == "true" } ?: false }
            .getOrDefault(false)

    suspend fun deletePasskey(id: Long, userId: Long) {
        deleteData("/api/user_passkeys/$id", mapOf("user_id" to "$userId"))
    }

    // ---------- Notify & alert rules ----------

    suspend fun notifyChannels(page: Int = 1, limit: Int = 100): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/notify/channel", mapOf("page" to "$page", "limit" to "$limit")) ?: Page()

    suspend fun notifyChannelsAll(): List<JsonObject> =
        getTyped<List<JsonObject>>("/api/notify/channel/all") ?: emptyList()

    suspend fun createNotifyChannel(name: String, type: String, config: JsonElement, enabled: Boolean) {
        postData(
            "/api/notify/channel",
            buildJsonObject {
                put("name", name)
                put("type", type)
                put("config", config)
                put("enabled", enabled)
            },
        )
    }

    suspend fun updateNotifyChannel(id: Long, name: String, type: String, config: JsonElement, enabled: Boolean) {
        putData(
            "/api/notify/channel/$id",
            buildJsonObject {
                put("name", name)
                put("type", type)
                put("config", config)
                put("enabled", enabled)
            },
        )
    }

    suspend fun deleteNotifyChannel(id: Long) {
        deleteData("/api/notify/channel/$id")
    }

    suspend fun testNotifyChannel(id: Long) {
        postData("/api/notify/channel/$id/test")
    }

    suspend fun notifySetting(): JsonObject? = getData("/api/notify/setting").obj()

    suspend fun updateNotifySetting(events: List<String>, channels: List<Long>) {
        postData(
            "/api/notify/setting",
            buildJsonObject {
                put("events", JsonArray(events.map(::JsonPrimitive)))
                put("channels", JsonArray(channels.map(::JsonPrimitive)))
            },
        )
    }

    suspend fun alertRules(page: Int = 1, limit: Int = 100): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/alert/rule", mapOf("page" to "$page", "limit" to "$limit")) ?: Page()

    suspend fun createAlertRule(body: JsonObject) {
        postData("/api/alert/rule", body)
    }

    suspend fun updateAlertRule(id: Long, body: JsonObject) {
        putData("/api/alert/rule/$id", body)
    }

    suspend fun deleteAlertRule(id: Long) {
        deleteData("/api/alert/rule/$id")
    }

    // ---------- Processes ----------

    suspend fun processes(
        page: Int = 1,
        limit: Int = 50,
        keyword: String = "",
        sort: String = "cpu",
        order: String = "desc",
    ): Page<JsonObject> {
        val params = buildMap {
            put("page", "$page")
            put("limit", "$limit")
            put("sort", sort)
            put("order", order)
            if (keyword.isNotBlank()) put("keyword", keyword)
        }
        return getTyped<Page<JsonObject>>("/api/process", params) ?: Page()
    }

    suspend fun processDetail(pid: Long): JsonObject? =
        getData("/api/process/detail", mapOf("pid" to "$pid")).obj()

    suspend fun killProcess(pid: Long) {
        postData("/api/process/kill", buildJsonObject { put("pid", pid) })
    }

    suspend fun signalProcess(pid: Long, signal: Int) {
        postData(
            "/api/process/signal",
            buildJsonObject {
                put("pid", pid)
                put("signal", signal)
            },
        )
    }

    // ---------- WebHook & templates ----------

    suspend fun webhooks(page: Int = 1, limit: Int = 100): Page<JsonObject> =
        getTyped<Page<JsonObject>>("/api/webhook", mapOf("page" to "$page", "limit" to "$limit")) ?: Page()

    suspend fun createWebhook(name: String, script: String, raw: Boolean, user: String): JsonObject? =
        postData(
            "/api/webhook",
            buildJsonObject {
                put("name", name)
                put("script", script)
                put("raw", raw)
                put("user", user)
            },
        ).obj()

    suspend fun updateWebhook(id: Long, name: String, script: String, raw: Boolean, user: String, status: Boolean) {
        putData(
            "/api/webhook/$id",
            buildJsonObject {
                put("name", name)
                put("script", script)
                put("raw", raw)
                put("user", user)
                put("status", status)
            },
        )
    }

    suspend fun deleteWebhook(id: Long) {
        deleteData("/api/webhook/$id")
    }

    suspend fun templates(query: String = "", page: Int = 1, limit: Int = 100): Page<JsonObject> {
        val params = buildMap {
            put("page", "$page")
            put("limit", "$limit")
            if (query.isNotBlank()) put("query", query)
        }
        return getTyped<Page<JsonObject>>("/api/template", params) ?: Page()
    }

    suspend fun deployTemplate(slug: String, name: String, compose: String, envs: List<Pair<String, String>>, autoFirewall: Boolean) {
        postData(
            "/api/template",
            buildJsonObject {
                put("slug", slug)
                put("name", name)
                put("compose", compose)
                put(
                    "envs",
                    JsonArray(envs.map { (key, value) ->
                        buildJsonObject {
                            put("key", key)
                            put("value", value)
                        }
                    }),
                )
                put("auto_firewall", autoFirewall)
            },
        )
    }

    // ---------- Database servers / users ----------

    suspend fun databaseServers(type: String? = null, page: Int = 1, limit: Int = 100): Page<JsonObject> {
        val params = buildMap {
            put("page", "$page")
            put("limit", "$limit")
            if (!type.isNullOrBlank()) put("type", type)
        }
        return getTyped<Page<JsonObject>>("/api/database_server", params) ?: Page()
    }

    suspend fun createDatabaseServer(
        name: String,
        type: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        remark: String = "",
    ) {
        postData(
            "/api/database_server",
            buildJsonObject {
                put("name", name)
                put("type", type)
                put("host", host)
                put("port", port)
                put("username", username)
                put("password", password)
                put("remark", remark)
            },
        )
    }

    suspend fun updateDatabaseServer(
        id: Long,
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        remark: String = "",
    ) {
        putData(
            "/api/database_server/$id",
            buildJsonObject {
                put("name", name)
                put("host", host)
                put("port", port)
                put("username", username)
                put("password", password)
                put("remark", remark)
            },
        )
    }

    suspend fun deleteDatabaseServer(id: Long) {
        deleteData("/api/database_server/$id")
    }

    suspend fun syncDatabaseServer(id: Long) {
        postData("/api/database_server/$id/sync")
    }

    suspend fun databaseUsers(type: String? = null, page: Int = 1, limit: Int = 100): Page<JsonObject> {
        val params = buildMap {
            put("page", "$page")
            put("limit", "$limit")
            if (!type.isNullOrBlank()) put("type", type)
        }
        return getTyped<Page<JsonObject>>("/api/database_user", params) ?: Page()
    }

    suspend fun createDatabaseUser(
        serverId: Long,
        username: String,
        password: String,
        host: String,
        privileges: List<String>,
        remark: String = "",
    ) {
        postData(
            "/api/database_user",
            buildJsonObject {
                put("server_id", serverId)
                put("username", username)
                put("password", password)
                put("host", host)
                put("privileges", JsonArray(privileges.map(::JsonPrimitive)))
                put("remark", remark)
            },
        )
    }

    suspend fun updateDatabaseUser(id: Long, password: String?, privileges: List<String>, remark: String) {
        putData(
            "/api/database_user/$id",
            buildJsonObject {
                if (!password.isNullOrEmpty()) put("password", password)
                put("privileges", JsonArray(privileges.map(::JsonPrimitive)))
                put("remark", remark)
            },
        )
    }

    suspend fun deleteDatabaseUser(id: Long) {
        deleteData("/api/database_user/$id")
    }

    // ---------- Redis / Elasticsearch ----------

    suspend fun redisDatabases(serverId: Long): JsonElement? =
        getData("/api/database_redis/databases", mapOf("server_id" to "$serverId"))

    suspend fun redisKeys(
        serverId: Long,
        db: Int,
        search: String = "",
        page: Int = 1,
        limit: Int = 100,
    ): Page<JsonObject> =
        getTyped<Page<JsonObject>>(
            "/api/database_redis/data",
            buildMap {
                put("server_id", "$serverId")
                put("db", "$db")
                put("page", "$page")
                put("limit", "$limit")
                if (search.isNotBlank()) put("search", search)
            },
        ) ?: Page()

    suspend fun redisKey(serverId: Long, db: Int, key: String): JsonObject? =
        getData(
            "/api/database_redis/key",
            mapOf("server_id" to "$serverId", "db" to "$db", "key" to key),
        ).obj()

    suspend fun redisSetKey(serverId: Long, db: Int, key: String, value: String, type: String, ttl: Long) {
        postData(
            "/api/database_redis/key",
            buildJsonObject {
                put("server_id", serverId)
                put("db", db)
                put("key", key)
                put("value", value)
                put("type", type)
                put("ttl", ttl)
            },
        )
    }

    suspend fun redisKeyTtl(serverId: Long, db: Int, key: String, ttl: Long) {
        postData(
            "/api/database_redis/key/ttl",
            buildJsonObject {
                put("server_id", serverId)
                put("db", db)
                put("key", key)
                put("ttl", ttl)
            },
        )
    }

    suspend fun redisRenameKey(serverId: Long, db: Int, oldKey: String, newKey: String) {
        postData(
            "/api/database_redis/key/rename",
            buildJsonObject {
                put("server_id", serverId)
                put("db", db)
                put("old_key", oldKey)
                put("new_key", newKey)
            },
        )
    }

    suspend fun redisClear(serverId: Long, db: Int) {
        postData(
            "/api/database_redis/clear",
            buildJsonObject {
                put("server_id", serverId)
                put("db", db)
            },
        )
    }

    suspend fun redisDeleteKey(serverId: Long, db: Int, key: String) {
        deleteData(
            "/api/database_redis/key",
            mapOf("server_id" to "$serverId", "db" to "$db", "key" to key),
        )
    }

    suspend fun esIndices(serverId: Long): List<JsonObject> =
        getTyped<List<JsonObject>>("/api/database_elasticsearch/indices", mapOf("server_id" to "$serverId")) ?: emptyList()

    suspend fun esDocuments(serverId: Long, index: String, search: String = "", page: Int = 1, limit: Int = 50): Page<JsonObject> =
        getTyped<Page<JsonObject>>(
            "/api/database_elasticsearch/data",
            buildMap {
                put("server_id", "$serverId")
                put("index", index)
                put("page", "$page")
                put("limit", "$limit")
                if (search.isNotBlank()) put("search", search)
            },
        ) ?: Page()

    suspend fun esDocument(serverId: Long, index: String, id: String): JsonObject? =
        getData(
            "/api/database_elasticsearch/document",
            mapOf("server_id" to "$serverId", "index" to index, "id" to id),
        ).obj()

    suspend fun esCreateIndex(serverId: Long, name: String) {
        postData(
            "/api/database_elasticsearch/index",
            buildJsonObject {
                put("server_id", serverId)
                put("name", name)
            },
        )
    }

    suspend fun esDeleteIndex(serverId: Long, name: String) {
        deleteData(
            "/api/database_elasticsearch/index",
            mapOf("server_id" to "$serverId", "name" to name),
        )
    }

    suspend fun esSetDocument(serverId: Long, index: String, id: String, body: String) {
        postData(
            "/api/database_elasticsearch/document",
            buildJsonObject {
                put("server_id", serverId)
                put("index", index)
                if (id.isNotBlank()) put("id", id)
                put("body", body)
            },
        )
    }

    suspend fun esDeleteDocument(serverId: Long, index: String, id: String) {
        deleteData(
            "/api/database_elasticsearch/document",
            mapOf("server_id" to "$serverId", "index" to index, "id" to id),
        )
    }

    // ---------- Network latency ----------

    suspend fun measureLatency(): Long? {
        return runCatching {
            val started = kotlin.time.TimeSource.Monotonic.markNow()
            getData("/api/user/is_login")
            started.elapsedNow().inWholeMilliseconds
        }.getOrNull()
    }

    // ---------- Terminal ----------

    private fun wsUrl(path: String): String {
        val base = when {
            baseUrl.startsWith("https://", true) -> "wss://" + baseUrl.removePrefix("https://").removePrefix("HTTPS://")
            baseUrl.startsWith("http://", true) -> "ws://" + baseUrl.removePrefix("http://").removePrefix("HTTP://")
            else -> "wss://$baseUrl"
        }
        return base + path
    }

    suspend fun openPty(useBash: Boolean = true): TerminalConnection {
        val session = client.webSocketSession {
            url(wsUrl("/api/ws/pty"))
            if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookieHeader())
        }
        session.send(Frame.Text(if (useBash) "bash" else "sh"))
        return TerminalConnection(session)
    }

    suspend fun openSshTerminal(hostId: Long): TerminalConnection {
        val session = client.webSocketSession {
            url(wsUrl("/api/ws/ssh?id=$hostId"))
            if (cookies.isNotEmpty()) header(HttpHeaders.Cookie, cookieHeader())
        }
        return TerminalConnection(session)
    }

    fun close() {
        client.close()
    }
}

class TerminalConnection(
    private val session: DefaultClientWebSocketSession,
) : TerminalChannel {
    // UI 回调必须在主线程更新 Compose 状态，否则终端输出不会触发重组
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun start(
        onOutput: (String) -> Unit,
        onClosed: (String?) -> Unit,
    ): Job = scope.launch {
        var closedReason: String? = null
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> onOutput(frame.readText())
                    is Frame.Binary -> onOutput(frame.readBytes().decodeToString())
                    is Frame.Close -> closedReason = frame.data.decodeToString()
                    else -> Unit
                }
            }
        } catch (t: Throwable) {
            closedReason = t.message
        } finally {
            onClosed(closedReason)
        }
    }

    override suspend fun send(text: String) {
        runCatching { session.send(Frame.Text(text)) }
    }

    suspend fun sendRaw(bytes: ByteArray) {
        runCatching { session.send(Frame.Binary(true, bytes)) }
    }

    override suspend fun resize(columns: Int, rows: Int) {
        runCatching {
            session.send(Frame.Text("""{"resize":true,"columns":$columns,"rows":$rows}"""))
        }
    }

    suspend fun ping() {
        runCatching { session.send(Frame.Text("""{"ping":true}""")) }
        delay(1)
    }

    override suspend fun close() {
        runCatching { session.close() }
        scope.cancel()
    }

    fun isActive(): Boolean = session.isActive
}
