package com.acepanel.client.mcp

import com.acepanel.client.data.AceApi
import com.acepanel.client.data.PageKey
import com.acepanel.client.data.RootTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * MCP JSON-RPC 分发 + 面板工具集。
 *
 * 所有工具最终都落到 [AceApi] 的现有方法上，因此：
 * - 复用客户端已登录的 Cookie 会话，不需要重新走 RSA 登录；
 * - 接口语义与 App 完全一致（参数、必填项、默认值都照抄）；
 * - 未单独封装的接口可以用 `ace_raw_request` 直连，覆盖 AceApi.kt 的全部 /api 路径。
 */
internal class McpToolRegistry {

    private val tools: Map<String, McpTool> = buildTools().associateBy { it.name }

    fun handle(body: String): HttpResult = runBlocking {
        val element = try {
            rpcJson.parseToJsonElement(body)
        } catch (t: Throwable) {
            return@runBlocking HttpResult(
                400,
                errorResponse(null, -32700, "JSON 解析失败：${t.message}").toString(),
            )
        }
        when (element) {
            is JsonArray -> {
                val responses = element.mapNotNull { item ->
                    (item as? JsonObject)?.let { handleSingle(it) }
                }
                if (responses.isEmpty()) {
                    HttpResult(202, "")
                } else {
                    HttpResult(200, JsonArray(responses).toString())
                }
            }

            is JsonObject -> {
                val response = handleSingle(element)
                if (response == null) HttpResult(202, "") else HttpResult(200, response.toString())
            }

            else -> HttpResult(400, errorResponse(null, -32600, "无效的 JSON-RPC 请求").toString())
        }
    }

    private suspend fun handleSingle(request: JsonObject): JsonObject? {
        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull
        if (method == null) {
            return if (id != null) errorResponse(id, -32600, "缺少 method") else null
        }
        // 没有 id 的是 notification（例如 notifications/initialized），按规范不回包。
        if (id == null) return null

        return when (method) {
            "initialize" -> {
                val requested = request["params"]
                    ?.jsonObject
                    ?.get("protocolVersion")
                    ?.jsonPrimitive
                    ?.contentOrNull
                successResponse(
                    id,
                    buildJsonObject {
                        put("protocolVersion", requested ?: "2025-06-18")
                        put(
                            "capabilities",
                            buildJsonObject {
                                put("tools", buildJsonObject { put("listChanged", false) })
                            },
                        )
                        put(
                            "serverInfo",
                            buildJsonObject {
                                put("name", "acepanel-client-bridge")
                                put("version", "1.0.0")
                            },
                        )
                        put(
                            "instructions",
                            "这是运行中的 AcePanel 客户端内置的本地 MCP 桥，工具直接复用客户端已登录的面板会话。" +
                                "若工具返回未登录，请先在客户端里登录目标服务器。ace_raw_request 可调用 AceApi.kt 里的任意 /api 接口。",
                        )
                    },
                )
            }

            "ping" -> successResponse(id, buildJsonObject {})

            "tools/list" -> successResponse(
                id,
                buildJsonObject { put("tools", toolList()) },
            )

            "tools/call" -> {
                val params = request["params"]?.jsonObject ?: buildJsonObject {}
                val name = params["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val arguments = params["arguments"]?.jsonObject ?: buildJsonObject {}
                successResponse(id, callTool(name, arguments))
            }

            "logging/setLevel" -> successResponse(id, buildJsonObject {})

            else -> errorResponse(id, -32601, "未知方法：$method")
        }
    }

    private suspend fun callTool(name: String, arguments: JsonObject): JsonObject {
        val tool = tools[name] ?: return toolResult("未知工具：$name", isError = true)
        return try {
            val value = tool.handler(arguments)
            toolResult(prettyJson.encodeToString(JsonElement.serializer(), value), isError = false)
        } catch (t: Throwable) {
            val message = t.message?.takeIf { it.isNotBlank() } ?: (t::class.simpleName ?: "调用失败")
            toolResult(message, isError = true)
        }
    }

    private fun toolList(): JsonArray = JsonArray(
        tools.values.map { tool ->
            buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("inputSchema", tool.inputSchema)
            }
        },
    )

