# 定制分支

本仓库基于上游 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 维护一组个人定制：去掉不需要的功能，以及按需补充的小功能。

定制与上游持续同步，因此这里记录的不是一次性改动，而是长期维护所需的约定：分支模型、同步流程、改动边界、以及每项定制的落点。

本目录下的文件：

| 文件 | 内容 |
| --- | --- |
| `README.md` | 分支模型、同步流程、定制原则、定制清单、构建与测试环境 |
| `roadmap.md` | 长期路线：阶段划分、易迁移目标、追更策略、分发与合规 |
| `upstream/` | 单次上游同步的作业单，合并落地后删除 |

## 分支模型

| 分支 | 用途 |
| --- | --- |
| `master` | 只跟随上游，不承载任何本地提交，任何时刻都应可 fast-forward |
| `custom` | 唯一工作分支，全部定制提交集中在这里 |

远端配置：

| 远端 | 仓库 | 用途 |
| --- | --- | --- |
| `origin` | NingmengLemon/rikkahub | 本仓库 fork，可推送 |
| `upstream` | rikkahub/rikkahub | 官方仓库，只读；push 地址是 `DISABLED_no_push_to_upstream`，误推会直接失败 |

远端默认分支是 `custom`。本地 `origin/HEAD` 的缓存仍指向 `origin/master`，可用 `git remote set-head origin -a` 刷新。

上游 master 已引入 Spotless + ktlint（`76e25d95`，在 3.0.0 发布之后），其 `ratchetFrom("origin/master")` 写死了 `origin/master`。下一次合并把这一版带进来之后，`master` 必须保持跟随上游并存在于 `origin`，不要清理这个引用，否则格式化闸门会静默失效。

## 上游同步流程

```powershell
git fetch upstream --no-tags
git checkout master
git merge --ff-only upstream/master
git push origin master
git checkout custom
git merge master
```

`master` 上用 `--ff-only`，一旦出现非快进说明有东西误提交到了 `master`，先查清楚再继续，不要用普通 merge 掩盖。

合并到 `custom` 后按下面的流程验证：

```powershell
$env:JAVA_HOME='E:\WorkBench\Java\jdk-23.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test --continue --console=plain
```

判据是「没有新增失败」，不是「全部通过」。Windows 上有固定的一批失败用例，见后文「测试环境噪声」一节，与代码无关。

以上是当前的合并流程。按模块边界分层的追更方式尚未落地，方向见 [`roadmap.md`](roadmap.md)。

### 当前待处理的同步

上游 3.0.0 的同步尚未进行，冲突面与建议的合并顺序已实测记录在 [`upstream/3.0.0.md`](upstream/3.0.0.md)。

## 定制原则

其中「改动尽量小，优先断入口」与「避开上游热区」两条是为降低上游合并成本而设的，会随追更策略转向或独立化落地而失效；其余几条不依赖合并成本。失效的时机、放开之后的具体差异，以及另外几处同类的合并成本让步，见 [`roadmap.md`](roadmap.md) 的「定制原则的适用范围」一节。

### 改动尽量小，优先断入口

删除功能时优先改条件、去掉入口，而不是删掉整段实现。上游改同一功能的实现时，条件行通常不会冲突，删光的实现则会在每次上游改动时反复冲突。

确实要删整个文件时，把路径记进下面的定制清单，合并时专门检查上游对该文件的改动。

### 数据结构的边界

数据层的约束以「能把官方客户端导出的备份导进来」为前提，导入器实现并在固定样本集上验证通过后即可解除下面的冻结。目标、设计要求与验收方式见 [`roadmap.md`](roadmap.md) 的阶段 1。

导入器目前尚未实现，所以现阶段仍按冻结处理：

- Room：不新增表、不新增列、不改已有列含义
- DataStore / Settings：不改已有 key 的语义；新增字段必须带默认值
- 序列化模型（`UIMessage`、`Conversation`、`Assistant` 等）：可以新增带默认值的字段，旧数据仍能读出；不改已有字段含义

需要"记录额外状态"时，优先考虑运行时内存或带默认值的新字段，而不是改已有结构。

冻结的解除条件是导入器可用并实测通过，不是一个时间点。在此之前想动结构，先去做导入器。

### 一个定制一个提交

提交信息用 `custom` 作为 scope，例如 `feat(custom): ...`、`refactor(custom): ...`，便于检索。

审计全部定制：

```powershell
git log --oneline master..custom
```

### Review 门禁

每批较大改动完成后，推送前必须过一次独立 review。目的是用干净上下文复核实现者的判断，避免实现者给自己打分。

