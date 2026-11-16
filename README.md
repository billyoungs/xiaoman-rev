# 小满（Xiaoman）—— TRAE 训练营二期课后实践

> **本 Fork（billyoungs/xiaoman-rev）**：字节跳动 **TRAE 官方训练营第二期 · 用 TRAE 搭建个人 Agent 助理** 的课后实践项目。
> 上游仓库 [`aduoge/xiaoman`](https://github.com/aduoge/xiaoman) 是训练营提供的基线代码，本仓库在此基础上叠加了若干增强与修复。
> 下游增强明细见 [ENHANCEMENTS.md](ENHANCEMENTS.md)。

[English](README.en.md)

---

小满是一个跑在 Android 手机上的开源私人助手。你在它的 App 里聊天，它会记住你告诉它的事，
也能替你操作手机上的其他 App，比如打车、点外卖、改系统设置。新场景以**技能**的形式添加：一个技能
就是一个包含 `SKILL.md` 和可选 `flow.yaml` 的小文件夹，推到手机上即可生效，不需要重新打包 APK。

- 需要 Android 11（API 30）及以上。小满通过无障碍服务读取屏幕并执行操作。
- iOS 无法在手机上单独运行；可用 Mac + WebDriverAgent 的方式试用，见 [docs/ios.md](docs/ios.md)。
- 可接入 MCP 服务（高德地图、腾讯地图、滴滴、12306 等），查询和下单类的事情可以不操作手机，见 [docs/mcp.md](docs/mcp.md)；安卓 App 在「服务」页一键勾选。
- iPhone 不装 App 也能用：网页版添加到主屏幕后聊天、勾选 MCP 服务，见 [docs/web.md](docs/web.md)。
- 模型：任意兼容 OpenAI 接口的服务都可以，例如火山方舟、阿里云百炼、OpenRouter、vLLM、Ollama。
  需要配置两个模型：一个聊天模型，一个能看图的屏幕操作模型。

## 本 Fork 的增强项

以下是对上游 `aduoge/xiaoman` 的全部新增 / 修改内容，按类别汇总。每项的背景、实现与验证细节见
[ENHANCEMENTS.md](ENHANCEMENTS.md)。

### LLM 客户端

- **内联 `<tool_call>` XML 解析**：当模型在末轮（或任何轮次）把工具调用以 XML 形式嵌进 `content` 正文时，客户端能自动提取 `<tool_call>` 块、剥离干净文本、并把 `radius=8000` 等字符串参数强转成 int/long/double/boolean，避免 MCP 服务端因类型不匹配拒绝调用。解决了末轮降级后工具调用被当正文显示的"乱码"问题。
  - 涉及 `core-llm/.../OpenAiCompatibleClient.kt`、`OpenAiCompatibleClientTest.kt`

### Agent 与对话循环

- **工具轮次上限 8 → 16**：`MAX_TOOL_ROUNDS` 从 8 提到 16。citywalk 这类多步骤任务（地理编码 → 搜 POI → 批量测距 → 搜设施 → 规划路线 → 打开地图）原来常常打满 8 轮提前终止，现在能完整跑完。
  - 涉及 `core-agent/.../XiaomanAgent.kt`
- **聊天工作流 phone_task 约束收紧**：`CHAT_SKILLS_HINT` 明确告知模型——只有工作流**要求打开某个 App 或界面**时才用 `phone_task`，否则直接在对话里按技能说明做。减少不必要的界面跳转。
  - 涉及 `core-agent/.../Prompts.kt`
- **URL 编码兼容全 API**：`URLEncoder.encode(String, Charset)` 是 Android API 33+ 才有的，换成编码名字符串重载，全版本可用。
  - 涉及 `core-agent/.../FlowRunner.kt`

### MCP 与确认机制

- **确认问题先呈现再阻塞**：`UserChannelApprovals` 在向 `UserChannel.ask()` 发问题之前，先通过新增的 `AgentListener` 把问题公告到对话流里，让用户看到"正在等你确认"再触发阻塞等待。
  - 涉及 `core-mcp/.../UserChannelApprovals.kt`
- **只读工具判定修正**：`ConfirmPolicy.WRITES` 改用 `tool.isReadOnly()`（综合 `readOnlyHint` 与工具名启发式），不再只看 `readOnlyHint` 字段。
  - 涉及 `core-mcp/.../McpToolProvider.kt`
- **HarmonyOS ICU 正则兼容**：`McpConfig` 里 `${VAR}` 替换的正则给右括号加了 `\` 转义——HarmonyOS 的 ICU 正则引擎拒绝裸 `}`，Android 的 ICU 宽松不敏感。
  - 涉及 `core-mcp/.../McpConfig.kt`

### App 界面与交互

- **悬浮胶囊背景透明度可调**：设置页新增"悬浮按钮透明度"滑块，`OverlayController` 实时读 `AppSettings.overlayTransparency`，无需重启即可生效。范围 clamp 在合法区间内。
  - 涉及 `app/.../settings/AppSettings.kt`、`SettingsStore.kt`、`OverlayController.kt`、`StatusOverlay.kt`、`SettingsScreen.kt`
- **聊天记录选中 & 复制**：长按消息弹出"复制"菜单，`ChatComponents` 里新增加密 SharedPreferences 读写、时间戳格式化，修复输入法焦点。
  - 涉及 `app/.../ui/ChatComponents.kt`、`ChatScreen.kt`、`McpScreen.kt`、`XiaomanRoot.kt`
- **MCP 确认卡片快捷按钮**：工具确认请求除了传统的回复"确认"/"取消"，卡片上直接提供两个可点按钮，减少手动输入。
  - 涉及 `app/.../ui/ApprovalCard.kt`
- **服务页逐项测试结果提示**：MCP 服务测试失败时显示结构化错误。
  - 涉及 `app/.../ui/McpScreen.kt`

### 技能

- **citywalk-plan**（新增 chat 技能）：城市漫步规划，地铁优先、少步行、搜沿途厕所/便利店/麦当劳。内含**效率守则**——搜索结果自带经纬度不再重复 `maps_geo`、`maps_distance` 用 `|` 批量传起终点、能并行的工具放同一轮。
  - 路径 `skills/examples/citywalk-plan/SKILL.md`
- **citywalk-map**（新增 phone_task 技能）：按「公共交通」方案在高德地图里打开路线，支持多途经点。
  - 路径 `skills/examples/citywalk-map/SKILL.md`、`flow.yaml`

### 测试与工具链

- **新增 Robolectric + Compose UI Test 测试集**：
  - `OverlayTransparencyTest` / `OverlayTransparencyUiTest`：透明度设置
  - `ChatCopyTest`：聊天复制
  - `ChatTimeTest`：时间戳格式化
  - `McpScreenTest`：服务页 UI
- **HarmonyOS「测试连接」崩溃修复**：（上游修复，本 Fork 已包含）
- **上游已有的增强**：淘宝销量不触发确认、`xiaoman://alarm/set` 深链、聊天工作流频道、Debug SAY 广播、网络断开快速失败、部署脚本打包 skills 等——全部保留。

