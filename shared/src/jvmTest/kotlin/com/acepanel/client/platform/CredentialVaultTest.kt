package com.acepanel.client.platform

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialVaultTest {

    private val tempDir = Files.createTempDirectory("acepanel-cred").toFile()
    private val previousDir = System.getProperty("acepanel.data.dir")

    @Test
    fun saveLoadClearRoundTrip() {
        System.setProperty("acepanel.data.dir", tempDir.absolutePath)

        CredentialVault.save("profile-1", "p@ssw0rd-测试")
        assertEquals("p@ssw0rd-测试", CredentialVault.load("profile-1"))

        CredentialVault.save("profile-1", "new-secret")
        assertEquals("new-secret", CredentialVault.load("profile-1"))

        CredentialVault.clear("profile-1")
        assertNull(CredentialVault.load("profile-1"))
    }

    @AfterTest
    fun cleanup() {
        if (previousDir == null) {
            System.clearProperty("acepanel.data.dir")
        } else {
            System.setProperty("acepanel.data.dir", previousDir)
        }
        tempDir.deleteRecursively()
    }
}