- 用 `subagent` 派发，不要用 `subagent_fork`。fork 会继承实现这批改动的对话上下文，失去独立视角的意义。
- 在派发的 prompt 里要求子代理先加载 `code-review` 技能，再按其流程执行。
- review 基线取 `master..custom`，即本批定制相对上游的全部改动。
- 关注三件事：是否引入回归、是否触碰已存储数据的结构、是否符合本文件的定制原则（尤其是是否用了删除实现而非断入口，这条评审标准随「优先断入口」原则一并失效）。
- 未通过就修复后重跑，不得带病推送。通过后才允许 `git push origin custom`。

### 避开上游热区

两个口径都看，用途不同。以下为 2026-10-17 在 `master` 上实测，路径取精确文件路径（用通配符会多计）。

全历史改动次数，反映该文件长期被上游改动的密度，也意味着其中积累的结构性变化多：

| 文件 | 全历史 |
| --- | --- |
| `ChatVM.kt` | 172 |
| `RouteActivity.kt` | 115 |
| `ChatService.kt` | 109 |
| `ChatDrawer.kt` | 37 |
| `FilesPicker.kt` | 14 |
| `GenerationLoop.kt` | 7 |

最近 200 个提交内的改动次数，反映当前活跃度：

| 文件 | 近 200 提交 |
| --- | --- |
| `ChatService.kt` | 18 |
| `ChatDrawer.kt` | 9 |
| `ChatVM.kt` | 7 |
| `RouteActivity.kt` | 7 |
| `GenerationLoop.kt` | 7 |
| `FilesPicker.kt` | 5 |

两个口径会给出不同结论。C1 与 C2 按全历史属于动了最密集的两个文件，按近窗口则是中等热度。`60d016bb` 那次清理改到了 `ChatService.kt`，它是近期最热的文件之一，语音相关的清理集中在它身上，合并时按清单的「合并注意」列逐项复核。

上游另有两个长期未合并的重构分支 `refactor/chat-service`（停在 08-26）与 `refactor/ai-stream`（停在 08-12）。它们目前不构成冲突来源，但若上游重启这类重构，上述文件会整体重写，届时应暂停对热区的新定制。

## 定制清单

每次新增定制都往表里加一行。合并冲突时按表逐项复核，确保定制意图没有在合并中被静默丢掉。

C6 与 C7 标为计划中。上游 3.0.0 的重构面尚未稳定，此时动这两处会在下次同步时与上游改动正面相撞，等上游版本节奏放缓后再实施。

