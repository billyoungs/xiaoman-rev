package dev.xiaoman.app.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.settings.McpSettingsStore
import dev.xiaoman.mcp.CustomMcpServer
import dev.xiaoman.mcp.McpSelection
import dev.xiaoman.mcp.ServerStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class McpScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun testConnectionShowsAResultUnderEachService() {
        val store = McpSettingsStore(
            context.getSharedPreferences("mcp-ui-plain", Context.MODE_PRIVATE),
            context.getSharedPreferences("mcp-ui-secret", Context.MODE_PRIVATE),
        )
        store.save(
            McpSelection(
                enabled = setOf("amap"),
                keys = mapOf("AMAP_MAPS_API_KEY" to "amap-key-0123456789"),
                custom = listOf(CustomMcpServer("我的服务", "https://mcp.example.com/mcp")),
            )
        )
        compose.setContent {
            MaterialTheme {
                McpScreen(
                    AppGraph(context),
                    store = store,
                    testConnection = {
                        listOf(
                            ServerStatus("amap", "高德地图", connected = true, toolCount = 7, error = null),
                            ServerStatus("custom-1", "我的服务", connected = false, toolCount = 0, error = "连接超时"),
                        )
                    },
                )
            }
        }

        compose.onNodeWithText("测试成功").assertDoesNotExist()
        compose.onNodeWithText("测试失败").assertDoesNotExist()

        compose.onNodeWithText("测试连接").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("测试成功").assertExists()
        compose.onNodeWithText("已连接，7 个工具").assertExists()
        compose.onNodeWithText("测试失败").assertExists()
        compose.onNodeWithText("连接失败：连接超时").assertExists()
    }
}
