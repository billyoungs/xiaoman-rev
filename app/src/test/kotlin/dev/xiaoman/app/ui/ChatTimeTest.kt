package dev.xiaoman.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import dev.xiaoman.app.session.ChatItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Double tapping a bubble shows its time, double tapping again hides it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], shadows = [ShadowMagnifier::class])
class ChatTimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun doubleTapTogglesTimestampBelowTheBubble() {
        val at = 1_760_000_000_000L
        compose.setContent { MaterialTheme { ChatMessage(ChatItem.Assistant(7, "你好", at)) } }
        val time = chatTime(at)

        compose.onNodeWithText(time).assertDoesNotExist()
        compose.onNodeWithText("你好").performTouchInput { doubleClick() }
        compose.waitForIdle()
        compose.onNodeWithText(time).assertExists()

        compose.onNodeWithText("你好").performTouchInput { doubleClick() }
        compose.waitForIdle()
        compose.onNodeWithText(time).assertDoesNotExist()
    }
}