| 编号 | 内容 | 落点 | 理由 | 合并注意 |
| --- | --- | --- | --- | --- |
| C1 | 移除媒体创作与生图 | `RouteActivity`（删 `Screen.ImageGen`、`Screen.MediaCreation`、`Screen.MediaCreationSessions` 及其 entry 与 intent 处理，`Screen.SettingMedia` 的 entry 保留）、`ChatDrawer`（侧边栏两个入口）、`ViewModelModule`、`RikkaHubApp`（启动恢复）、`SettingPage`（媒体设置入口）、`shortcuts.xml`、`AndroidManifest`；删除 `ui/pages/mediacreation` 与 `ui/pages/imggen`；其遗留的 158 条字符串文案（`media_creation_*`、`imggen_page_*`、`chat_page_menu_image_generation`、`shortcut_media_creation`、`setting_page_media_desc`、`menu`、`sketch_draw_on_image`）已删除 | 不使用 | 刻意保留未动：`data/model/MediaCreation.kt`、`data/files/MediaCreationFiles.kt`、`data/db` 下的实体与 DAO、`data/repository/MediaCreationRepository.kt`、`service/MediaCreationService.kt`、`service/MediaCreationForegroundService.kt`、整个 `mediagen/` 模块、manifest 的 `<service>` 声明、`Settings.mediaGenerationProviders`、`SettingMediaPage.kt` 及其路由。合并时不要因为界面上看不到就顺手删掉这些。两处冲突风险：上游若重排 `RouteActivity` 的 `Screen` 与 entry 列表；上游 `feat/mediagen` 分支（16 提交，2026-10-05 仍有活动）正在改 `MediaCreationService.kt` 与被删除的 mediacreation UI，合并时按该分支进度判断是否恢复部分实现 |
| C2 | 移除 Firebase Analytics | `app/build.gradle.kts`、`AppModule`、`ChatVM`、`ViewModelModule` | 自用不需要埋点上报 | Crashlytics 保留，`google-services` 插件与 `google-services.json` 仍需存在。上游若增删 `ChatVM` 构造参数，`ViewModelModule` 里的显式传参会冲突 |
| C3 | 移除赞助商推广，并允许删除所有提供商 | `DefaultProviders.kt`（删掉 AiHubMix、APIMart、随想AI网关、MaruCode 四项，新增 `REMOVED_SPONSOR_PROVIDER_IDS`）、`RecommendedProviders.kt`（整个文件删除，它只包含这四家）、`SettingProviderPage.kt`（断开右上角推荐 Sheet、去掉 AiHubMix 的「10% 优惠」标签、去掉列表删除菜单的 `builtIn` 门禁）、`SettingProviderDetailPage.kt`（去掉详情页删除按钮的 `builtIn` 门禁）、`ai/util/Request.kt`（去掉 `configureReferHeaders` 里给 aihubmix.com 加 `APP-Code` 的分支）、`DefaultSettings.kt`（去掉 AiHubMix 的 TTS 预置项，新增 `REMOVED_SPONSOR_TTS_PROVIDER_IDS`）、`SettingsNormalization.kt`（提供商不再补回；助手与 TTS 仍补回）、相关测试 | 不用这些推广渠道；预置项原本在界面上无法删除，规范化又会把删掉的补回来 | 行为改动，`Settings.providers` 字段与 DataStore key 未变，不触发迁移。三点副作用：其一，存量里的赞助商条目会在下一次设置变更写盘时从设置中清掉，用户在这四家填过的 API key 与自建模型随之消失，回退旧版本后不会再出现；其二，因为不再补回，上游以后新增的预置提供商不会出现在已有安装里，需要手动添加；其三，删掉提供商后 `chatModelId` 等可能悬空，查找返回 null 并按未选择处理。上游若重写 `withBuiltInDefaults`，必须确认仍不补回提供商，否则删除功能失效；上游若恢复 `RecommendedProviders.kt` 或推荐入口，需要重新删除。`setting_provider_page_recommend` 字符串已删除 |
| C4 | 清理赞助商图标、README 推广与启动赞助弹窗 | `utils/AIIconMatcher.kt`（删掉 aihubmix 的图标映射与匹配正则）、`app/src/main/assets/icons/aihubmix-color.svg`（删除）、`README.md`、`README_ZH_CN.md`、`README_ZH_TW.md`（删掉 Sponsors 章节）、`SettingPage.kt`（删掉启动 100 次后的赞助弹窗及其失效的三个 import 与 `launchCount` 订阅）、`SettingVM.kt`（删掉失去唯一读者的 `launchCount`）、弹窗的 4 条 `setting_page_sponsor_alert_*` 字符串 × 7 个 locale 已删除 | 不需要推广入口 | 捐赠页 `SettingDonatePage`、`SponsorAPI`、`Screen.SettingDonate` 与捐赠相关字符串保留不动。`Settings.sponsorAlertDismissedAt` 是已存储字段，未删除以免改动结构，只是不再有自动写入方，`DebugPage` 仍可手动改。刻意保留：`docs/sponsors/*.png` 已无引用但仍留在仓库里，删除会与上游图片更新产生 modify/delete 冲突。合并注意：三份 README 属上游高频改动文档，以上游最新版为准并重新删掉 Sponsors 章节；`SettingPage` 的弹窗区域与 `SettingProviderPage` 类似，上游若加回推广块，需要重新删除 |
| C5 | 移除 TTS 与 STT（语音合成与语音识别） | **speech 模块删除 32 个文件**（`speech/src/main/java/me/rerere/` 下除 `tts/provider/TTSProviderSetting.kt`、`asr/ASRProviderSetting.kt` 外的全部）：ASR 侧 `ASRController.kt`、`ASRState.kt`、`ASRVoiceTurn.kt`、`AudioAmplitude.kt`、`providers/DashScopeASRController.kt`、`providers/MiMoASRController.kt`、`providers/OpenAIRealtimeASRController.kt`、`providers/StepASRController.kt`、`providers/VolcengineASRController.kt`、`providers/VolcengineASRProtocol.kt`；TTS 侧 `controller/AudioPlayer.kt`、`controller/TextChunker.kt`、`controller/TtsController.kt`、`controller/TtsSynthesizer.kt`、`model/PlaybackState.kt`、`model/TTSRequest.kt`、`model/TTSResponse.kt`、`provider/TTSManager.kt`、`provider/TTSProvider.kt`、`provider/TTSProviderException.kt`、`provider/providers/` 下 `ElevenLabsTTSProvider.kt`、`FishAudioTTSProvider.kt`、`GeminiTTSProvider.kt`、`GroqTTSProvider.kt`、`MiMoTTSProvider.kt`、`MiniMaxTTSProvider.kt`、`OpenAITTSProvider.kt`、`QwenTTSProvider.kt`、`StepTTSProvider.kt`、`SystemTTSProvider.kt`、`VolcengineTTSProvider.kt`、`XAITTSProvider.kt`；**测试删除 5 个**：`app/src/test/java/me/rerere/rikkahub/ui/pages/chat/VoiceSessionControllerTest.kt`、`speech/src/test/java/me/rerere/asr/ASRVoiceTurnTest.kt`、`speech/src/test/java/me/rerere/asr/providers/VolcengineASRProtocolTest.kt`、`speech/src/test/java/me/rerere/tts/provider/providers/MiMoTTSProviderTest.kt`、`speech/src/test/java/me/rerere/tts/provider/providers/VolcengineTTSProviderTest.kt`；**app 侧删除**：`ui/hooks/TTS.kt`、`ui/hooks/ASR.kt`、`ui/context/LocalTTSState.kt`、`ui/context/LocalASRState.kt`、`ui/components/ui/TTSController.kt`、`ui/components/ai/AsrButton.kt`、`ui/components/ai/VoiceModeRow.kt`、`ui/pages/chat/VoiceSessionController.kt`、`ui/pages/chat/VoiceMode.kt`、`ui/pages/chat/TTSAutoPlay.kt`、`ui/pages/setting/SettingSpeechPage.kt`、`ui/pages/setting/components/TTSProviderConfigure.kt`、`ui/pages/setting/components/ASRProviderConfigure.kt`、`data/ai/tools/local/TextToSpeechTool.kt`；**断入口**：`SettingPage` 语音项、`SettingPreferencesGeneralPage` 的「TTS 设置」区块、`ChatInput` 麦克风与录音权限音效、`FilesPicker` 语音模式项、`ChatMessageActions` 朗读、`AssistantLocalToolPage` 的 TTS 开关、`RouteActivity` 的 TTS/ASR 状态与 `Screen.SettingSpeech`、`BuiltinToolUIs` 的 TTS 渲染与 `ToolUIRegistry` 注册、`LocalTools` 不再产出 TTS 工具、构造参数去掉 `ttsManager` 与 `settingsStore`、`AppModule` 解绑 `TTSManager` 并调整 `LocalTools` 注入、`ChatPage` 与 `ChatVM` 的语音会话接线、manifest 的 `RECORD_AUDIO` 与 `TTS_SERVICE` 查询；**一并清掉的死代码与孤儿资源**：`AppEvent.Speak`、`ChatService.enqueueVoiceMessage`、`SettingsExt` 的 `getSelectedTTSProvider` 与 `getSelectedASRProvider`、`PermissionTypes.PermissionRecordAudio`、`MessageQueue` 的 reply 等待通道与 `MessageQueuePausedException`、`ChatService` 里对应的 reply 完成路径与 `failReplyWaiters` 调用、3 个 voice 队列测试、`res/raw/asr_start.mp3` 与 `asr_stop.mp3`，以及 7 个 locale 各 314 条无引用字符串（按归属拆开：媒体创作与生图 158 条属 C1、提供商推荐 1 条属 C3、赞助弹窗 4 条属 C4、语音相关 151 条属 C5） | 不用语音功能 | 保留存储与反序列化：`Settings.ttsProviders`、`asrProviders`、`selectedTTSProviderId`、`selectedASRProviderId`、`defaultTTSPlaybackSpeed`、`ttsOnlyReadQuoted`、`ttsOnlyReadOutsideBrackets`、`autoPlayTTSAfterGeneration` 等字段与 DataStore key 未动；`TTSProviderSetting`、`ASRProviderSetting` 逐字节未改；`DEFAULT_TTS_PROVIDERS`（只剩 SystemTTS）与 TTS 补回逻辑保留；`LocalToolOption.Tts` 枚举值保留，旧助手配置仍可反序列化；`speech` 的 `build.gradle.kts` 未精简，仍带 media3 等用不到的依赖。与 C4 不同，这里选择的是彻底清理而非保留孤儿，理由是语音功能的入口、实现、文案与音效已全部消失，留着孤儿只会增加维护时的误判。合并注意：app 侧语音代码是上游活跃区域（3.0.0 就加了语音模式开关），恢复实现时需补齐上述入口；`speech` 模块上游虽未改动，但按本行删空后不要因界面上看不到而恢复。`SettingPage`、`SettingPreferencesGeneralPage`、`ChatInput`、`ChatMessageActions`、`ChatPage`、`ChatVM`、`ChatService`、`RouteActivity` 均为热区，冲突时以保留删除为准 |
| C6 | 移除聊天回复建议（计划中） | 生成侧 `ChatService` 里的建议请求（`generateSuggestion` 与 `if (!settings.enableSuggestion) return@runCatching` 的判断仍在 `ChatService`，3.0.0 只把提示词与请求构建移到 `ConversationMetaGenerator`）、`data/ai/prompts/Suggestion.kt`（其 `DEFAULT_SUGGESTION_PROMPT` 被 `Settings.kt` 与 `SettingModelPromptPage` 当默认值引用，删文件前要把常量挪走或内联）；UI 侧 `ChatList` 的建议 chip 列表、`ChatPage` 的 `onClickSuggestion`、`SettingModelPage` 的开关、`SettingModelPromptPage` 的提示词编辑项 | 不用这个功能，每条回复都会多一次生成请求 | 保留存储与协议：`Settings.enableSuggestion`、`Settings.suggestionPrompt` 的字段与 DataStore key 不动，`Conversation.chatSuggestions` 是 Room 字段（`ConversationEntity.chatSuggestions`）不能删，`WebDto.chatSuggestions` 一并保留。只断入口与生成，不删字段 |
| C7 | 替换官方 logo 为自己的图标（计划中） | `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` 下的 `ic_launcher.png`、`ic_launcher_background.png`、`ic_launcher_foreground.png`、`ic_launcher_monochrome.png`，5 个密度共 20 个 png；`mipmap-anydpi-v26/ic_launcher.xml` 的结构不变 | 让桌面上装的这个版本与官方版一眼区分开 | 只替换 png 内容，保持文件名与目录结构不变，让上游对 `ic_launcher.xml` 逻辑的改动仍能自动合并。`monochrome` 必须一起做，否则 Android 13 的主题图标会退回官方样式。设置-关于页直接复用 `R.mipmap.ic_launcher`，无需单独改。上游 3.0.0 的 `bf04cde9` 调整过图标配色，此后上游再改这些 png 时会与本行冲突，处理方式是一律保留我们自己的 png。它同时改了 `web-ui/public/favicon.ico`、`docs/icon.png`、`docs/img/poster.png`，这几处不在本行范围内，若也要换成自己的品牌需单独处理 |

