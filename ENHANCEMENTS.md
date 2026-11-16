# 小满（xiaoman）后期增强功能梳理

> **复用说明**
> 本文档按统一模板记录对上游初始项目的每一项后期修改 / 增强：**背景与问题 → 方案 → 涉及文件 → 关键实现 → 验证 → 复用要点**。
> 它有两种复用方式：
> 1. **作为记录模板**：后续新增功能时，复制文末「空白模板」继续往下写，保持结构一致。
> 2. **作为移植手册**：把同类改动迁移回上游 [`aduoge/xiaoman`](https://github.com/aduoge/xiaoman) 或其他分支时，按「涉及文件 + 关键实现 + 复用要点」逐项落地。
>
> 文中代码片段均为可复用的最小实现模式，路径相对项目根目录。

---

## 0. 项目与构建基线（可复用环境）

| 项 | 值 |
| --- | --- |
| 上游仓库 | `https://github.com/aduoge/xiaoman` |
| 主要模块 | `app`、`hands`、`core-api` / `core-llm` / `core-mcp` / `core-memory` / `core-skills` / `core-sentinel` / `core-agent` |
| JDK | 17（用户级安装，示例 `~/jdks/jdk-17.0.21+9`） |
| Android SDK | 35（示例 `~/Android/Sdk`），`minSdk 30` / `targetSdk 35` |
| 构建工具 | Gradle 8.11.1 wrapper、AGP 8.7.3、Kotlin 2.1.0、Compose BOM 2024.12.01 |
| 运行时验证 | Robolectric 4.14.1 + Compose UI Test（`ui-test-junit4`），**无 KVM / 模拟器** |
| 调试签名 SHA-256 | `d4d0de4dfc7bc43570ea3c6ddef2f4224d3ec833047b89ab1fe9b0b478b2f729`（各次构建一致，可 `adb install -r` 覆盖安装） |

**构建脚本** `scripts/gradle.sh`：默认 `--offline`，加 `--online` 拉取缺失依赖；构建产物与缓存放在源码树之外（`~/.cache/xiaoman/<checkout-hash>/out`，本机为 `49c5532588af`），不污染工作区。

**常用命令（可直接复用）**

```bash
cd <项目根目录>
export JAVA_HOME="$HOME/jdks/jdk-17.0.21+9" ANDROID_HOME="$HOME/Android/Sdk" PATH="$JAVA_HOME/bin:$PATH"

# 单元测试（app + hands）
scripts/gradle.sh :app:testDebugUnitTest :hands:testDebugUnitTest
# 首次或新增依赖时联网
scripts/gradle.sh --online :app:testDebugUnitTest :hands:testDebugUnitTest
# 打 debug 包
scripts/gradle.sh :app:assembleDebug
# 产物：~/.cache/xiaoman/<hash>/out/app/outputs/apk/debug/app-debug.apk
```

> 注意：Bash 调用之间工作目录可能重置，命令前务必显式 `cd` 到项目根目录；脚本找不到时退出码 127。

---

## 1. HarmonyOS「测试连接」崩溃修复

**背景与问题**
HarmonyOS 4.2.0 上点击服务页「测试连接」即崩溃。根因：`McpConfig` 替换 `${VAR}` 占位符的正则里写了**未转义的 `}`**，HarmonyOS 的 ICU 正则引擎比 `java.util.regex` 严格，拒绝裸 `}`。

**方案**
把正则中的字面量 `}` 转义为 `\}`（Kotlin 字符串里写作 `\\}`）。

**涉及文件**
- `core-mcp/src/main/kotlin/dev/xiaoman/mcp/McpConfig.kt`

**关键实现**

```kotlin
// HarmonyOS 的 ICU 正则拒绝裸 }；保持转义。
Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}").replace(element.content) { match ->
    val variable = match.groupValues[1]
    env[variable]?.also { secrets += it } ?: run { missing += variable; "" }
}
```

**验证**
`core-mcp` 相关测试通过 + 对打包 dex 做正则扫描确认无裸 `}` + **真机确认**（用户反馈「可以正常对话和测试连接了」）。

**复用要点**
- 面向 OEM 系统（HarmonyOS / MIUI / ColorOS 等）时，正则中的字面量 `{` `}` 尽量转义，ICU 比桌面 JDK 严格。
- 排查方法：全仓 `grep` 所有 `Regex(...)`，逐一检查未转义的花括号。
- 这类崩溃只在特定 ROM 出现，桌面测试不一定能复现，需保留真机验证环节。

---

## 2. 聊天记录选中与复制

**背景与问题**
聊天消息无法选中复制；且 OEM 系统（尤其 HarmonyOS）经常吞掉 Compose 长按后的浮动选择菜单，导致「能选中却复制不了」。

**方案**
1. 所有消息类型（用户 / 助手 / 处理过程 / 提示 / 错误 / 审批）外层包 `SelectionContainer`，支持长按选择。
2. 每条消息额外提供一个显式「复制」按钮，复制后显示「已复制」反馈（约 1.6s 自动复位）。
3. 复制得到的是**气泡渲染后的纯文本**（去掉 Markdown 标记），而非模型原始输出。

**涉及文件**
- `app/src/main/kotlin/dev/xiaoman/app/ui/ChatComponents.kt`
- `app/src/main/kotlin/dev/xiaoman/app/ui/ApprovalCard.kt`

**关键实现**

```kotlin
/** OEM（如 HarmonyOS）常吞掉浮动选择菜单，所以每条消息再给一个按钮。 */
@Composable
private fun CopyAction(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    val tint = LocalContentColor.current
    Surface(onClick = { clipboard.setText(AnnotatedString(text)); copied = true },
        shape = RoundedCornerShape(10.dp), color = Color.Transparent,
        contentColor = if (copied) tint else tint.copy(alpha = 0.62f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
            Text(if (copied) "已复制" else "复制", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 复制得到气泡显示的内容，而不是模型发来的原始 Markdown。 */
internal fun chatPlainText(raw: String) = chatDisplayText(raw).replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")

private fun chatDisplayText(raw: String) =
    raw.replace(Regex("(?m)^#{1,6}\\s+"), "").replace(Regex("(?m)^[-*]\\s+"), "• ")
```

消息分支用法：`SelectionContainer { Text(...) }` + `CopyAction(chatPlainText(item.text))`（助手）/ `CopyAction(item.text)`（用户原样）。

**验证**
- `ChatCopyTest`：断言剪贴板内容等于渲染文本（如 `• 合计：2.40元\n尚未付款`）、「已复制」出现；用**差分法**证明长按抬起选择句柄（回复长按后的 root 节点数 > 纯文本长按）。
- `ChatPresentationTest`：不变式 `chatRichText(raw).text == chatPlainText(raw)`，保证复制与渲染一致。

**复用要点**
- `SelectionContainer` 提供长按选择；为 OEM 兼容**务必再给显式复制按钮**。
- 复制内容应与渲染一致（去 Markdown 标记），避免用户复制到 `**` `#` 等符号。
- Compose `LazyColumn` 不支持跨消息拖拽选择，按条复制即可覆盖该限制。

**测试坑（可复用）**
- Robolectric 下 `android.widget.Magnifier.dismiss()` 因 `Surface` 为 null 抛 NPE → 用 `@Implements(Magnifier::class)` 只 shadow `dismiss()`。
- `TextSelectionRange` 语义在 Robolectric 不可观测 → 改用「选择句柄 popup 的 root 数量」做差分断言。

---

## 3. 第三方 App 顶部步骤提示框背景透明度可调

**背景与需求**
让用户在小满「设置」里用**拉动条**按百分比调节第三方 App 顶部步骤提示框（pill）的背景透明度，拖动即时预览，保存后生效。

**方案**
- 模型：`AppSettings.overlayTransparency`（0..100，0=不透明，100=全透明）→ `overlayAlpha`（0..255）。默认 6% 对应原始 `0xF0` 背景，升级后外观不变。
- 持久化：`SettingsStore` 写入 plain 文件并钳制非法值。
- 渲染：`StatusOverlay.setBackgroundAlpha()` 可在 pill 显示中实时更新背景；文字加阴影保证高透明度下仍可读。
- 联动：`OverlayController` 监听设置流，任务进行中每一步都应用最新透明度。
- 设置页：`Slider` + 百分比读数 + 一块模拟第三方屏幕的**实时预览**。

**涉及文件**
- `app/.../settings/AppSettings.kt`、`app/.../settings/SettingsStore.kt`
- `hands/.../StatusOverlay.kt`
- `app/.../OverlayController.kt`、`app/.../AppGraph.kt`
- `app/.../ui/SettingsScreen.kt`、`app/src/main/res/values/strings.xml`

**关键实现**

```kotlin
// 百分比 → 背景 alpha（四舍五入）；100% 透明 = 0。默认 6% 保留原 0xF0。
val overlayAlpha: Int get() = ((100 - overlayTransparency.coerceIn(OVERLAY_TRANSPARENCY_RANGE)) * 255 + 50) / 100
const val DEFAULT_OVERLAY_TRANSPARENCY = 6
```

```kotlin
// StatusOverlay：显示中也能实时改背景 alpha。
fun setBackgroundAlpha(alpha: Int) = onMain {
    backgroundAlpha = alpha.coerceIn(0, 255)
    val view = root ?: return@onMain
    (view.background as? GradientDrawable)?.setColor((backgroundAlpha shl 24) or BACKGROUND_RGB)
    view.invalidate()
}
// 文字阴影，高透明度下仍清晰：
label.setShadowLayer(2f, 0f, 1f, 0xB3000000.toInt())
```

```kotlin
// OverlayController：settings 用「懒提供」，只在 pill 真正显示时读取（见下方回归坑）。
private val settings: () -> StateFlow<AppSettings> = { MutableStateFlow(AppSettings()) }
// collect 内：
val alpha = settings().value.overlayAlpha
// AppGraph.start()：
OverlayController(session, accessibilityDevice, uiVisible = uiVisible, settings = { settings.settings }).start(scope)
```

设置页 `OverlayTransparencyControl`：`Slider(valueRange = 0f..100f)` + `Text("$transparency%")` + `OverlayPreview(transparency)`（渐变模拟屏 + 半透明 pill）。

**验证**
- `OverlayTransparencyTest`（6 项）：默认 6→0xF0、0/50/100→255/128/0、越界钳制、表单往返、持久化、损坏值钳制。
- `OverlayTransparencyUiTest`（3 项）：用 `SemanticsActions.SetProgress` 驱动真实滑条到 75/100/0，断言读数与回调。

**复用要点**
- 百分比→alpha 用四舍五入映射，并选一个默认值保留旧外观，避免升级突变。
- pill 用 `TYPE_ACCESSIBILITY_OVERLAY`，**无需 `SYSTEM_ALERT_WINDOW` 权限**，但必须由已连接的无障碍服务创建。
- 设置流要**懒加载**，不要在 App 启动路径上 eagerly 构建加密存储。

**重要回归与修复（务必复用此教训）**
最初在 `AppGraph.start()` 直接传 `settings = settings.settings`，会在 `Application.onCreate` 阶段就构建 `EncryptedSharedPreferences`；Robolectric 无 `AndroidKeyStore`，导致**所有**启动 App 的测试崩溃（`KeyStoreException: AndroidKeyStore not found`）。
修复：把 `OverlayController` 的 settings 改为 `() -> StateFlow<AppSettings>` 懒提供，仅在 pill 真正显示（有无障碍服务且任务运行）时读取——测试中永不显示故不触发，生产环境 keystore 可用故正常。

---

## 4. 服务页逐项测试结果提示

**背景与需求**
在「服务」页点击底部「测试连接」后，要在**每个服务项目下方**显示是否测试成功的结果提示。

**方案**
- 把原本不起眼的纯文本结果升级为带图标和底色的**结果徽标**：成功＝对勾图标 +「测试成功」+「已连接，N 个工具」；失败＝错误图标 +「测试失败」+「连接失败：原因」。内置服务与自定义服务通用。
- 结果按服务名映射回各自卡片：内置服务 `ServerStatus.name == CatalogServer.id`，自定义服务为 `custom-1 / custom-2 ...`（与 `McpCatalog.toConfig` 的命名一致）。
- `McpScreen` 增加可注入的 `store` 与 `testConnection` 默认参数（生产仍走 `graph`），便于做真实点击集成测试。

**涉及文件**
- `app/src/main/kotlin/dev/xiaoman/app/ui/McpScreen.kt`
- `app/src/main/res/values/strings.xml`（新增 `mcp_test_success` / `mcp_test_failure`）

**关键实现**

```kotlin
@Composable
fun McpScreen(
    graph: AppGraph,
    store: McpSettingsStore = graph.mcpSettings,
    testConnection: suspend (McpSelection) -> List<ServerStatus> = { graph.testMcp(it) },
) { /* ... */ }
// 测试按钮：
status.clear()
testConnection(selection).forEach { status[it.name] = it }
// 卡片查找：status[server.id]（内置） / status["custom-${i + 1}"]（自定义）
```

```kotlin
@Composable
private fun StatusLine(status: ServerStatus) {
    val ok = status.connected
    val scheme = MaterialTheme.colorScheme
    val accent = if (ok) scheme.primary else scheme.error
    Surface(
        color = if (ok) scheme.primaryContainer else scheme.errorContainer,
        contentColor = if (ok) scheme.onPrimaryContainer else scheme.onErrorContainer,
        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Error, null, tint = accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(if (ok) R.string.mcp_test_success else R.string.mcp_test_failure),
                    style = MaterialTheme.typography.labelLarge, color = accent)
                Text(if (ok) stringResource(R.string.mcp_connected, status.toolCount)
                     else stringResource(R.string.mcp_failed, status.error ?: "?"),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

**验证**
`McpScreenTest`：注入 fake `testConnection` 返回「内置成功 + 自定义失败」，断言**点击前无徽标**，点击「测试连接」后内置服务出现「测试成功 / 已连接，7 个工具」、自定义服务出现「测试失败 / 连接失败：连接超时」。

**复用要点**
- 结果键必须与卡片查找键一致（内置=id，自定义=custom-N），否则不显示。
- 只对已启用且配置完整的服务测试；未填 Key 的保留「还没有填写…」提示。
- **Compose 测试坑**：底部按钮在长滚动列的视口外，直接 `performClick` 会落在屏幕外不触发；要先 `performScrollTo()` 再 `performClick()`（或用 `performSemanticsAction(SemanticsActions.OnClick)`）。

---

## 5. 可复用的测试与验证方法（无模拟器）

**配置**
- Robolectric：`@RunWith(RobolectricTestRunner::class)`、`@Config(sdk = [34])`；涉及真实渲染时加 `@GraphicsMode(GraphicsMode.Mode.NATIVE)`。
- Compose UI：`@get:Rule val compose = createComposeRule()`；依赖 `ui-test-junit4`（testImplementation）+ `ui-test-manifest`（debugImplementation）。
- `ui-test-manifest` 会往 debug 包注入 exported 的 `ComponentActivity`，在 `app/src/debug/AndroidManifest.xml` 用 `tools:replace="android:exported"` 收紧为 `false`。
- 测试里构造存储用 **plain `SharedPreferences`**，绕开 `AndroidKeyStore`。

**常见坑清单（逐条可复用）**
| 现象 | 处理 |
| --- | --- |
| `AndroidKeyStore not found` | 不在启动路径 eagerly 构建加密存储；测试用 plain prefs / 懒提供 |
| `Magnifier.dismiss()` NPE | `@Implements(Magnifier::class)` 只 shadow `dismiss()` |
| 选择范围语义不可观测 | 用选择句柄 popup 的 root 数量做差分断言 |
| 滑条交互 | `performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }` |
| 视口外按钮点击无效 | `performScrollTo().performClick()` |
| 测试 stdout 不进 XML | 诊断信息写进断言 message |
| 后台任务退出码被掩盖 | 日志追加 `GRADLE_EXIT=$?` 再 grep（`; echo` 会让整体退出码恒 0） |
| zsh `nomatch` 中断命令 | `setopt null_glob` + 数组 |

**交付前验证流程**
1. `:app:testDebugUnitTest :hands:testDebugUnitTest` 全绿（当前 app 42 项 + hands 24 项，0 失败）。
2. `:app:assembleDebug`。
3. `aapt2 dump resources/badging` 校验新字符串与包信息；解包 `classes*.dex` `grep` 关键符号。
4. `apksigner verify --print-certs` 核对签名 SHA-256 与历史一致。
5. 复制到交付目录，记录 MD5，`adb install -r` 覆盖安装。

---

## 6. 交付物与安装

- APK 交付路径：`~/agent-projects/android-projects/app-debug.apk`
- 签名 SHA-256：`d4d0de4dfc7bc43570ea3c6ddef2f4224d3ec833047b89ab1fe9b0b478b2f729`
- 手机接在 Windows 主机（VM 无 USB 直通），把 APK 拷到 Windows 后：

```bat
adb install -r app-debug.apk
```

签名一致，`-r` 覆盖安装即可，无需卸载、不丢配置。

---

## 7. 移植回上游 / 复用清单

按模块逐项迁移（建议顺序，互不依赖）：

1. **core-mcp**：`McpConfig.kt` 正则转义（§1）——独立的崩溃修复，最先合入。
2. **app/ui**：`ChatComponents.kt` + `ApprovalCard.kt` 选中复制（§2）。
3. **settings + hands**：透明度字段 / 持久化 / `StatusOverlay` 动态 alpha / `OverlayController` 懒提供 / 设置页滑条（§3）。**注意带上懒加载修复**，否则上游测试同样会因 keystore 崩溃。
4. **app/ui**：`McpScreen.kt` 结果徽标 + 可注入参数 + 字符串（§4）。
5. **测试基建**：`ui-test-junit4` / `ui-test-manifest` 依赖、debug manifest 收紧、各新增测试（§5）。

每移植一项，跑对应模块测试 + 打包 dex/资源校验，再真机回归。

---

## 附：增强记录空白模板（复制此处继续写）

```markdown
## N. <功能名称>

**背景与问题**
<为什么要改，原始缺陷或新需求>

**方案**
<总体做法，分点>

**涉及文件**
- <相对路径>

**关键实现**
```kotlin
// 可复用的最小代码片段
```

**验证**
<测试名 / 断言要点 / 是否真机确认>

**复用要点**
<移植到别处时的注意事项、坑、前提条件>
```