### 构建基线（可复用）

| 项 | 值 |
| --- | --- |
| 上游仓库 | `https://github.com/aduoge/xiaoman` |
| 本 Fork | `https://github.com/billyoungs/xiaoman-rev` |
| JDK | 17（示例路径 `~/jdks/jdk-17.0.21+9`） |
| Android SDK | 35，`minSdk 30` / `targetSdk 35` |
| 构建工具 | Gradle 8.11.1 wrapper、AGP 8.7.3、Kotlin 2.1.0、Compose BOM 2024.12.01 |
| 测试 | Robolectric 4.14.1 + Compose UI Test；**无 KVM / 模拟器** |
| 构建脚本 | `scripts/gradle.sh`（默认 `--offline`，加 `--online` 联网），产物在源码树外 `~/.cache/xiaoman/<hash>/out` |

## 工作方式

```
你：帮我打车回家
 │
 ▼
聊天模型 ── 查记忆（家在哪）、挑技能（taxi-home）
 │
 └─ phone_task ──► 操作循环：截屏 + 节点树 → 操作模型给出一步 → Sentinel 审核 → 执行
                     │                                        │
                     │                         支付 / 发送 / 删除等先问你
                     ▼
               结果摘要回到聊天；成功的操作路径写入记忆，下次执行时参考
```

## 安全