    private fun successResponse(id: JsonElement?, result: JsonElement): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("result", result)
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message)
            },
        )
    }

    private fun toolResult(text: String, isError: Boolean): JsonObject = buildJsonObject {
        put(
            "content",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                ),
            ),
        )
        put("isError", isError)
    }

    // ---------------------------------------------------------------- 工具定义

    private fun buildTools(): List<McpTool> {
        val result = mutableListOf<McpTool>()

        fun register(
            name: String,
            description: String,
            inputSchema: JsonObject,
            handler: suspend (JsonObject) -> JsonElement,
        ) {
            result += McpTool(name, description, inputSchema, handler)
        }

        // ---------- 状态与万能接口 ----------

        register(
            "ace_status",
            "查看客户端当前状态：是否已登录面板、当前服务器、账号与面板信息。无参数。",
            schema(),
        ) {
            val store = McpBridgeHost.store
            val profile = store?.activeProfile
            buildJsonObject {
                put("clientRunning", store != null)
                put("loggedIn", store?.api != null)
                put(
                    "server",
                    profile?.let {
                        buildJsonObject {
                            put("name", it.name)
                            put("baseUrl", it.baseUrl)
                            put("entrance", it.entrance)
                            put("hostname", it.hostname)
                            put("panelVersion", it.panelVersion)
                        }
                    } ?: JsonNull,
                )
                put("user", store?.user?.let { encode(it) } ?: JsonNull)
                put("panel", store?.panelInfo?.let { encode(it) } ?: JsonNull)
            }
        }

        register(
            "ace_raw_request",
            "万能接口：直接向面板后端发起请求，覆盖 AceApi.kt 里的全部 /api 接口。" +
                "path 必须以 /api/ 开头；返回 {status, durationMs, body, json}，不解析响应信封、也不因 HTTP 错误码抛异常。",
            schema(
                required = listOf("path"),
                props = mapOf(
                    "method" to pStr("HTTP 方法", enum = listOf("GET", "POST", "PUT", "PATCH", "DELETE"), default = "GET"),
                    "path" to pStr("接口路径，例如 /api/home/system_info"),
                    "query" to pObj("查询参数键值对，例如 {\"page\":\"1\",\"limit\":\"20\"}"),
                    "body" to pObj("JSON 请求体"),
                ),
            ),
        ) { args ->
            val api = api()
            val method = args.strOrNull("method")?.uppercase() ?: "GET"
            val path = args.str("path")
            require(path.startsWith("/api/")) { "path 必须以 /api/ 开头，例如 /api/home/system_info" }
            val query = args.objOrNull("query")
                ?.entries
                ?.mapNotNull { (key, value) ->
                    val text = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    key to text
                }
                ?.toMap()
                ?: emptyMap()
            val raw = api.callRaw(method, path, query, args["body"] as? JsonObject)
            buildJsonObject {
                put("status", raw.status)
                put("durationMs", raw.durationMs)
                put("body", raw.body)
                put("json", runCatching { rpcJson.parseToJsonElement(raw.body) }.getOrNull() ?: JsonNull)
            }
        }

        // ---------- UI 导航（真实驱动客户端界面） ----------

        register(
            "ui_open_page",
            "把正在运行的客户端切换到指定页面（真实 UI 导航）。page 取值：" +
                PageKey.entries.joinToString("、") { it.name },
            schema(
                required = listOf("page"),
                props = mapOf(
                    "page" to pStr("目标页面", enum = PageKey.entries.map { it.name }),
                ),
            ),
        ) { args ->
            val store = McpBridgeHost.store ?: error("客户端尚未启动")
            val pageName = args.str("page").uppercase()
            val page = PageKey.entries.firstOrNull { it.name == pageName }
                ?: error("未知页面：$pageName")
            withContext(Dispatchers.Main) {
                store.pageStack.clear()
                store.selectedTab = RootTab.OVERVIEW
                store.openPage(page)
            }
            buildJsonObject {
                put("ok", true)
                put("page", page.name)
            }
        }

        register(
            "ui_switch_tab",
            "切换客户端主标签（总览 / 服务器 / 终端 / 监控 / 我的）。",
            schema(
                required = listOf("tab"),
                props = mapOf(
                    "tab" to pStr("主标签", enum = RootTab.entries.map { it.name }),
                ),
            ),
        ) { args ->
            val store = McpBridgeHost.store ?: error("客户端尚未启动")
            val tabName = args.str("tab").uppercase()
            val tab = RootTab.entries.firstOrNull { it.name == tabName }
                ?: error("未知主标签：$tabName")
            withContext(Dispatchers.Main) {
                store.pageStack.clear()
                store.selectedTab = tab
            }
            buildJsonObject {
                put("ok", true)
                put("tab", tab.name)
            }
        }

        register(
            "ui_back",
            "关闭客户端当前页面，返回上一级。",
            schema(),
        ) {
            val store = McpBridgeHost.store ?: error("客户端尚未启动")
            withContext(Dispatchers.Main) { store.closePage() }
            ok()
        }

        register(
            "ui_current",
            "查看客户端当前导航状态：主标签与页面栈。",
            schema(),
        ) {
            val store = McpBridgeHost.store ?: error("客户端尚未启动")
            withContext(Dispatchers.Main) {
                buildJsonObject {
                    put("selectedTab", store.selectedTab.name)
                    put("pages", JsonArray(store.pageStack.map { JsonPrimitive(it.name) }))
                }
            }
        }

        // ---------- 首页 / 监控 ----------

        register("ace_system_info", "系统信息：主机名、系统版本、面板版本、CPU/内存/磁盘概览。", schema()) {
            encode(api().systemInfo())
        }

        register("ace_panel_info", "面板信息：面板名称、版本等。", schema()) {
            encode(api().panelInfo())
        }

        register("ace_count_info", "资源计数：网站、数据库、应用等数量。", schema()) {
            encode(api().countInfo())
        }

        register(
            "ace_current_info",
            "实时负载：CPU / 内存 / 网卡吞吐 / 磁盘 IO。可选 nets、disks 过滤。",
            schema(
                props = mapOf(
                    "nets" to pStrArray("网卡名列表，留空返回全部"),
                    "disks" to pStrArray("磁盘挂载点列表，留空返回全部"),
                ),
            ),
        ) { args ->
            encode(api().currentInfo(args.stringList("nets"), args.stringList("disks")))
        }

        // ---------- 网站 ----------

        register(
            "ace_list_websites",
            "列出网站。type 可选 all / proxy / static / php。",
            schema(
                props = mapOf(
                    "type" to pStr("网站类型", enum = listOf("all", "proxy", "static", "php"), default = "all"),
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 200, min = 1),
                ),
            ),
        ) { args ->
            encode(api().websites(args.strOrNull("type") ?: "all", args.int("page", 1), args.int("limit", 200)))
        }

        register(
            "ace_create_website",
            "创建网站。type=static/proxy/php；domains 必填；proxy 填反向代理目标；php 为 PHP 版本 ID。",
            schema(
                required = listOf("name", "domains", "type"),
                props = mapOf(
                    "name" to pStr("网站名称（唯一）"),
                    "domains" to pStrArray("域名列表"),
                    "type" to pStr("网站类型", enum = listOf("static", "proxy", "php")),
                    "path" to pStr("网站根目录，留空使用面板默认"),
                    "php" to pInt("PHP 版本 ID，0 表示不指定", default = 0),
                    "proxy" to pStr("反向代理目标，例如 http://127.0.0.1:8080"),
                    "listens" to pStrArray("监听端口列表，默认 [\"80\"]"),
                    "remark" to pStr("备注"),
                ),
            ),
        ) { args ->
            api().createWebsite(
                name = args.str("name"),
                domains = args.stringList("domains"),
                type = args.str("type"),
                path = args.strOrNull("path") ?: "",
                php = args.int("php", 0),
                proxy = args.strOrNull("proxy") ?: "",
                listens = args.stringList("listens").ifEmpty { listOf("80") },
                remark = args.strOrNull("remark") ?: "",
            ) ?: JsonNull
        }

        register(
            "ace_set_website_status",
            "启用或停用网站。",
            schema(
                required = listOf("id", "status"),
                props = mapOf(
                    "id" to pInt("网站 ID"),
                    "status" to pBool("true 启用 / false 停用"),
                ),
            ),
        ) { args ->
            api().websiteStatus(args.long("id"), args.bool("status", true))
            ok()
        }

        register(
            "ace_delete_website",
            "删除网站。可指定同时删除网站目录与关联数据库。",
            schema(
                required = listOf("id"),
                props = mapOf(
                    "id" to pInt("网站 ID"),
                    "deletePath" to pBool("是否删除网站目录", default = false),
                    "deleteDb" to pBool("是否删除关联数据库", default = false),
                ),
            ),
        ) { args ->
            api().deleteWebsite(args.long("id"), args.bool("deletePath", false), args.bool("deleteDb", false))
            ok()
        }

        // ---------- 容器 ----------

        register(
            "ace_list_containers",
            "列出 Docker 容器。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 200, min = 1),
                ),
            ),
        ) { args ->
            encode(api().containers(args.int("page", 1), args.int("limit", 200)))
        }

        register(
            "ace_container_action",
            "对容器执行 start / stop / restart / pause / unpause / kill。",
            schema(
                required = listOf("id", "action"),
                props = mapOf(
                    "id" to pStr("容器 ID"),
                    "action" to pStr(
                        "操作",
                        enum = listOf("start", "stop", "restart", "pause", "unpause", "kill"),
                    ),
                ),
            ),
        ) { args ->
            api().containerAction(args.str("id"), args.str("action"))
            ok()
        }

        register(
            "ace_create_container",
            "用镜像创建容器（自动发布全部端口，restart_policy=unless-stopped）。",
            schema(
                required = listOf("name", "image"),
                props = mapOf(
                    "name" to pStr("容器名称"),
                    "image" to pStr("镜像名，例如 nginx:latest"),
                ),
            ),
        ) { args ->
            api().createContainer(args.str("name"), args.str("image")) ?: JsonNull
        }

        register(
            "ace_delete_container",
            "删除容器。",
            schema(required = listOf("id"), props = mapOf("id" to pStr("容器 ID"))),
        ) { args ->
            api().deleteContainer(args.str("id"))
            ok()
        }

        // ---------- 文件 ----------

        register(
            "ace_list_files",
            "列出目录内容。",
            schema(
                required = listOf("path"),
                props = mapOf(
                    "path" to pStr("目录绝对路径，例如 /opt"),
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 200, min = 1),
                    "sort" to pStr("排序字段", default = "name"),
                ),
            ),
        ) { args ->
            encode(api().fileList(args.str("path"), args.int("page", 1), args.int("limit", 200), args.strOrNull("sort") ?: "name"))
        }

        register(
            "ace_read_file",
            "读取文本文件内容（面板返回 {content, ...} 结构）。",
            schema(required = listOf("path"), props = mapOf("path" to pStr("文件绝对路径"))),
        ) { args ->
            api().fileContent(args.str("path")) ?: JsonNull
        }

        register(
            "ace_write_file",
            "保存文本文件（覆盖写入）。",
            schema(
                required = listOf("path", "content"),
                props = mapOf(
                    "path" to pStr("文件绝对路径"),
                    "content" to pStr("文件内容"),
                ),
            ),
        ) { args ->
            api().saveFile(args.str("path"), args.str("content"))
            ok()
        }

        register(
            "ace_create_file",
            "创建文件或目录。",
            schema(
                required = listOf("path"),
                props = mapOf(
                    "path" to pStr("目标绝对路径"),
                    "dir" to pBool("true 创建目录 / false 创建文件", default = false),
                ),
            ),
        ) { args ->
            api().createFile(args.str("path"), args.bool("dir", false))
            ok()
        }

        register(
            "ace_delete_file",
            "删除文件或目录。",
            schema(required = listOf("path"), props = mapOf("path" to pStr("目标绝对路径"))),
        ) { args ->
            api().deleteFile(args.str("path"))
            ok()
        }

        register(
            "ace_copy_files",
            "批量复制文件。items 为 [{source, target}]。",
            schema(
                required = listOf("items"),
                props = mapOf(
                    "items" to pPairArray("复制项列表，每项包含 source 与 target"),
                    "force" to pBool("目标存在时是否强制覆盖", default = false),
                ),
            ),
        ) { args ->
            api().copyFiles(args.pairs("items"), args.bool("force", false))
            ok()
        }

        register(
            "ace_move_files",
            "批量移动文件。items 为 [{source, target}]。",
            schema(
                required = listOf("items"),
                props = mapOf(
                    "items" to pPairArray("移动项列表，每项包含 source 与 target"),
                    "force" to pBool("目标存在时是否强制覆盖", default = false),
                ),
            ),
        ) { args ->
            api().moveFiles(args.pairs("items"), args.bool("force", false))
            ok()
        }

        register(
            "ace_set_file_permission",
            "设置文件权限与属主。",
            schema(
                required = listOf("path", "mode", "owner", "group"),
                props = mapOf(
                    "path" to pStr("目标绝对路径"),
                    "mode" to pStr("权限，例如 755"),
                    "owner" to pStr("属主，例如 root"),
                    "group" to pStr("属组，例如 root"),
                ),
            ),
        ) { args ->
            api().setFilePermission(args.str("path"), args.str("mode"), args.str("owner"), args.str("group"))
            ok()
        }

        register(
            "ace_compress_files",
            "压缩文件或目录为 zip。",
            schema(
                required = listOf("dir", "paths", "file"),
                props = mapOf(
                    "dir" to pStr("这些路径所在的目录"),
                    "paths" to pStrArray("要压缩的路径列表"),
                    "file" to pStr("输出压缩包路径"),
                ),
            ),
        ) { args ->
            api().compressFiles(args.str("dir"), args.stringList("paths"), args.str("file"))
            ok()
        }

        register(
            "ace_uncompress_file",
            "解压文件到目标目录。",
            schema(
                required = listOf("file", "path"),
                props = mapOf(
                    "file" to pStr("压缩包绝对路径"),
                    "path" to pStr("解压目标目录"),
                ),
            ),
        ) { args ->
            api().uncompressFile(args.str("file"), args.str("path"))
            ok()
        }

        // ---------- 计划任务 ----------

        register(
            "ace_list_cron",
            "列出计划任务。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 100, min = 1),
                ),
            ),
        ) { args ->
            encode(api().cronTasks(args.int("page", 1), args.int("limit", 100)))
        }

        register(
            "ace_create_cron",
            "创建 shell 计划任务。time 为 6 段 cron 表达式（秒 分 时 日 月 周）。",
            schema(
                required = listOf("name", "time", "script"),
                props = mapOf(
                    "name" to pStr("任务名称"),
                    "time" to pStr("cron 表达式，例如 0 0 3 * * *"),
                    "script" to pStr("要执行的 shell 脚本"),
                ),
            ),
        ) { args ->
            api().createCron(args.str("name"), args.str("time"), args.str("script"))
            ok()
        }

        register(
            "ace_set_cron_status",
            "启用或停用计划任务。",
            schema(
                required = listOf("id", "status"),
                props = mapOf(
                    "id" to pInt("任务 ID"),
                    "status" to pBool("true 启用 / false 停用"),
                ),
            ),
        ) { args ->
            api().cronStatus(args.long("id"), args.bool("status", true))
            ok()
        }

        register(
            "ace_delete_cron",
            "删除计划任务。",
            schema(required = listOf("id"), props = mapOf("id" to pInt("任务 ID"))),
        ) { args ->
            api().deleteCron(args.long("id"))
            ok()
        }

        // ---------- 数据库 ----------

        register(
            "ace_list_databases",
            "列出数据库。type 可选 mysql / postgresql 等，留空返回全部。",
            schema(
                props = mapOf(
                    "type" to pStr("数据库类型"),
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 300, min = 1),
                ),
            ),
        ) { args ->
            encode(api().databases(args.strOrNull("type"), args.int("page", 1), args.int("limit", 300)))
        }

        register(
            "ace_create_database",
            "创建数据库。body 直接传给面板，常见字段：server_id、name、type、username、password、permission。",
            schema(required = listOf("body"), props = mapOf("body" to pObj("数据库创建参数对象"))),
        ) { args ->
            api().createDatabase(args.obj("body")) ?: JsonNull
        }

        register(
            "ace_delete_database",
            "删除数据库。",
            schema(
                required = listOf("serverId", "name"),
                props = mapOf(
                    "serverId" to pInt("数据库服务器 ID"),
                    "name" to pStr("数据库名"),
                ),
            ),
        ) { args ->
            api().deleteDatabase(args.long("serverId"), args.str("name"))
            ok()
        }

        // ---------- 应用 ----------

        register(
            "ace_list_apps",
            "列出应用。installedOnly=true 只看已安装。",
            schema(
                props = mapOf(
                    "category" to pStr("分类"),
                    "query" to pStr("搜索关键字"),
                    "installedOnly" to pBool("只看已安装", default = false),
                ),
            ),
        ) { args ->
            encode(api().appList(args.strOrNull("category"), args.strOrNull("query"), args.bool("installedOnly", false)))
        }

        register(
            "ace_install_app",
            "安装应用。",
            schema(
                required = listOf("slug"),
                props = mapOf(
                    "slug" to pStr("应用标识，例如 mysql"),
                    "channel" to pStr("安装通道，默认 default", default = "default"),
                ),
            ),
        ) { args ->
            api().appInstall(args.str("slug"), args.strOrNull("channel") ?: "default")
            ok()
        }

        register(
            "ace_uninstall_app",
            "卸载应用。",
            schema(required = listOf("slug"), props = mapOf("slug" to pStr("应用标识"))),
        ) { args ->
            api().appUninstall(args.str("slug"))
            ok()
        }

        // ---------- 备份 ----------

        register(
            "ace_list_backups",
            "列出某类备份。type 例如 website / database / app。",
            schema(
                required = listOf("type"),
                props = mapOf(
                    "type" to pStr("备份类型，例如 website"),
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 100, min = 1),
                ),
            ),
        ) { args ->
            encode(api().backups(args.str("type"), args.int("page", 1), args.int("limit", 100)))
        }

        register(
            "ace_create_backup",
            "创建备份。",
            schema(
                required = listOf("type", "target"),
                props = mapOf(
                    "type" to pStr("备份类型，例如 website"),
                    "target" to pStr("备份目标名称（网站名/数据库名等）"),
                    "storage" to pInt("备份存储 ID，0 表示默认", default = 0),
                ),
            ),
        ) { args ->
            api().createBackup(args.str("type"), args.str("target"), args.int("storage", 0))
            ok()
        }

        register(
            "ace_delete_backup",
            "删除备份文件。",
            schema(
                required = listOf("type", "file"),
                props = mapOf(
                    "type" to pStr("备份类型"),
                    "file" to pStr("备份文件名"),
                ),
            ),
        ) { args ->
            api().deleteBackup(args.str("type"), args.str("file"))
            ok()
        }

        // ---------- 证书 ----------

        register(
            "ace_list_certs",
            "列出证书。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 100, min = 1),
                ),
            ),
        ) { args ->
            encode(api().certs(args.int("page", 1), args.int("limit", 100)))
        }

        register(
            "ace_create_cert",
            "创建证书申请。type 例如 P256 / RSA2048；启用自动续期请传 autoRenewal=true。",
            schema(
                required = listOf("domains"),
                props = mapOf(
                    "domains" to pStrArray("域名列表"),
                    "type" to pStr("密钥类型", default = "P256"),
                    "autoRenewal" to pBool("是否自动续期", default = true),
                    "accountId" to pInt("ACME 账户 ID，0 表示默认", default = 0),
                    "dnsId" to pInt("DNS 账户 ID，0 表示使用 HTTP 验证", default = 0),
                ),
            ),
        ) { args ->
            api().createCert(
                domains = args.stringList("domains"),
                type = args.strOrNull("type") ?: "P256",
                autoRenewal = args.bool("autoRenewal", true),
                accountId = args.long("accountId", 0L),
                dnsId = args.long("dnsId", 0L),
            ) ?: JsonNull
        }

        register(
            "ace_renew_cert",
            "续签证书。",
            schema(required = listOf("id"), props = mapOf("id" to pInt("证书 ID"))),
        ) { args ->
            api().certRenew(args.long("id"))
            ok()
        }

        register(
            "ace_delete_cert",
            "删除证书。",
            schema(required = listOf("id"), props = mapOf("id" to pInt("证书 ID"))),
        ) { args ->
            api().deleteCert(args.long("id"))
            ok()
        }

        // ---------- 防火墙 ----------

        register("ace_firewall_status", "查看防火墙状态。", schema()) {
            api().firewallStatus() ?: JsonNull
        }

        register(
            "ace_list_firewall_rules",
            "列出防火墙端口规则。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 200, min = 1),
                ),
            ),
        ) { args ->
            encode(api().firewallRules(args.int("page", 1), args.int("limit", 200)))
        }

        register(
            "ace_create_firewall_rule",
            "新增防火墙端口规则。",
            schema(
                required = listOf("portStart", "portEnd", "protocol", "strategy"),
                props = mapOf(
                    "portStart" to pInt("起始端口"),
                    "portEnd" to pInt("结束端口（单端口与起始相同）"),
                    "protocol" to pStr("协议", enum = listOf("tcp", "udp", "tcp/udp")),
                    "strategy" to pStr("策略", enum = listOf("accept", "drop")),
                    "direction" to pStr("方向", enum = listOf("in", "out"), default = "in"),
                    "family" to pStr("IP 族", enum = listOf("ipv4", "ipv6"), default = "ipv4"),
                    "address" to pStr("限制来源地址，留空表示任意"),
                ),
            ),
        ) { args ->
            api().createFirewallRule(
                portStart = args.int("portStart"),
                portEnd = args.int("portEnd"),
                protocol = args.str("protocol"),
                strategy = args.str("strategy"),
                direction = args.strOrNull("direction") ?: "in",
                family = args.strOrNull("family") ?: "ipv4",
                address = args.strOrNull("address") ?: "",
            )
            ok()
        }

        // ---------- 进程 ----------

        register(
            "ace_list_processes",
            "列出进程，可按 CPU/内存排序。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 50, min = 1),
                    "keyword" to pStr("进程名关键字"),
                    "sort" to pStr("排序字段", default = "cpu"),
                    "order" to pStr("排序方向", enum = listOf("asc", "desc"), default = "desc"),
                ),
            ),
        ) { args ->
            encode(
                api().processes(
                    page = args.int("page", 1),
                    limit = args.int("limit", 50),
                    keyword = args.strOrNull("keyword") ?: "",
                    sort = args.strOrNull("sort") ?: "cpu",
                    order = args.strOrNull("order") ?: "desc",
                ),
            )
        }

        register(
            "ace_kill_process",
            "结束进程。",
            schema(required = listOf("pid"), props = mapOf("pid" to pInt("进程 PID"))),
        ) { args ->
            api().killProcess(args.long("pid"))
            ok()
        }

        // ---------- 日志 ----------

        register(
            "ace_list_logs",
            "读取面板/系统日志。type 例如 app、system、ssh。",
            schema(
                props = mapOf(
                    "type" to pStr("日志类型", default = "app"),
                    "limit" to pInt("条数", default = 200, min = 1),
                    "date" to pStr("日期，格式 yyyy-mm-dd"),
                ),
            ),
        ) { args ->
            encode(api().logs(args.strOrNull("type") ?: "app", args.int("limit", 200), args.strOrNull("date")))
        }

        // ---------- 任务 ----------

        register(
            "ace_list_tasks",
            "列出后台任务（安装、备份等异步任务）。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 100, min = 1),
                ),
            ),
        ) { args ->
            encode(api().tasks(args.int("page", 1), args.int("limit", 100)))
        }

        register(
            "ace_cancel_task",
            "取消后台任务。",
            schema(required = listOf("id"), props = mapOf("id" to pInt("任务 ID"))),
        ) { args ->
            api().cancelTask(args.long("id"))
            ok()
        }

        // ---------- 用户 ----------

        register(
            "ace_list_users",
            "列出面板用户。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 100, min = 1),
                ),
            ),
        ) { args ->
            encode(api().users(args.int("page", 1), args.int("limit", 100)))
        }

        register(
            "ace_create_user",
            "创建面板用户。",
            schema(
                required = listOf("username", "password", "email"),
                props = mapOf(
                    "username" to pStr("用户名"),
                    "password" to pStr("密码"),
                    "email" to pStr("邮箱"),
                ),
            ),
        ) { args ->
            api().createUser(args.str("username"), args.str("password"), args.str("email"))
            ok()
        }

        // ---------- SSH 主机 / 项目 ----------

        register(
            "ace_list_ssh_hosts",
            "列出面板里保存的 SSH 主机。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 100, min = 1),
                ),
            ),
        ) { args ->
            encode(api().sshHosts(args.int("page", 1), args.int("limit", 100)))
        }

        register(
            "ace_list_projects",
            "列出项目。",
            schema(
                props = mapOf(
                    "page" to pInt("页码", default = 1, min = 1),
                    "limit" to pInt("每页条数", default = 100, min = 1),
                ),
            ),
        ) { args ->
            encode(api().projects(args.int("page", 1), args.int("limit", 100)))
        }

        return result
    }

    // ---------------------------------------------------------------- 辅助

    private fun api(): AceApi = McpBridgeHost.store?.api
        ?: error("客户端尚未登录面板：请先在 AcePanel 客户端里登录目标服务器，再调用本工具。")

    private fun ok(): JsonElement = buildJsonObject { put("ok", true) }

    private inline fun <reified T> encode(value: T): JsonElement = rpcJson.encodeToJsonElement(value)

    private fun JsonObject.str(key: String, default: String? = null): String =
        strOrNull(key) ?: default ?: error("缺少参数：$key")

    private fun JsonObject.strOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String, default: Long? = null): Long =
        strOrNull(key)?.toLongOrNull() ?: default ?: error("缺少参数：$key")

    private fun JsonObject.int(key: String, default: Int? = null): Int =
        strOrNull(key)?.toIntOrNull() ?: default ?: error("缺少参数：$key")

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        strOrNull(key)?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.stringList(key: String): List<String> {
        val array = this[key] as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: error("参数 $key 必须是 JSON 对象")

    private fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.pairs(key: String): List<Pair<String, String>> {
        val array = this[key] as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val source = obj.strOrNull("source") ?: return@mapNotNull null
            val target = obj.strOrNull("target") ?: return@mapNotNull null
            source to target
        }
    }

    private fun schema(
        required: List<String> = emptyList(),
        props: Map<String, JsonElement> = emptyMap(),
    ): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { props.forEach { (key, value) -> put(key, value) } })
        if (required.isNotEmpty()) {
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
        }
        put("additionalProperties", true)
    }

    private fun pStr(description: String, enum: List<String>? = null, default: String? = null): JsonObject =
        buildJsonObject {
            put("type", "string")
            put("description", description)
            if (enum != null) put("enum", JsonArray(enum.map { JsonPrimitive(it) }))
            if (default != null) put("default", default)
        }

    private fun pInt(description: String, default: Int? = null, min: Int? = null): JsonObject =
        buildJsonObject {
            put("type", "integer")
            put("description", description)
            if (default != null) put("default", default)
            if (min != null) put("minimum", min)
        }

    private fun pBool(description: String, default: Boolean? = null): JsonObject =
        buildJsonObject {
            put("type", "boolean")
            put("description", description)
            if (default != null) put("default", default)
        }

    private fun pStrArray(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", buildJsonObject { put("type", "string") })
    }

    private fun pObj(description: String): JsonObject = buildJsonObject {
        put("type", "object")
        put("description", description)
        put("additionalProperties", true)
    }

    private fun pPairArray(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put(
            "items",
            buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put("source", buildJsonObject { put("type", "string") })
                        put("target", buildJsonObject { put("type", "string") })
                    },
                )
                put("required", JsonArray(listOf(JsonPrimitive("source"), JsonPrimitive("target"))))
            },
        )
    }

    private data class McpTool(
        val name: String,
        val description: String,
        val inputSchema: JsonObject,
        val handler: suspend (JsonObject) -> JsonElement,
    )

    private companion object {
        val rpcJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
        val prettyJson = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
