package com.acepanel.client.data

/**
 * 首页「常用功能」可配置快捷入口。
 * 只保存枚举名，图标与点击行为由 UI 层映射，避免把 Compose 类型带入持久化层。
 */
enum class QuickActionKey(
    val label: String,
    val page: PageKey? = null,
    val tab: RootTab? = null,
) {
    TERMINAL("终端", tab = RootTab.TERMINAL),
    FILES("文件", page = PageKey.FILES),
    WEBSITES("网站", page = PageKey.WEBSITES),
    CONTAINERS("容器", page = PageKey.CONTAINERS),
    DATABASES("数据库", page = PageKey.DATABASES),
    APPS("应用商店", page = PageKey.APPS),
    CRON("计划任务", page = PageKey.CRON),
    SECURITY("安全", page = PageKey.SECURITY),
    MONITOR("监控", tab = RootTab.MONITOR),
    BACKUPS("备份", page = PageKey.BACKUPS),
    CERTS("SSL 证书", page = PageKey.CERTS),
    ENVIRONMENTS("运行环境", page = PageKey.ENVIRONMENTS),
    LOGS("日志", page = PageKey.LOGS),
    PROJECTS("项目", page = PageKey.PROJECTS),
    TOOLBOX("工具箱", page = PageKey.TOOLBOX),
    ALERTS("告警记录", page = PageKey.ALERTS),
    ACCOUNT_CENTER("用户与安全", page = PageKey.ACCOUNT_CENTER),
    NOTIFY_CENTER("通知与告警", page = PageKey.NOTIFY_CENTER),
    DATA_CENTER("数据服务", page = PageKey.DATA_CENTER),
    OPS_CENTER("自动化与进程", page = PageKey.OPS_CENTER),
    API_CENTER("接口中心", page = PageKey.API_CENTER),
    FEATURE_CENTER("全部功能", page = PageKey.FEATURE_CENTER),
    ;

    companion object {
        val defaults: List<QuickActionKey> = listOf(TERMINAL, FILES, WEBSITES, CONTAINERS)
    }
}
