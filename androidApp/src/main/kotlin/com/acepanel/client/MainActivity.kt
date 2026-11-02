package com.acepanel.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.acepanel.client.mcp.McpBridge
import com.acepanel.client.platform.AndroidAppContext
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidAppContext.context = applicationContext
        // 客户端内置 MCP 桥：App 进程存活期间监听回环端口，复用当前登录会话。
        thread(name = "acepanel-mcp-boot", isDaemon = true) {
            McpBridge.startDefault(File(filesDir, "mcp-token.txt"))
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
