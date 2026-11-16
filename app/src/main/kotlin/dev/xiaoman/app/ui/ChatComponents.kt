package dev.xiaoman.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xiaoman.app.session.ChatItem
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ChatHeader(ready: Boolean, busy: Boolean, attention: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)) }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("小满", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("你的手机小助手", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(when { attention -> "等你确认"; busy -> "处理中"; ready -> "已就绪"; else -> "待设置" },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
internal fun ChatWelcome(onExample: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("今天想做点什么？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("说一句，我来帮你动手。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Example(Icons.Outlined.ShoppingBag, "选一本笔记本", "20元以内，先给我确认", "帮我在淘宝挑一本20元以内的A5笔记本，选好后给我确认。", onExample)
        Example(Icons.Outlined.Schedule, "提醒我喝水", "10分钟后提醒", "10分钟后提醒我喝水。", onExample)
        Example(Icons.Outlined.Lightbulb, "记住我的偏好", "让助手慢慢懂你", "记住我买东西喜欢简单实用。", onExample)
        Example(
            Icons.Outlined.AutoAwesome, "你好，你是谁？", "先认识一下小满",
            "你好，你是谁？", onExample,
        )
        Example(
            Icons.Outlined.Park, "周末城市漫步", "地铁优先，少走路",
            "小满，我周末想去浦东外环线沿途的开放式公园健步，帮我规划一下有草坪有森林和河岸的，" +
                "还要查一下沿途最近的快餐。对了，我要坐地铁去，给我步行少的路线。还要指明公园里公共卫生间的位置。",
            onExample,
        )
        Example(
            Icons.Outlined.ShoppingCart, "淘宝买取暖器", "领券下单，付款前确认",
            "帮我上淘宝，选择踢脚线取暖器，需要石墨烯的，最好是支持APP遥控的款式，还要有国补，" +
                "优先选择自营或旗舰店，直接帮我领券下单，但是不要直接支付，需要由我来确认。",
            onExample,
        )
        Text("也可以直接输入你的想法", modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Example(icon: ImageVector, title: String, detail: String, prompt: String, onExample: (String) -> Unit) {
    Surface(onClick = { onExample(prompt) }, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun TaskStatus(status: String, onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onStop) { Text("停止", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
internal fun ProcessCard(entries: List<ChatItem.Progress>) {
    var expanded by rememberSaveable(entries.first().id) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ListAlt, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("处理过程 · ${entries.size}条记录", Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    if (expanded) "收起处理过程" else "展开处理过程", modifier = Modifier.size(20.dp))
            }
            if (expanded) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { Text(it.text, style = MaterialTheme.typography.bodySmall,
                            color = if (it.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                CopyAction(entries.joinToString("\n") { it.text })
            }
        }
    }
}

@Composable
internal fun ChatMessage(item: ChatItem) {
    when (item) {
        is ChatItem.User -> {
            var showTime by rememberSaveable(item.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp),
                    modifier = Modifier.fillMaxWidth(0.90f).toggleTimeOnDoubleTap { showTime = !showTime }) {
                    SelectionContainer { Text(item.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
                }
                if (showTime) ChatTime(item.at, Modifier.padding(top = 4.dp))
                CopyAction(item.text)
            }
        }
        is ChatItem.Assistant -> {
            var showTime by rememberSaveable(item.id) { mutableStateOf(false) }
            Column {
                Text("小满", modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.toggleTimeOnDoubleTap { showTime = !showTime }) {
                    SelectionContainer { Text(chatRichText(item.text), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                }
                if (showTime) ChatTime(item.at, Modifier.padding(top = 4.dp))
                CopyAction(chatPlainText(item.text))
            }
        }
        is ChatItem.Question -> NoticeCard(if (item.takeover) "需要你帮一下" else "再告诉我一点", item.text, false)
        is ChatItem.Error -> NoticeCard("这次没完成", item.text, true)
        is ChatItem.Progress -> Unit // handled by ProcessCard
    }
}

/** Double tap toggles the bubble timestamp. Events are observed but never consumed, so selection still works. */
private fun Modifier.toggleTimeOnDoubleTap(toggle: () -> Unit): Modifier = pointerInput(Unit) {
    var lastUpAt = 0L
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        val now = up.uptimeMillis
        if (lastUpAt != 0L && now - lastUpAt <= viewConfiguration.doubleTapTimeoutMillis) {
            lastUpAt = 0L
            toggle()
        } else {
            lastUpAt = now
        }
    }
}

@Composable
private fun ChatTime(at: Long, modifier: Modifier = Modifier) {
    Text(chatTime(at), modifier = modifier, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val chatTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun chatTime(at: Long): String =
    chatTimeFormat.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

/** OEM shells such as HarmonyOS often swallow the floating selection menu, so every message also has a button. */
@Composable
private fun CopyAction(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val tint = LocalContentColor.current
    Surface(onClick = { clipboard.setText(AnnotatedString(text)); copied = true },
        shape = RoundedCornerShape(10.dp), color = Color.Transparent,
        contentColor = if (copied) tint else tint.copy(alpha = 0.62f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
            Text(if (copied) "已复制" else "复制", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, error: Boolean) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(chatRichText(body), style = MaterialTheme.typography.bodyMedium) }
            CopyAction(chatPlainText(body))
        }
    }
}

/** Basic emphasis and lists from model replies, rendered as text rather than raw Markdown markers. */
internal fun chatRichText(raw: String) = buildAnnotatedString {
    val text = chatDisplayText(raw)
    var position = 0
    Regex("\\*\\*(.+?)\\*\\*").findAll(text).forEach { match ->
        append(text.substring(position, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[1]) }
        position = match.range.last + 1
    }
    append(text.substring(position))
}

/** Copying yields what the bubble shows, not the raw Markdown the model sent. */
internal fun chatPlainText(raw: String) = chatDisplayText(raw).replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")

private fun chatDisplayText(raw: String) =
    raw.replace(Regex("(?m)^#{1,6}\\s+"), "").replace(Regex("(?m)^[-*]\\s+"), "• ")
