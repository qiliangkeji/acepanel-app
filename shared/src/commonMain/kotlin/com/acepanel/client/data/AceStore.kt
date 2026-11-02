package com.acepanel.client.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.acepanel.client.platform.CredentialVault
import com.acepanel.client.platform.PlatformSettings
import com.acepanel.client.platform.currentTimeMillis
import com.acepanel.client.platform.debugAutoLogin
import com.acepanel.client.platform.debugStartRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ServerProfile(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val entrance: String = "",
    val userAgent: String = "",
    val username: String = "",
    val kind: String = "panel",
    val group: String = "",
    val remark: String = "",
    val hostname: String = "",
    val osName: String = "",
    val panelVersion: String = "",
    val latencyMs: Long? = null,
    val online: Boolean = false,
    val lastConnectedAt: Long = 0,
)

enum class RootTab(val label: String) {
    OVERVIEW("总览"),
    SERVERS("服务器"),
    TERMINAL("终端"),
    MONITOR("监控"),
    PROFILE("我的"),
}

enum class PageKey {
    FEATURE_CENTER,
    APPS,
    WEBSITES,
    DATABASES,
    FILES,
    CONTAINERS,
    SECURITY,
    CRON,
    SETTINGS,
    BACKUPS,
    CERTS,
    ENVIRONMENTS,
    LOGS,
    PROJECTS,
    TOOLBOX,
    ALERTS,
    API_CENTER,
    ACCOUNT_CENTER,
    NOTIFY_CENTER,
    DATA_CENTER,
    OPS_CENTER,
}

data class SshTerminalTarget(
    val hostId: Long,
    val name: String,
    val subtitle: String = "",
    val directConfig: DirectSshConfig? = null,
    // 面板本机的 PTY 终端（/api/ws/pty），文件系统"终端"入口用它直接进入当前目录
    val pty: Boolean = false,
    val initialCommand: String = "",
    // PTY 会话的工作目录，只用于标题与会话信息展示
    val ptyDir: String = "",
)

/**
 * 文件系统里"复制 / 移动"的暂存区。
 * 与 Web 控制台一致：先标记源文件，进入目标目录后粘贴，move=true 表示剪切。
 */
data class FileClipboard(val move: Boolean, val paths: List<String>)

sealed interface AuthState {
    data object Booting : AuthState
    data object Login : AuthState
    data object Ready : AuthState
}

enum class ThemeMode { System, Light, Dark }

data class ToastMessage(val text: String, val isError: Boolean = false, val id: Long = 0)

private val StoreJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

