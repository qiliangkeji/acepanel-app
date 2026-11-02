package com.acepanel.client.platform

/** iOS 暂不实现自动登录；接口保持 no-op，避免影响共享代码编译。 */
actual object CredentialVault {
    actual fun save(profileId: String, password: String) = Unit
    actual fun load(profileId: String): String? = null
    actual fun clear(profileId: String) = Unit
}