## 构建

本机构建需要以下几个非仓库内的前提，缺任何一项都会在特定任务上失败：

- JDK 必须是 HotSpot。默认 `JAVA_HOME` 指向 OpenJ9 的 JDK 21，KSP 会抛 `NoClassDefFoundError: jdk.jfr.Event`。改用 `E:\WorkBench\Java\jdk-23.0.2`
- `app/google-services.json` 是占位文件（被 gitignore）。缺失时 `:app:processDebugGoogleServices` 失败。占位配置让 Crashlytics 无法上报崩溃，不影响应用运行；Analytics 已在 C2 中移除
- `local.properties` 提供 release 签名（被 gitignore）
- `material3/material-color-utilities` 是 submodule，未初始化时 `:material3:compileDebugKotlin` 报 `Unresolved reference 'dynamiccolor'`

```powershell
$env:JAVA_HOME='E:\WorkBench\Java\jdk-23.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```

产物位于 `app/build/outputs/apk/debug/` 与 `app/build/outputs/apk/release/`，各有 universal 与分 ABI 三种。debug 的 applicationId 带 `.debug` 后缀，可与官方版共存；release 使用自签名，与官方签名冲突，需先卸载官方版。

## 测试环境噪声

在 Windows 上下列测试必然失败，属于测试本身假设类 Unix 环境，与代码改动无关。已用更新前的提交做过对照，失败数完全一致：

| 测试类 | 失败数 | 原因 |
| --- | --- | --- |
| `data.datastore.SettingsStoreTest` | 6/12 | DataStore 在 Windows 上不能 rename 覆盖被占用的目标文件 |
| `workspace.ExampleUnitTest` | 4/10 | 硬编码执行 `/bin/sh`，Windows 上 `CreateProcess error=2` |
| `workspace.RootfsInstallerTest` | 1/3 | 软链断言期望 `/bin/busybox`，Windows 路径分隔符给出 `bin\busybox` |

`highlight` 的 `LanguageFixtureTest` 会因工作区文件被 checkout 成 CRLF 而整批失败。仓库的 `.gitattributes` 要求 `eol=lf`，但全局 `core.autocrlf=true` 的前提下，旧 checkout 的文件可能仍是 CRLF。表现是 30 个用例全部报 `highlight.js parity` 不匹配。修复：

```powershell
Remove-Item -Recurse -Force highlight\src\test\resources\hljs
git checkout -- highlight/src/test/resources/hljs
```