class AceStore(
    private val settings: PlatformSettings = PlatformSettings(),
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var profiles by mutableStateOf<List<ServerProfile>>(emptyList())
        private set
    var activeProfileId by mutableStateOf<String?>(null)
        private set
    var authState by mutableStateOf<AuthState>(AuthState.Booting)
        private set
    var user by mutableStateOf<UserInfo?>(null)
        private set
    var panelInfo by mutableStateOf(PanelInfo())
        private set
    var systemInfo by mutableStateOf<SystemInfo?>(null)
        private set
    var currentInfo by mutableStateOf<CurrentInfo?>(null)
        private set
    var countInfo by mutableStateOf(CountInfo())
        private set
    var sshHosts by mutableStateOf<List<SshHost>>(emptyList())
        private set

    var selectedTab by mutableStateOf(RootTab.OVERVIEW)
    val pageStack = mutableStateListOf<PageKey>()
    /**
     * 页面内部的返回拦截器。
     *
     * 文件系统这类页面在页面内部还有目录层级，返回键应该先退到上一级目录，
     * 只有退到根目录之后才让全局把整个页面出栈。处理器返回 true 表示这次返回已被页面消费。
     */
    var pageBackHandler by mutableStateOf<(() -> Boolean)?>(null)
    /** 文件系统长按"复制 / 移动"后待粘贴的源路径。 */
    var fileClipboard by mutableStateOf<FileClipboard?>(null)
    var showAddServer by mutableStateOf(false)
        private set
    var showDirectSshForm by mutableStateOf(false)
        private set
    var sshTerminal by mutableStateOf<SshTerminalTarget?>(null)
        private set

    var themeMode by mutableStateOf(ThemeMode.System)
        private set
    var notificationsEnabled by mutableStateOf(true)
        private set
    var autoReconnect by mutableStateOf(true)
        private set
    var quickActions by mutableStateOf(QuickActionKey.defaults)
        private set
    var refreshTick by mutableStateOf(0)
        private set

    var toast by mutableStateOf<ToastMessage?>(null)
        private set
    var bootMessage by mutableStateOf("正在连接面板…")

    var loginError by mutableStateOf<String?>(null)
    var loginLoading by mutableStateOf(false)
        private set
    var loginAddress by mutableStateOf("")
    var loginUsername by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var loginRemember by mutableStateOf(true)
    var loginTwoFaRequired by mutableStateOf(false)
    var loginPassCode by mutableStateOf("")
    var loginCaptchaRequired by mutableStateOf(false)
    var loginCaptchaImage by mutableStateOf("")
    var loginCaptchaCode by mutableStateOf("")

    var appsCache by mutableStateOf<List<HomeApp>>(emptyList())
        private set
    var appsTotal by mutableStateOf(0)
        private set
    var installedEnv by mutableStateOf<InstalledEnvironment?>(null)
        private set
    var lastDashboardRefresh by mutableStateOf(0L)
        private set
    var dashboardLoading by mutableStateOf(false)
        private set
    var dashboardError by mutableStateOf<String?>(null)
        private set
    /** 实时负载（CPU / 内存 / 网卡）单独的错误，用于解释「为什么是 0」。 */
    var liveError by mutableStateOf<String?>(null)
        private set

    var api: AceApi? = null
        private set

    private var toastJob: Job? = null
    private var dashboardJob: Job? = null
    private var pollJob: Job? = null
    private var autoLoginJob: Job? = null

    val activeProfile: ServerProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }

    init {
        profiles = loadProfiles()
        activeProfileId = settings.getString("active_profile")
        themeMode = runSafely {
            ThemeMode.valueOf(settings.getString("theme_mode") ?: "System")
        }.getOrDefault(ThemeMode.System)
        notificationsEnabled = settings.getBoolean("notifications", true)
        autoReconnect = settings.getBoolean("auto_reconnect", true)
        quickActions = loadQuickActions()
        val profile = activeProfile
        if (profile != null) {
            loginAddress = profile.baseUrl + profile.entrance
            loginUsername = profile.username
        }
        boot()
    }

    private fun loadProfiles(): List<ServerProfile> {
        val raw = settings.getString("profiles") ?: return emptyList()
        return runSafely { StoreJson.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
    }

    private fun persistProfiles() {
        settings.putString("profiles", StoreJson.encodeToString(profiles))
    }

    private fun cookiesKey(id: String) = "cookies_$id"

    private fun loadCookies(id: String): Map<String, String> {
        val raw = settings.getString(cookiesKey(id)) ?: return emptyMap()
        return runSafely { StoreJson.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    private fun saveCookies(id: String, cookies: Map<String, String>, remember: Boolean = true) {
        if (remember && cookies.isNotEmpty()) {
            settings.putString(cookiesKey(id), StoreJson.encodeToString(cookies))
        } else {
            settings.remove(cookiesKey(id))
        }
    }

    private fun clearCookies(id: String) {
        settings.remove(cookiesKey(id))
    }

    private fun setProfile(value: ServerProfile) {
        val index = profiles.indexOfFirst { it.id == value.id }
        profiles = if (index >= 0) {
            profiles.toMutableList().also { it[index] = value }
        } else {
            profiles + value
        }
        persistProfiles()
    }

    /**
     * 会话过期后尝试用本地保存的凭据静默重登。
     * 账号开启了两步验证/验证码时会失败，调用方需要回退到登录页。
     */
    private suspend fun tryStoredLogin(profile: ServerProfile, client: AceApi): Boolean {
        if (profile.username.isBlank()) return false
        val password = runSafely { CredentialVault.load(profile.id) }.getOrNull()
        if (password.isNullOrBlank()) return false
        return runSafely {
            client.login(username = profile.username, password = password)
            true
        }.getOrDefault(false)
    }

    private fun newClient(profile: ServerProfile, cookies: Map<String, String>): AceApi = AceApi(
        baseUrl = profile.baseUrl,
        initialCookies = cookies,
        onCookiesChanged = { updated -> saveCookies(profile.id, updated, true) },
        onSessionExpired = { scope.launch { handleSessionExpired() } },
        entrance = profile.entrance,
        userAgent = profile.userAgent,
    )

    fun boot() {
        scope.launch {
            delay(420)
            val debugLogin = debugAutoLogin()
            val profile = activeProfile
            if (profile == null && debugLogin != null) {
                login(debugLogin.first, debugLogin.second, debugLogin.third)
                return@launch
            }
            if (profile == null) {
                authState = AuthState.Login
                return@launch
            }
            bootMessage = "正在恢复 ${profile.name.ifBlank { profile.baseUrl }} 的会话…"
            val cookies = loadCookies(profile.id)
            val hasStoredPassword = !runSafely { CredentialVault.load(profile.id) }.getOrNull().isNullOrBlank()
            if (cookies.isEmpty() && !hasStoredPassword) {
                authState = AuthState.Login
                return@launch
            }
            val client = newClient(profile, cookies)
            api = client
            scope.launch {
                val result = runSafely { client.visitEntrance(); client.isLogin() }
                if (result.getOrDefault(false)) {
                    onAuthenticated(client, profile, remember = true)
                } else if (tryStoredLogin(profile, client)) {
                    // Cookie 过期但本地有凭据：静默重登，用户无感。
                    onAuthenticated(client, profile, remember = true)
                } else {
                    client.close()
                    api = null
                    val debugLogin = debugAutoLogin()
                    if (debugLogin != null) {
                        // 调试模式下会话失效（本地 mock 重启就会这样）时直接重登，
                        // 否则全站走查会一直停在登录页，page: 路由全部失效。
                        login(debugLogin.first, debugLogin.second, debugLogin.third)
                    } else {
                        authState = AuthState.Login
                        loginError = if (cookies.isEmpty()) {
                            "请登录面板"
                        } else {
                            result.exceptionOrNull()?.let { friendlyError(it) } ?: "会话已失效，请重新登录"
                        }
                    }
                }
            }
        }
    }

    fun login(
        address: String = loginAddress,
        username: String = loginUsername,
        password: String = loginPassword,
        remember: Boolean = loginRemember,
        passCode: String = loginPassCode,
        captchaCode: String = loginCaptchaCode,
        onResult: ((Boolean, String?) -> Unit)? = null,
    ) {
        if (loginLoading) return
        val cleanAddress = address.trim()
        if (cleanAddress.isBlank()) {
            loginError = "请输入面板地址"
            onResult?.invoke(false, loginError)
            return
        }
        if (username.isBlank() || password.isBlank()) {
            loginError = "请输入账号和密码"
            onResult?.invoke(false, loginError)
            return
        }
        loginLoading = true
        loginError = null
        scope.launch {
            val (normalized, entrance) = splitPanelAddress(cleanAddress)
            val existing = profiles.firstOrNull { it.baseUrl.trimEnd('/') == normalized && it.entrance == entrance }
            val tempId = existing?.id ?: ("p-" + (normalized + entrance).hashCode().toString())
            val previous = api
            val cookies = existing?.let { loadCookies(it.id) }
                ?.takeIf { it.isNotEmpty() }
                ?: previous?.takeIf { it.baseUrl == normalized }?.cookieSnapshot
                ?: emptyMap()
            val client = AceApi(
                normalized,
                cookies,
                onCookiesChanged = { updated -> saveCookies(tempId, updated, remember) },
                entrance = entrance,
                userAgent = existing?.userAgent ?: "",
            )
            try {
                val twoFa = runSafely { client.isTwoFa(username) }.getOrDefault(false)
                loginTwoFaRequired = twoFa
                if (twoFa && passCode.isBlank()) {
                    loginError = "该账号开启了两步验证，请输入动态验证码"
                    loginLoading = false
                    api = client
                    onResult?.invoke(false, loginError)
                    return@launch
                }
                val info = client.login(
                    username = username,
                    password = password,
                    passCode = passCode,
                    captchaCode = captchaCode,
                )
                api = client
                val sys = runSafely { client.systemInfo() }.getOrNull()
                val panel = runSafely { client.panelInfo() }.getOrNull() ?: PanelInfo()
                val latency = client.measureLatency()
                val profile = ServerProfile(
                    id = tempId,
                    name = existing?.name?.takeIf { it.isNotBlank() }
                        ?: (sys?.hostname?.takeIf { it.isNotBlank() } ?: hostFromAddress(cleanAddress)),
                    baseUrl = normalized,
                    entrance = entrance,
                    username = username,
                    hostname = sys?.hostname ?: "",
                    osName = sys?.osName ?: "",
                    panelVersion = sys?.panelVersion ?: panel.name,
                    latencyMs = latency,
                    online = true,
                    lastConnectedAt = currentTimeMillis(),
                )
                setProfile(profile)
                activeProfileId = profile.id
                settings.putString("active_profile", profile.id)
                saveCookies(profile.id, client.cookieSnapshot, remember)
                // 记住登录状态时把密码加密存本地，Cookie 过期后可静默重登。
                if (remember && password.isNotBlank()) {
                    runSafely { CredentialVault.save(profile.id, password) }
                }
                user = info
                panelInfo = panel
                systemInfo = sys
                loginPassword = ""
                loginPassCode = ""
                loginCaptchaCode = ""
                loginError = null
                authState = AuthState.Ready
                selectedTab = RootTab.OVERVIEW
                pageStack.clear()
                showAddServer = false
                previous?.takeIf { it !== client }?.close()
                refreshDashboard(showLoading = true)
                loadSshHosts()
                applyDebugRoute()
                onResult?.invoke(true, null)
            } catch (t: Throwable) {
                previous?.close()
                // 保留本次客户端，验证码/两步验证的会话依赖这里的 Cookie
                api = client
                loginError = friendlyError(t)
                onResult?.invoke(false, loginError)
                refreshCaptcha()
            } finally {
                loginLoading = false
            }
        }
    }

    fun refreshCaptcha() {
        val existing = api
        scope.launch {
            val client = existing ?: runSafely {
                val (base, entrance) = splitPanelAddress(loginAddress)
                AceApi(base, entrance = entrance).also { it.visitEntrance() }
            }.onFailure { return@launch }.getOrNull() ?: return@launch
            runSafely { client.getCaptcha() }.onSuccess {
                loginCaptchaRequired = it.required
                loginCaptchaImage = it.image
            }
            if (existing == null) client.close()
        }
    }

    fun prepareLogin(address: String = loginAddress) {
        loginAddress = address
        val (base, entrance) = splitPanelAddress(address)
        val profile = profiles.firstOrNull { it.baseUrl == base && it.entrance == entrance }
        if (profile != null) {
            loginUsername = profile.username
        }
        loginError = null
        loginTwoFaRequired = false
        loginCaptchaRequired = false
        authState = AuthState.Login
    }

    private suspend fun onAuthenticated(client: AceApi, profile: ServerProfile, remember: Boolean) {
        api = client
        user = runSafely { client.userInfo() }.getOrNull()
        panelInfo = runSafely { client.panelInfo() }.getOrDefault(PanelInfo())
        systemInfo = runSafely { client.systemInfo() }.getOrNull()
        if (user == null) {
            authState = AuthState.Login
            loginError = "无法获取账号信息，请重新登录"
            return
        }
        val latency = client.measureLatency()
        val updated = profile.copy(
            latencyMs = latency,
            online = true,
            panelVersion = systemInfo?.panelVersion ?: profile.panelVersion,
            osName = systemInfo?.osName ?: profile.osName,
            hostname = systemInfo?.hostname ?: profile.hostname,
        )
        setProfile(updated)
        authState = AuthState.Ready
        refreshDashboard(showLoading = true)
        loadSshHosts()
        applyDebugRoute()
    }

    fun applyDebugRoute(route: String? = debugStartRoute()) {
        val route = route ?: return
        when {
            route.startsWith("tab:") -> {
                val key = route.removePrefix("tab:")
                RootTab.entries.firstOrNull { it.name.equals(key, true) }?.let {
                    pageStack.clear()
                    selectedTab = it
                }
            }
            route.startsWith("page:") -> {
                val key = route.removePrefix("page:")
                PageKey.entries.firstOrNull { it.name.equals(key, true) }?.let {
                    pageStack.clear()
                    openPage(it)
                }
            }
            route == "logout" -> signOut()
            route == "home" -> {
                showDirectSshForm = false
                showAddServer = false
                sshTerminal = null
                pageStack.clear()
                selectedTab = RootTab.OVERVIEW
            }
            route.startsWith("theme:") -> {
                when (route.removePrefix("theme:").lowercase()) {
                    "dark" -> setTheme(ThemeMode.Dark)
                    "light" -> setTheme(ThemeMode.Light)
                    else -> setTheme(ThemeMode.System)
                }
            }
            route == "addserver" -> showAddServer = true
            route == "sshclose" -> sshTerminal = null
            route == "directsshform" -> {
                sshTerminal = null
                showDirectSshForm = true
            }
            route.startsWith("directssh:") -> {
                val parts = route.removePrefix("directssh:").split("|")
                if (parts.size >= 3) {
                    openDirectSshTerminal(
                        DirectSshConfig(
                            name = parts.getOrNull(4) ?: parts[0],
                            host = parts[0],
                            port = parts[1].toIntOrNull() ?: 22,
                            username = parts[2],
                            authMethod = "password",
                            password = parts.getOrNull(3) ?: "",
                        ),
                    )
                }
            }
            route.startsWith("ssh:") -> {
                val parts = route.removePrefix("ssh:").split("|")
                val id = parts.firstOrNull()?.toLongOrNull()
                if (id != null) openSshTerminal(id, parts.getOrNull(1) ?: "SSH 终端", parts.getOrNull(2) ?: "")
            }
        }
    }

    fun refreshDashboard(showLoading: Boolean = true) {
        val client = api ?: return
        dashboardJob?.cancel()
        dashboardJob = scope.launch {
            if (showLoading) lastDashboardRefresh = 0
            dashboardLoading = true
            dashboardError = null
            try {
                val sys = client.systemInfo()
                val counts = client.countInfo()
                val current = client.currentInfo()
                val homeApps = runSafely { client.homeApps() }.getOrDefault(emptyList())
                val installedApps = if (homeApps.isEmpty()) {
                    runSafely { client.appList(installedOnly = true).items }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val apps = homeApps.ifEmpty { installedApps }
                val env = runSafely { client.installedEnvironment() }.getOrNull()
                val appTotal = runSafely { client.appList().total.toInt() }.getOrDefault(apps.size)
                systemInfo = sys
                countInfo = counts
                currentInfo = current
                appsCache = apps
                appsTotal = appTotal
                installedEnv = env
                liveError = null
                lastDashboardRefresh = currentTimeMillis()
                activeProfile?.let { p ->
                    setProfile(
                        p.copy(
                            hostname = sys.hostname,
                            osName = sys.osName,
                            panelVersion = sys.panelVersion,
                            online = true,
                        ),
                    )
                }
            } catch (t: Throwable) {
                if (t.isCancellation()) throw t
                dashboardError = friendlyError(t)
                if (t is SessionExpiredException) handleSessionExpired() else toast(friendlyError(t), true)
            } finally {
                dashboardLoading = false
            }
        }
    }

    /**
     * 实时负载轮询。
     *
     * 这里刻意不传 nets / disks 过滤条件：面板在参数为空时会返回全部网卡与磁盘，
     * 由客户端自己过滤虚拟网卡，避免「面板 system_info 里的设备名和 gopsutil 对不上」
     * 时把数据筛成空、界面上看就是 CPU / 吞吐永远是 0。
     * 同时用 pollJob 去重：请求没回来就不再叠加新请求，防止轮询把队列堆满。
     */
    fun pollOverview() {
        val client = api ?: return
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            runSafely { client.currentInfo() }
                .onSuccess {
                    currentInfo = it
                    liveError = null
                }
                .onFailure { liveError = friendlyError(it) }
        }
    }

    fun loadSshHosts() {
        val client = api ?: return
        scope.launch {
            runSafely { client.sshHosts() }.onSuccess { sshHosts = it.items }
        }
    }

    fun openPage(page: PageKey) {
        if (pageStack.lastOrNull() != page) pageStack.add(page)
    }

    fun closePage() {
        if (pageStack.isNotEmpty()) pageStack.removeAt(pageStack.lastIndex)
    }

    fun openAddServer() {
        showAddServer = true
    }

    fun closeAddServer() {
        showAddServer = false
    }

    fun openSshTerminal(hostId: Long, name: String, subtitle: String = "") {
        showDirectSshForm = false
        sshTerminal = SshTerminalTarget(hostId, name, subtitle)
    }

    /** 长按菜单里的"复制 / 移动"：源路径先进暂存区，到目标目录后粘贴。 */
    fun markFiles(move: Boolean, paths: List<String>) {
        fileClipboard = if (paths.isEmpty()) null else FileClipboard(move, paths)
    }

    fun clearFileClipboard() {
        fileClipboard = null
    }

    /** 在面板本机终端里打开某个目录，等价于 Web 控制台的"终端"入口。 */
    fun openFileTerminal(directory: String) {
        showDirectSshForm = false
        val host = systemInfo?.hostname ?: activeProfile?.name ?: "面板主机"
        sshTerminal = SshTerminalTarget(
            hostId = 0L,
            name = "终端",
            subtitle = "$host · $directory",
            pty = true,
            initialCommand = "cd '" + directory.replace("'", "'\\''") + "'",
            ptyDir = directory,
        )
    }

    fun openDirectSshForm() {
        showDirectSshForm = true
    }

    fun closeDirectSshForm() {
        showDirectSshForm = false
    }

    fun openDirectSshTerminal(config: DirectSshConfig) {
        showDirectSshForm = false
        sshTerminal = SshTerminalTarget(
            hostId = 0L,
            name = config.name.ifBlank { config.host },
            subtitle = "${config.username}@${config.host}",
            directConfig = config,
        )
    }

    fun closeSshTerminal() {
        sshTerminal = null
    }

    fun switchProfile(profile: ServerProfile) {
        if (profile.id == activeProfileId && authState == AuthState.Ready) {
            selectedTab = RootTab.OVERVIEW
            pageStack.clear()
            return
        }
        scope.launch {
            api?.close()
            api = null
            activeProfileId = profile.id
            settings.putString("active_profile", profile.id)
            loginAddress = profile.baseUrl + profile.entrance
            loginUsername = profile.username
            val cookies = loadCookies(profile.id)
            val client = newClient(profile, cookies)
            api = client
            val ok = runSafely { client.visitEntrance(); client.isLogin() }.getOrDefault(false)
            if (ok || tryStoredLogin(profile, client)) {
                onAuthenticated(client, profile, remember = true)
                selectedTab = RootTab.OVERVIEW
                pageStack.clear()
            } else {
                client.close()
                api = null
                authState = AuthState.Login
                loginError = "该服务器需要重新登录"
            }
        }
    }

    fun removeProfile(profile: ServerProfile) {
        profiles = profiles.filterNot { it.id == profile.id }
        persistProfiles()
        clearCookies(profile.id)
        runSafely { CredentialVault.clear(profile.id) }
        if (activeProfileId == profile.id) {
            api?.close()
            api = null
            activeProfileId = null
            settings.remove("active_profile")
            user = null
            authState = AuthState.Login
        }
    }

    fun signOut() {
        val client = api
        val profile = activeProfile
        scope.launch {
            autoLoginJob?.cancel()
            autoLoginJob = null
            runCatching { client?.logout() }
            client?.close()
            api = null
            user = null
            systemInfo = null
            currentInfo = null
            countInfo = CountInfo()
            appsCache = emptyList()
            sshHosts = emptyList()
            pageStack.clear()
            selectedTab = RootTab.OVERVIEW
            if (profile != null) {
                clearCookies(profile.id)
                // 主动退出时不要把密码留在本地，否则下次又会被自动登回去。
                runSafely { CredentialVault.clear(profile.id) }
                loginAddress = profile.baseUrl + profile.entrance
                loginUsername = profile.username
            }
            authState = AuthState.Login
        }
    }

    fun addPanelProfile(profile: ServerProfile) {
        setProfile(profile)
        if (activeProfileId == null) {
            activeProfileId = profile.id
            settings.putString("active_profile", profile.id)
        }
    }

    fun addSshHost(
        name: String,
        host: String,
        port: Int,
        user: String,
        authMethod: String,
        password: String,
        key: String,
        passphrase: String,
        remark: String,
        onDone: (Boolean, String) -> Unit,
    ) {
        val client = api
        if (client == null) {
            onDone(false, "请先登录面板")
            return
        }
        scope.launch {
            runSafely {
                client.createSshHost(
                    buildJsonObject {
                        put("name", name)
                        put("host", host)
                        put("port", port)
                        put("user", user)
                        put("auth_method", authMethod)
                        put("password", password)
                        put("key", key)
                        put("passphrase", passphrase)
                        put("remark", remark)
                    },
                )
            }.onSuccess {
                loadSshHosts()
                toast("主机已添加")
                onDone(true, "")
            }.onFailure {
                onDone(false, friendlyError(it))
            }
        }
    }

    fun clearLocalCache() {
        appsCache = emptyList()
        installedEnv = null
        lastDashboardRefresh = 0
        refreshDashboard(showLoading = true)
        toast("本地缓存已清理")
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        settings.putString("theme_mode", mode.name)
    }

    fun setNotifications(enabled: Boolean) {
        notificationsEnabled = enabled
        settings.putBoolean("notifications", enabled)
    }

    fun updateAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        settings.putBoolean("auto_reconnect", enabled)
    }

    private fun loadQuickActions(): List<QuickActionKey> {
        val raw = settings.getString("quick_actions") ?: return QuickActionKey.defaults
        val parsed = raw.split(',')
            .mapNotNull { name -> QuickActionKey.entries.firstOrNull { it.name == name.trim() } }
            .distinct()
        return parsed.takeIf { it.size == 4 } ?: QuickActionKey.defaults
    }

    fun updateQuickActions(actions: List<QuickActionKey>) {
        val clean = actions.distinct().take(4)
        if (clean.size != 4) return
        quickActions = clean
        settings.putString("quick_actions", clean.joinToString(",") { it.name })
    }

    fun openQuickAction(action: QuickActionKey) {
        action.page?.let { openPage(it) }
        action.tab?.let {
            pageStack.clear()
            selectedTab = it
        }
    }

    /** 全局刷新：当前页面通过 refreshTick 重建并重新执行各自的加载逻辑。 */
    fun requestGlobalRefresh() {
        refreshTick += 1
        refreshDashboard(showLoading = false)
        loadSshHosts()
    }

    fun handleSessionExpired() {
        // 自动重登过程中又收到 401 时不要再套娃。
        if (autoLoginJob?.isActive == true) return
        val profile = activeProfile
        val previous = api
        previous?.close()
        api = null
        if (profile == null) {
            authState = AuthState.Login
            loginError = "登录状态已过期，请重新登录"
            return
        }
        clearCookies(profile.id)
        autoLoginJob = scope.launch {
            val client = newClient(profile, emptyMap())
            api = client
            if (tryStoredLogin(profile, client)) {
                loginError = null
                onAuthenticated(client, profile, remember = true)
            } else {
                client.close()
                api = null
                authState = AuthState.Login
                loginError = "登录状态已过期，请重新登录"
            }
        }
    }

    fun toast(text: String, isError: Boolean = false) {
        // 协程取消不是错误：切页面、下拉刷新、连点刷新都会取消上一个请求。
        // 这类 "StandaloneCoroutine was cancelled" 提示对用户毫无意义，直接丢弃。
        if (isError && text.isCancellationMessage()) return
        toast = ToastMessage(text, isError, currentTimeMillis())
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(3200)
            toast = null
        }
    }

    fun clearToast() {
        toast = null
    }

    fun friendlyError(t: Throwable): String = when {
        t.isCancellation() -> "请求已取消"
        t is SessionExpiredException -> t.message ?: "登录状态已过期"
        t is ApiException -> t.message ?: "请求失败"
        else -> when {
            t.message?.contains("timeout", true) == true ||
                t.message?.contains("timed out", true) == true -> "连接超时，请检查服务器地址与网络"
            t.message?.contains("connect", true) == true ||
                t.message?.contains("unreachable", true) == true -> "无法连接面板，请检查地址、端口与 HTTPS 设置"
            t.message?.contains("SSL", true) == true -> "TLS 握手失败，请确认面板地址与协议"
            t.message?.contains("101", true) == true || t.message?.contains("websocket", true) == true ->
                "无法建立 WebSocket 终端连接，请确认面板版本与反向代理已支持 WebSocket"
            else -> t.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
        }
    }

    companion object {
        fun normalize(address: String): String = splitPanelAddress(address).first

        /** 支持直接粘贴带安全入口的地址，例如 https://1.2.3.4:8443/0a2vc7 */
        fun splitPanelAddress(address: String): Pair<String, String> {
            var value = address.trim()
            if (value.isEmpty()) return "" to ""
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://$value"
            }
            val schemeEnd = value.indexOf("://") + 3
            val slash = value.indexOf('/', schemeEnd)
            if (slash < 0) return value.trimEnd('/') to ""
            val base = value.substring(0, slash).trimEnd('/')
            var path = value.substring(slash).trim().trimEnd('/')
            if (path.isBlank() || path == "/login" || path == "/index.html") return base to ""
            return base to (if (path.startsWith("/")) path else "/$path")
        }

        fun hostFromAddress(address: String): String {
            val base = splitPanelAddress(address).first
            val value = base.removePrefix("https://").removePrefix("http://")
            return value.substringBefore('/').substringBefore(':')
        }
    }
}
