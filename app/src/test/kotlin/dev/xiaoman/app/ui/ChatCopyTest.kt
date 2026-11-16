package dev.xiaoman.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Magnifier
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import dev.xiaoman.app.session.ChatItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Robolectric's magnifier owns no real Surface, so dismissing the selection lens throws there. */
@Implements(Magnifier::class)
class ShadowMagnifier {
    @Implementation fun dismiss() {}
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], shadows = [ShadowMagnifier::class])
class ChatCopyTest {
    @get:Rule val compose = createComposeRule()

    private fun clipboardText(): String? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip ?: return null
        return if (clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context).toString()
    }

    @Test fun replyCopyButtonPutsTheRenderedTextOnTheClipboard() {
        compose.setContent { MaterialTheme { ChatMessage(ChatItem.Assistant(1, "- **合计**：**2.40元**\n尚未付款")) } }
        compose.onNodeWithText("复制").performClick()
        compose.waitForIdle()
        assertEquals("• 合计：2.40元\n尚未付款", clipboardText())
        compose.onNodeWithText("已复制").assertExists()
    }

    @Test fun userCopyButtonKeepsTheMessageVerbatim() {
        compose.setContent { MaterialTheme { ChatMessage(ChatItem.User(2, "帮我买 **A5** 笔记本")) } }
        compose.onNodeWithText("复制").performClick()
        compose.waitForIdle()
        assertEquals("帮我买 **A5** 笔记本", clipboardText())
    }

    @Test fun failureCardIsCopyableForBugReports() {
        compose.setContent { MaterialTheme { ChatMessage(ChatItem.Error(3, "服务器 mcp 请求超时")) } }
        compose.onNodeWithText("复制").performClick()
        compose.waitForIdle()
        assertEquals("服务器 mcp 请求超时", clipboardText())
    }

    @Test fun processLogIsCopyableOnceExpanded() {
        compose.setContent {
            MaterialTheme { ProcessCard(listOf(ChatItem.Progress(1, "1. 搜索笔记本", true), ChatItem.Progress(2, "2. 打开详情", false))) }
        }
        compose.onNodeWithText("处理过程 · 2条记录").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("复制").performClick()
        compose.waitForIdle()
        assertEquals("1. 搜索笔记本\n2. 打开详情", clipboardText())
    }

    /** Selection handles are the observable proof: bare text raises none, a chat reply raises them. */
    @Test fun longPressRaisesSelectionHandlesOnAReplyButNotOnBareText() {
        compose.setContent {
            MaterialTheme {
                Column {
                    Text("普通文本不可选中")
                    ChatMessage(ChatItem.Assistant(4, "这句话可以长按选中"))
                }
            }
        }
        compose.onNodeWithText("普通文本不可选中").performTouchInput { longClick() }
        compose.waitForIdle()
        val bare = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size

        compose.onNodeWithText("这句话可以长按选中").performTouchInput { longClick() }
        compose.waitForIdle()
        val reply = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size

        assertTrue(reply > bare, "长按回复没有弹出选择手柄：普通文本=$bare 回复=$reply")
    }
}