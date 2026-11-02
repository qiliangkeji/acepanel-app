package com.acepanel.client.platform

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object CredentialVault {
    private const val STORE_FILE = "credentials.properties"
    private const val KEY_FILE = "credentials.key"
    private const val KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    private val lock = Any()

    actual fun save(profileId: String, password: String) {
        if (profileId.isBlank() || password.isBlank()) return
        synchronized(lock) {
            runCatching {
                val key = loadOrCreateKey()
                val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, iv),
                )
                val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
                val props = loadProps()
                props["$profileId.iv"] = Base64.getEncoder().encodeToString(iv)
                props["$profileId.data"] = Base64.getEncoder().encodeToString(encrypted)
                storeProps(props)
            }.onFailure {
                println("[CredentialVault] save failed: ${it.message}")
            }
        }
    }

    actual fun load(profileId: String): String? = synchronized(lock) {
        runCatching {
            val props = loadProps()
            val ivText = props.getProperty("$profileId.iv") ?: return null
            val dataText = props.getProperty("$profileId.data") ?: return null
            val key = loadOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(ivText)),
            )
            String(cipher.doFinal(Base64.getDecoder().decode(dataText)), Charsets.UTF_8)
        }.getOrNull()
    }

    actual fun clear(profileId: String) {
        synchronized(lock) {
            runCatching {
                val props = loadProps()
                props.remove("$profileId.iv")
                props.remove("$profileId.data")
                storeProps(props)
            }
        }
    }

    private fun dir(): File = File(appDataDir()).also { it.mkdirs() }

    private fun loadProps(): Properties = Properties().apply {
        val file = File(dir(), STORE_FILE)
        if (file.isFile) {
            runCatching { file.inputStream().use { load(it) } }
        }
    }

    private fun storeProps(props: Properties) {
        val file = File(dir(), STORE_FILE)
        file.outputStream().use { props.store(it, null) }
        restrict(file)
    }

    private fun loadOrCreateKey(): ByteArray {
        val file = File(dir(), KEY_FILE)
        if (file.isFile) {
            val existing = file.readBytes()
            if (existing.size == KEY_BYTES) return existing
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        file.writeBytes(key)
        restrict(file)
        return key
    }

    /** 尽量收紧到「仅当前用户可读写」；失败不影响功能。 */
    private fun restrict(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }
}
