package com.acepanel.client.platform

/**
 * 面板登录凭据的本地存储。
 *
 * 只保存密码，地址与账号本来就在 ServerProfile 里。密码不会明文落盘：
 * JVM/Android 上用 AES-GCM 加密后写入 [appDataDir]，密钥单独存放并收紧文件权限。
 *
 * 注意：这是「本地加密」而不是系统钥匙串。能读到当前用户文件的人理论上也能拿到密钥；
 * 对个人面板客户端足够，但不适合多人共用的机器。iOS 暂不实现（返回 null）。
 */
expect object CredentialVault {
    fun save(profileId: String, password: String)
    fun load(profileId: String): String?
    fun clear(profileId: String)
}
