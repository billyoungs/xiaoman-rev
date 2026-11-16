package dev.xiaoman.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OverlayTransparencyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sliderChangesTransparencyAndReadoutLive() {
        val changes = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                var value by remember { mutableStateOf(6) }
                OverlayTransparencyControl(value) { value = it; changes.add(it) }
            }
        }
        compose.onNodeWithText("6%").assertExists()
        compose.onNodeWithContentDescription("提示框背景透明度")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        compose.waitForIdle()
        compose.onNodeWithText("75%").assertExists()
        assertTrue(changes.contains(75), "拉动条没有回调 75，实际：$changes")
    }

    @Test fun readoutReachesTheFullyTransparentEnd() {
        compose.setContent {
            MaterialTheme {
                var value by remember { mutableStateOf(6) }
                OverlayTransparencyControl(value) { value = it }
            }
        }
        compose.onNodeWithContentDescription("提示框背景透明度")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        compose.waitForIdle()
        compose.onNodeWithText("100%").assertExists()
    }

    @Test fun readoutReachesTheFullyOpaqueEnd() {
        compose.setContent {
            MaterialTheme {
                var value by remember { mutableStateOf(6) }
                OverlayTransparencyControl(value) { value = it }
            }
        }
        compose.onNodeWithContentDescription("提示框背景透明度")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        compose.onNodeWithText("0%").assertExists()
    }
}