每一步操作在真正落到手机之前，都要经过 Sentinel 策略检查。这一层在模型之外，模型无法绕过：

- **拒绝**：向密码框输入；在受保护（FLAG_SECURE）的页面上操作；技能声明了 `apps` 时操作其他 App。
- **询问**：点击「支付」「发送」「删除」「确认呼叫」等敏感按钮前，需要你确认（允许一次 / 本次任务都允许 / 拒绝）。
- 技能只能**追加**确认词，不能删除内置确认词。
- 每一次决策都会写入审计记录。

## 构建

需要 JDK 17 和 Android SDK 35。

```bash
scripts/gradle.sh --online test :app:assembleDebug   # 首次运行，会下载依赖
scripts/gradle.sh test :app:assembleDebug            # 之后可以离线构建
```

`scripts/gradle.sh` 封装了 `./gradlew`，构建产物放在仓库目录之外，保持工作区干净。可用的环境变量见脚本开头的注释。

## 使用

1. 安装 debug APK，打开小满。
2. 在「设置」里分别填写聊天模型和操作模型的 Base URL、模型名称和 API Key。
3. 按提示开启小满无障碍服务。
4. 回到「聊天」，试试：「帮我打车回家」「记住我家在……」「把 Wi-Fi 打开」。

以火山方舟为例，Base URL 填 `https://ark.cn-beijing.volces.com/api/v3`（也是默认值），模型填方舟上的模型或接入点 ID。

## 课堂实践

[学生动手手册](docs/classroom.md)：TRAE 训练营第二期「用 TRAE 搭建你的个人 Agent 助手」的配套手册。第一节装好小满并跑通淘宝任务，第二节蒸馏你自己，再用 TRAE 改外卖技能、车票技能和首页入口。

## 技能与开发者 CLI

- 技能格式见 [docs/skills.md](docs/skills.md)，示例在 [skills/examples/](skills/examples/)。
- [cli/](cli/README.md)（Python 3，仅依赖标准库）可以校验技能、通过 adb 推送到手机、在 debug 包上直接跑任务并查看轨迹：

```bash
python3 cli/xiaoman.py lint skills/examples/*
python3 cli/xiaoman.py push skills/examples/taxi-home
python3 cli/xiaoman.py run "打开设置里的 Wi-Fi 页面" --skill open-settings-wifi --follow
python3 cli/xiaoman.py traces show <run_id>
```

## 目录结构

```
xiaoman/
├── core-api/        # 接口与数据结构，其他模块只依赖它
├── core-llm/        # 兼容 OpenAI 的模型客户端
├── core-memory/     # 记忆：Basic Memory 格式的 Markdown 笔记 + 中文检索
├── core-skills/     # 技能加载与校验（SKILL.md / flow.yaml）
├── core-sentinel/   # 安全策略：允许 / 询问 / 拒绝 + 审计
├── core-agent/      # Agent：聊天编排、操作循环、技能流程、经验沉淀
├── hands/           # Android 无障碍层：读屏、点击、输入、悬浮停止按钮
├── ios-hands/、ios-runner/ # iOS WDA 设备接口 / Mac JVM 命令行（使用说明见 docs/ios.md）
├── core-mcp/              # MCP 客户端，把外部服务的工具交给聊天模型（见 docs/mcp.md）
├── mcp/                   # MCP 服务器示例清单 servers.example.json
├── web-server/            # 网页版：服务端 + 移动端网页（见 docs/web.md）
├── app/             # Android App：聊天、技能、记忆、设置界面
├── cli/             # 技能开发者 CLI
├── skills/examples/ # 示例技能，也会打包进 APK
├── docs/            # 架构与技能文档
├── ENHANCEMENTS.md  # 本 Fork 后期增强项详细记录
└── scripts/         # 构建脚本
```

`core-*` 都是纯 Kotlin/JVM 模块，不依赖 Android API，可以在任何机器上跑单元测试。详细设计见
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 记忆

记忆是 Basic Memory 格式的 Markdown 文件，存放在
`/sdcard/Android/data/dev.xiaoman.app/files/memory/`，可以直接拷进 Obsidian 查看和编辑。
「忘记」会直接删除对应文件。

## 许可证

MIT，见 [LICENSE](LICENSE)。
