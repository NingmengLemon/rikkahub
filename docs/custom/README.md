# 定制分支

本仓库基于上游 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 维护一组个人定制：去掉不需要的功能，以及按需补充的小功能。

定制与上游持续同步，因此这里记录的不是一次性改动，而是长期维护所需的约定：分支模型、同步流程、改动边界、以及每项定制的落点。

本目录下的文件：

| 文件 | 内容 |
| --- | --- |
| `README.md` | 分支模型、同步流程、定制原则、定制清单、构建与测试环境 |
| `roadmap.md` | 长期路线：阶段划分、易迁移目标、追更策略、分发与合规 |
| `importer.md` | 阶段 1 导入器的设计：读取层、版本判定、settings 解析、验收样本、分步计划 |

大的上游同步可以另建一份作业单放在 `upstream/`，合并落地后删除；当前没有作业单，所以那个目录不存在。

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

上游已引入 Spotless + ktlint（`76e25d95`，在 3.0.0 发布之后），其 `ratchetFrom("origin/master")` 写死了 `origin/master`，只检查相对该引用改动过的文件。因此 `master` 必须保持跟随上游并存在于 `origin`，不要清理这个引用：本地 `master` 快进后若尚未推送，`origin/master` 仍停在旧基点，`spotlessCheck` 会把上游那一整批文件当作改动过的来检查，报出与本地定制无关的格式违规。

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

`test` 覆盖所有模块，包括噪声表里那些 workspace 用例。app 有 `github` 与 `play` 两个渠道，它会按 debug 与 release 组合出 4 套变体各跑一遍，速度慢一些。

判据是「没有新增失败」，不是「全部通过」。Windows 上有固定的一批失败用例，见后文「测试环境噪声」一节，与代码无关。

以上是当前的合并流程。按模块边界分层的追更方式尚未落地，方向见 [`roadmap.md`](roadmap.md)。

### 当前待处理的同步

无。基点在上游 master 的当前头 `a046505a`，下一次同步从该点开始。

## 定制原则

本节中「改动尽量小，优先断入口」与「避开上游热区」两条是为降低上游合并成本而设的，会随整分支合并结束而失效；其余几条不依赖合并成本。失效的时机、放开之后的具体差异，以及另外几处同类的合并成本让步，见 [`roadmap.md`](roadmap.md) 的「定制原则的适用范围」一节。

### 改动尽量小，优先断入口

删除功能时优先改条件、去掉入口，而不是删掉整段实现。上游改同一功能的实现时，条件行通常不会冲突，删光的实现则会在每次上游改动时反复冲突。

确实要删整个文件时，把路径记进下面的定制清单，合并时专门检查上游对该文件的改动。

C8 与 C9 删的是完整实现而不是入口。这两项功能各自独立成文件，改动集中在自己那一小块里，不进入上游的核心改动路径；保留断入口会让每次上游改动都要重新确认入口是否还在，维护成本高于删除。与上游热区重叠的功能仍按上面这条规则处理。

### 数据结构的边界

数据层的约束以「能把官方客户端导出的备份导进来」为前提，导入器实现并在固定样本集上验证通过后即可解除下面的冻结。目标、设计要求与验收方式见 [`roadmap.md`](roadmap.md) 的阶段 1。

导入器已落地读取层、写入层、多版本快照、恢复分流与转换（C20 到 C23），验收覆盖读取层与转换产物两层（C24）。按冻结条款的原文「实现并在固定样本集上验证通过」，验收还差附件文件的哈希集合、settings 关键字段与搜索的人工闸门，所以冻结继续生效：

- Room：不新增表、不新增列、不改已有列含义
- DataStore / Settings：不改已有 key 的语义；新增字段必须带默认值
- 序列化模型（`UIMessage`、`Conversation`、`Assistant` 等）：可以新增带默认值的字段，旧数据仍能读出；不改已有字段含义

需要"记录额外状态"时，优先考虑运行时内存或带默认值的新字段，而不是改已有结构。

冻结的解除条件是导入器可用并实测通过，不是一个时间点。导入器与第一次改 Room schema 是同一批工作：分叉之前它没有真实场景（官方 v32 的 identity 与本仓库相同，真实官方备份走 `stageRestore`），分叉之后才需要它，而分叉本身又要靠它保命，所以两者只能一起做，同一批里导入器先落地并验证，再改结构。详见 `importer.md`。

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
- 关注三件事：是否引入回归、是否触碰已存储数据的结构、是否符合本文件的定制原则。C8 与 C9 按「改动尽量小，优先断入口」一节的补充说明直接删了实现，不按「优先断入口」判；这条评审标准本身随该原则一并失效。
- 未通过就修复后重跑，不得带病推送。通过后才允许 `git push origin custom`。

### 避开上游热区

两个口径都看，用途不同。以下为 2026-10-20 在 `e5bb7073`（上游 3.1.0 的基点）上实测，路径取精确文件路径（用通配符会多计）。

全历史改动次数，反映该文件长期被上游改动的密度，也意味着其中积累的结构性变化多：

| 文件 | 全历史 |
| --- | --- |
| `ChatVM.kt` | 175 |
| `RouteActivity.kt` | 116 |
| `ChatService.kt` | 114 |
| `ChatDrawer.kt` | 38 |
| `FilesPicker.kt` | 16 |

最近 200 个提交内的改动次数，反映当前活跃度：

| 文件 | 近 200 提交 |
| --- | --- |
| `ChatService.kt` | 19 |
| `ChatDrawer.kt` | 10 |
| `ChatVM.kt` | 8 |
| `RouteActivity.kt` | 8 |
| `FilesPicker.kt` | 6 |

两个口径会给出不同结论。C1 与 C2 按全历史属于动了最密集的两个文件，按近窗口则是中等热度。`60d016bb` 那次清理改到了 `ChatService.kt`，它是近期最热的文件之一，语音相关的清理集中在它身上，合并时按清单的「合并注意」列逐项复核。

`GenerationLoop.kt` 已随上游 3.0.0 的重构删除，多步生成循环迁到 `ai/src/main/java/me/rerere/ai/agent/AgentLoop.kt`。后者随 3.1.0 的合并进来，此后上游每次改它都在累计热度，目前仍然偏低。

上游另有两个长期未合并的重构分支 `refactor/chat-service`（停在 08-26）与 `refactor/ai-stream`（停在 08-12）。它们目前不构成冲突来源，但若上游重启这类重构，上述文件会整体重写，届时应暂停对热区的新定制。

## 定制清单

每次新增定制都往表里加一行。合并冲突时按表逐项复核，确保定制意图没有在合并中被静默丢掉。

| 编号 | 内容 | 落点 | 理由 | 合并注意 |
| --- | --- | --- | --- | --- |
| C1 | 移除媒体创作与生图 | `RouteActivity`（删 `Screen.ImageGen`、`Screen.MediaCreation`、`Screen.MediaCreationSessions` 及其 entry 与 intent 处理，`Screen.SettingMedia` 的 entry 保留）、`ChatDrawer`（侧边栏两个入口）、`ViewModelModule`、`RikkaHubApp`（启动恢复）、`SettingPage`（媒体设置入口）、`shortcuts.xml`、`AndroidManifest`；删除 `ui/pages/mediacreation` 与 `ui/pages/imggen`；其遗留的 157 条无引用字符串（以 `media_creation_*`、`imggen_page_*` 为主，另有 `chat_page_menu_image_generation`、`shortcut_media_creation`、`setting_page_media_desc`、`menu`）已删除；`media_creation_*` 中仍有 19 条在用（`MediaCreationService`、`MediaCreationForegroundService`、`RikkaHubApp`），不要按前缀整批删；`sketch_draw_on_image` 当时一并删除，但它同时是聊天输入框画板的按钮文案，已恢复 | 不使用 | 刻意保留未动：`data/model/MediaCreation.kt`、`data/files/MediaCreationFiles.kt`、`data/db` 下的实体与 DAO、`data/repository/MediaCreationRepository.kt`、`service/MediaCreationService.kt`、`service/MediaCreationForegroundService.kt`、整个 `mediagen/` 模块、manifest 的 `<service>` 声明、`Settings.mediaGenerationProviders`、`SettingMediaPage.kt` 及其路由。合并时不要因为界面上看不到就顺手删掉这些。两处冲突风险：上游若重排 `RouteActivity` 的 `Screen` 与 entry 列表；上游 `feat/mediagen` 分支（16 提交，2026-10-05 仍有活动）正在改 `MediaCreationService.kt` 与被删除的 mediacreation UI，合并时按该分支进度判断是否恢复部分实现 |
| C2 | 移除 Firebase Analytics | `app/build.gradle.kts`、`AppModule`、`ChatVM`、`ViewModelModule` | 自用不需要埋点上报 | Crashlytics 当时保留，后来在 C13 里一并移除。上游若增删 `ChatVM` 构造参数，`ViewModelModule` 里的显式传参会冲突 |
| C3 | 移除赞助商推广，并允许删除所有提供商 | `DefaultProviders.kt`（删掉 AiHubMix、APIMart、随想AI网关、MaruCode 四项，新增 `REMOVED_SPONSOR_PROVIDER_IDS`）、`RecommendedProviders.kt`（整个文件删除，它只包含这四家）、`SettingProviderPage.kt`（断开右上角推荐 Sheet、去掉 AiHubMix 的「10% 优惠」标签、去掉列表删除菜单的 `builtIn` 门禁）、`SettingProviderDetailPage.kt`（去掉详情页删除按钮的 `builtIn` 门禁）、`ai/util/Request.kt`（去掉 `configureReferHeaders` 里给 aihubmix.com 加 `APP-Code` 的分支）、`DefaultSettings.kt`（去掉 AiHubMix 的 TTS 预置项，新增 `REMOVED_SPONSOR_TTS_PROVIDER_IDS`）、`SettingsNormalization.kt`（提供商不再补回；助手与 TTS 仍补回）、相关测试 | 不用这些推广渠道；预置项原本在界面上无法删除，规范化又会把删掉的补回来 | 行为改动，`Settings.providers` 字段与 DataStore key 未变，不触发迁移。三点副作用：其一，存量里的赞助商条目会在下一次设置变更写盘时从设置中清掉，用户在这四家填过的 API key 与自建模型随之消失，回退旧版本后不会再出现；其二，因为不再补回，上游以后新增的预置提供商不会出现在已有安装里，需要手动添加；其三，删掉提供商后 `chatModelId` 等可能悬空，查找返回 null 并按未选择处理。上游若重写 `withBuiltInDefaults`，必须确认仍不补回提供商，否则删除功能失效；上游若恢复 `RecommendedProviders.kt` 或推荐入口，需要重新删除。`setting_provider_page_recommend` 字符串已删除 |
| C4 | 清理赞助商图标、README 推广与启动赞助弹窗 | `utils/AIIconMatcher.kt`（删掉 aihubmix 的图标映射与匹配正则）、`app/src/main/assets/icons/aihubmix-color.svg`（删除）、`README.md`、`README_ZH_CN.md`、`README_ZH_TW.md`（删掉 Sponsors 章节）、`SettingPage.kt`（删掉启动 100 次后的赞助弹窗及其失效的三个 import 与 `launchCount` 订阅）、`SettingVM.kt`（删掉失去唯一读者的 `launchCount`）、弹窗的 4 条 `setting_page_sponsor_alert_*` 字符串 × 7 个 locale 已删除 | 不需要推广入口 | 捐赠页 `SettingDonatePage`、`SponsorAPI`、`Screen.SettingDonate` 与捐赠相关字符串保留不动。`Settings.sponsorAlertDismissedAt` 是已存储字段，未删除以免改动结构，只是不再有自动写入方，`DebugPage` 仍可手动改。刻意删除：`docs/sponsors/*.png` 与 `docs/donate.png` 早已无引用，这次一并删掉；合并时上游若更新这些图片会成 modify/delete 冲突，按删除为准。合并注意：三份 README 属上游高频改动文档，以上游最新版为准并重新删掉 Sponsors 章节；`SettingPage` 的弹窗区域与 `SettingProviderPage` 类似，上游若加回推广块，需要重新删除。`AIIconMatcher` 的图标映射本身不是推广入口，只删掉当年带推广性质的 aihubmix 那一条；上游后续新增的赞助商图标映射（3.1.1 的 `naapi`）保留 |
| C5 | 移除 TTS 与 STT（语音合成与语音识别） | **speech 模块删除 32 个文件**（`speech/src/main/java/me/rerere/` 下除 `tts/provider/TTSProviderSetting.kt`、`asr/ASRProviderSetting.kt` 外的全部）：ASR 侧 `ASRController.kt`、`ASRState.kt`、`ASRVoiceTurn.kt`、`AudioAmplitude.kt`、`providers/DashScopeASRController.kt`、`providers/MiMoASRController.kt`、`providers/OpenAIRealtimeASRController.kt`、`providers/StepASRController.kt`、`providers/VolcengineASRController.kt`、`providers/VolcengineASRProtocol.kt`；TTS 侧 `controller/AudioPlayer.kt`、`controller/TextChunker.kt`、`controller/TtsController.kt`、`controller/TtsSynthesizer.kt`、`model/PlaybackState.kt`、`model/TTSRequest.kt`、`model/TTSResponse.kt`、`provider/TTSManager.kt`、`provider/TTSProvider.kt`、`provider/TTSProviderException.kt`、`provider/providers/` 下 `ElevenLabsTTSProvider.kt`、`FishAudioTTSProvider.kt`、`GeminiTTSProvider.kt`、`GroqTTSProvider.kt`、`MiMoTTSProvider.kt`、`MiniMaxTTSProvider.kt`、`OpenAITTSProvider.kt`、`QwenTTSProvider.kt`、`StepTTSProvider.kt`、`SystemTTSProvider.kt`、`VolcengineTTSProvider.kt`、`XAITTSProvider.kt`；**测试删除 5 个**：`app/src/test/java/me/rerere/rikkahub/ui/pages/chat/VoiceSessionControllerTest.kt`、`speech/src/test/java/me/rerere/asr/ASRVoiceTurnTest.kt`、`speech/src/test/java/me/rerere/asr/providers/VolcengineASRProtocolTest.kt`、`speech/src/test/java/me/rerere/tts/provider/providers/MiMoTTSProviderTest.kt`、`speech/src/test/java/me/rerere/tts/provider/providers/VolcengineTTSProviderTest.kt`；**app 侧删除**：`ui/hooks/TTS.kt`、`ui/hooks/ASR.kt`、`ui/context/LocalTTSState.kt`、`ui/context/LocalASRState.kt`、`ui/components/ui/TTSController.kt`、`ui/components/ai/AsrButton.kt`、`ui/components/ai/VoiceModeRow.kt`、`ui/pages/chat/VoiceSessionController.kt`、`ui/pages/chat/VoiceMode.kt`、`ui/pages/chat/TTSAutoPlay.kt`、`ui/pages/setting/SettingSpeechPage.kt`、`ui/pages/setting/components/TTSProviderConfigure.kt`、`ui/pages/setting/components/ASRProviderConfigure.kt`、`data/ai/tools/local/TextToSpeechTool.kt`；**断入口**：`SettingPage` 语音项、`SettingPreferencesGeneralPage` 的「TTS 设置」区块、`ChatInput` 麦克风与录音权限音效、`FilesPicker` 语音模式项、`ChatMessageActions` 朗读、`AssistantLocalToolPage` 的 TTS 开关、`RouteActivity` 的 TTS/ASR 状态与 `Screen.SettingSpeech`、`BuiltinToolUIs` 的 TTS 渲染与 `ToolUIRegistry` 注册、`LocalTools` 不再产出 TTS 工具、构造参数去掉 `ttsManager` 与 `settingsStore`、`AppModule` 解绑 `TTSManager` 并调整 `LocalTools` 注入、`ChatPage` 与 `ChatVM` 的语音会话接线、manifest 的 `RECORD_AUDIO` 与 `TTS_SERVICE` 查询；**一并清掉的死代码与孤儿资源**：`AppEvent.Speak`、`ChatService.enqueueVoiceMessage`、`SettingsExt` 的 `getSelectedTTSProvider` 与 `getSelectedASRProvider`、`PermissionTypes.PermissionRecordAudio`、`MessageQueue` 的 reply 等待通道与 `MessageQueuePausedException`、`ChatService` 里对应的 reply 完成路径与 `failReplyWaiters` 调用、`ChatService.generationDoneFlow` 与 `ChatVM` 的转发（唯一消费者是 TTSAutoPlay，3.1.1 合并后才补删）、3 个 voice 队列测试、`res/raw/asr_start.mp3` 与 `asr_stop.mp3`，以及 7 个 locale 各 316 条无引用字符串（按归属拆开：媒体创作与生图 157 条属 C1、提供商推荐 1 条属 C3、赞助弹窗 4 条属 C4、语音相关 154 条属 C5） | 不用语音功能 | 保留存储与反序列化：`Settings.ttsProviders`、`asrProviders`、`selectedTTSProviderId`、`selectedASRProviderId`、`defaultTTSPlaybackSpeed`、`ttsOnlyReadQuoted`、`ttsOnlyReadOutsideBrackets`、`autoPlayTTSAfterGeneration` 等字段与 DataStore key 未动；`TTSProviderSetting`、`ASRProviderSetting` 逐字节未改；`DEFAULT_TTS_PROVIDERS`（只剩 SystemTTS）与 TTS 补回逻辑保留；`LocalToolOption.Tts` 枚举值保留，旧助手配置仍可反序列化；`speech` 的 `build.gradle.kts` 未精简，仍带 media3 等用不到的依赖。与 C4 不同，这里选择的是彻底清理而非保留孤儿，理由是语音功能的入口、实现、文案与音效已全部消失，留着孤儿只会增加维护时的误判。合并注意：app 侧语音代码是上游活跃区域（3.1.0 加过语音模式开关，3.1.1 又动了 `speech` 的 `TTSProvider.kt` 与 `ChatService` 的 reply 等待路径），恢复实现时需补齐上述入口；`speech` 只剩两个序列化类，上游改那一带时按本行保持删除。`SettingPage`、`SettingPreferencesGeneralPage`、`ChatInput`、`ChatMessageActions`、`ChatPage`、`ChatVM`、`ChatService`、`RouteActivity` 均为热区，冲突时以保留删除为准 |
| C6 | 移除聊天回复建议 | 生成侧：`ChatService` 的 `generateSuggestion` 方法与每轮结束时的调用、`ConversationMetaGenerator.generateSuggestions`；`data/ai/prompts/Suggestion.kt` 整个删除，`DEFAULT_SUGGESTION_PROMPT` 内联到 `Settings.kt` 作为字段默认值。UI 侧：`ChatList` 的 `onClickSuggestion` 参数与 `ChatSuggestionsRow`、`ChatPage` 的回调、`SettingModelPage` 的开关、`SettingModelPromptPage` 的建议提示词编辑项；7 个 locale 删掉四条建议文案，`setting_model_page_fast_model_desc` 去掉「chat suggestions」的说法 | 不用这个功能，每条回复都会多一次生成请求 | 保留存储与协议：`Settings.enableSuggestion`、`Settings.suggestionPrompt` 的字段与 DataStore key 不动，`Conversation.chatSuggestions` 是 Room 字段不能删，`WebDto.chatSuggestions` 与 `ConversationRepository.updateChatSuggestions` 一并保留，历史数据仍能读出与同步。`UsageSource.SUGGESTION` 枚举值保留，用量统计里不会再有新记录。`setting_model_page_suggestion_prompt_vars` 仍被标题提示词项使用，没有删 |
| C7 | 应用图标旋转 180 度 | `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` 下的 `ic_launcher.png`、`ic_launcher_background.png`、`ic_launcher_foreground.png`、`ic_launcher_monochrome.png`，5 个密度共 20 个 png 各旋转 180 度；`mipmap-anydpi-v26/ic_launcher.xml` 的结构不变 | 让桌面上装的这个版本与官方版一眼区分开，造型变成倒着的兔子 | 用 hflip 加 vflip 逐像素翻转，没有重采样，尺寸与 alpha 通道保持不变。上游的背景是浅上深下的纵向渐变，转后变成深上浅下，必须跟着转：legacy 的 `ic_launcher.png` 把背景与兔子烤在同一张图里，整图旋转后渐变必然反转，自适应路径下只有一起转两条路径的观感才一致。`monochrome` 必须一起做，否则 Android 13 的主题图标会退回官方样式。设置-关于页直接复用 `R.mipmap.ic_launcher`，无需单独改。上游 3.1.0 的 `bf04cde9` 调整过图标配色，此后上游再改这些 png 时会与本行冲突，处理方式一律保留我们旋转后的版本。它同时改了 `web-ui/public/favicon.ico`、`docs/icon.png`、`docs/img/poster.png`，这几处不在本行范围内 |
| C8 | 移除 Chatbox 与 Cherry Studio 导入 | `data/sync/importer/` 整个包删除（主源集 `ChatboxImporter.kt`、`CherryStudioProviderImporter.kt`，测试源集 `app/src/test/java/me/rerere/rikkahub/data/sync/importer/ChatboxImporterTest.kt`）；`BackupVM`（`restoreFromChatBox`、`restoreFromCherryStudio`、`ChatboxRestoreResult` 及随之失去读者的 `conversationRepository`、`filesManager` 两个构造参数）；`ImportExportTab`（备份页「从其他 APP 导入」分组的两个入口）；7 个 locale 的 `backup_page_import_chatbox_desc`、`backup_page_import_cherry_studio_desc`、`backup_page_import_from_chatbox`、`backup_page_import_from_cherry_studio`、`backup_page_import_from_other_app` 已删除 | 不用这两个第三方客户端，也不需要它们的迁移路径 | 这里删的是实现而不是断入口，理由见上面「改动尽量小，优先断入口」一节的补充说明。上游若重新加回这两个入口，需要重新删除 |
| C9 | 移除 S3 备份 | `data/sync/S3Sync.kt` 与 `ui/pages/backup/tabs/S3Tab.kt` 整个删除；`DataSourceModule` 的 `S3Sync` 绑定；`BackupVM`（`s3Sync` 参数、`s3BackupItems`、`loadS3BackupFileItems`、`testS3`、`backupToS3`、`restoreFromS3`、`deleteS3BackupFile`，以及 `S3BackupItem` 的 import）；`BackupPage`（S3 分页与 `HugeIcons.Bucket` import）；`backup_page_s3_backup` 与 `backup_page_s3_backup_files` 两条文案已删除；`setting_media_page_s3_desc` 里「与备份用的 S3 相互独立」半句在 7 个 locale 一并去掉 | 不用 S3 做备份，WebDAV 与本地文件已够用 | **S3 底层必须保留**：`data/sync/s3/S3Client.kt`、`AwsSignatureV4.kt`、`S3Config.kt`、`ui/components/ui/S3ConfigItems.kt` 仍被媒体创作的素材上传使用（`RemoteFileStore` + `Settings.uploadS3Config` + `SettingMediaPage`），`backup_page_s3_endpoint` 等 7 条连接配置文案也仍在使用。`Settings.s3Config` 是已存储字段，未删除以免改动结构，只是不再有读写方。合并注意：上游若改 `S3Config` 或 `S3ConfigItems`，要确认媒体页那条路径仍然可用 |
| C10 | 新增从官方 RikkaHub 导入 | `ImportExportTab`（新增「从官方 RikkaHub 导入」分组与 `ImportSource.OFFICIAL`，与本地导入共用同一个确认对话框与 `openDocumentLauncher`）；7 个 locale 新增 `backup_page_import_from_official` 与 `backup_page_import_official_desc` | 改包名后本应用与官方应用并存，需要一个明确的迁移入口 | 官方备份与本应用的归档格式相同（`settings.json` + `rikka_hub.db` + 附件目录），所以复用同一条 `stageRestore` 流程，入口只负责引导用户先在官方应用里导出备份。`JsonInstant` 开了 `ignoreUnknownKeys`，官方备份里的额外字段不会导致解码失败。这一行只管导入方向；反过来的「导出官方兼容备份」是并列的第二个出口，由 C18 落地，现在遇到 schema 分叉会直接失败 |
| C11 | 导出备份显示进度 | `BackupManager.createBackup`（新增可选 `onProgress` 回调与 `BackupProgress`，附件先收集成列表再写入）；`WebDavSync.prepareBackupFile`（透传回调）；`BackupVM`（`exportProgress` 状态与 `finally` 清理）；`ImportExportTab`（打包阶段显示确定进度，写入目标文件阶段显示不确定进度）；7 个 locale 新增 `backup_page_export_packing` 与 `backup_page_saving` | 附件多时导出要等很久，没有进度不知道卡在哪一步 | 打包阶段按条目推进：`settings.json`、数据库快照、每个附件各算一条，`total` 与 `advance()` 的调用点一一对应。改为先收集附件再写入是为了给出确定的总数，代价是「列出」与「写入」之间文件消失的窗口从一个文件扩大到整个打包阶段；`PARTIAL_SUFFIX` 过滤与 `resolveInside` 校验都保留 |
| C12 | 改包名与应用名 | `app/build.gradle.kts`（`applicationId` 改为 `top.lemoneko.rakkihub`）、`AndroidManifest.xml`（shortcut deep link 的 scheme 从 `rikkahub` 改为 `rakkihub`）、`res/xml/shortcuts.xml`（`android:data` 同步）、7 个 locale 的 `app_name` 改为 `RakkiHub`（德语在 3.1.1 之后补上，见「文案与 locale」一节）、`ExampleInstrumentedTest`（不再写死旧包名，改与 `BuildConfig.APPLICATION_ID` 比对） | 自主 fork 阶段要与官方应用并存，安装标识与私有数据目录必须独立 | `namespace` 保持 `me.rerere.rikkahub` 不动，类名、R 类与 `shortcuts.xml` 的 `targetClass` 都不受影响；`applicationIdSuffix = ".debug"` 保留，debug 包名是 `top.lemoneko.rakkihub.debug`；`${applicationId}` 拼的 authorities 与 `BuildConfig.APPLICATION_ID` 自动跟随。当时同步改过 `google-services.json` 的两个 package_name 与 CI 的 secret，C13 把 Firebase 整体移除后这两处都不再存在 |
| C13 | 移除 Firebase 与 google-services | `app/build.gradle.kts`（两个 plugin alias、`firebase-bom` 与 `firebase-crashlytics` 依赖、冗余的 `barcode.scanning`）、根 `build.gradle.kts`（两个 plugin alias）、`libs.versions.toml`（firebase 三个版本、四个库定义、`google-services` 插件、`barcodeScanning`）、`AppModule.kt`（两处 import 与 `single { Firebase.crashlytics }` 绑定，这个绑定本来就没有消费者）、本地 `app/google-services.json`、`README.md`/`README_ZH_CN.md`/`README_ZH_TW.md`（删掉「需要 google-services.json 才能构建」的提示）、CI 的 `GOOGLE_SERVICES_JSON` secret 与 workflow 里的写入步骤；顺带为 `ImageUtils.kt` 补上显式的 `androidx.exifinterface` 依赖 | 不需要崩溃上报，也不想让应用依赖 Google 的运行时服务 | 代码与构建脚本里已无 `com.google.firebase` 的引用；checked-in 的 baseline profile（`app/src/main/generated/baselineProfiles/`）里还留着 firebase 与 crashlytics 的历史条目，也有 C1/C5 删掉的 MediaCreation、ImgGen 与 TTS 类。这些条目指向已经不存在的类，ART 会跳过，不影响运行；重新生成能自愈，但在这台 K60 上跑不通（Gradle 装 nonMinifiedRelease 被 HyperOS 拦下，用 su 装包后 instrumentation 又卡住并以 `DeadObjectException` 退出），真要重做需要换模拟器或干净设备。扫码在 3.1.1 由上游换成 CameraX 加 zxing，`quickie-bundled`、`barcode.scanning` 与 ML Kit 的传递依赖都不再存在；`androidx.exifinterface` 由上游在同一处显式声明，合并时删掉重复的那一行。上游若重新引入 Firebase，需要重新删除 |
| C14 | 移除设置页的分享本应用入口，统一品牌文案 | `SettingPage.kt`（about 分组里的分享 item、`shareText`/`share`/`noShareApp` 三个局部变量与三个失效 import）、7 个 locale 的 `setting_page_share`、`setting_page_share_desc`、`setting_page_share_text`、`setting_page_no_share_app`；品牌文案：`setting_page_web_server_desc`（7 个 locale）、`oauth` 模块的 4 条回调提示（7 个 locale）、`web-ui` 的 `page.json` 标题与描述、`conversation-sidebar.tsx` 的侧边栏标题 | 分享出去还自称官方品牌并指向上游官网，没有意义；其余文案跟着应用名一起改成 RakkiHub | `search-picker.tsx` 里的 `rikkahub: "RikkaHub"` 是搜索服务的显示名，指官方接口，保留；`backup_page_import_from_official` 的「Import from RikkaHub」指官方应用，同样保留 |
| C15 | 移除更新检查 | `utils/UpdateChecker.kt` 与 `ui/components/ui/UpdateCard.kt` 整个删除，随前者一起消失的还有只服务于版本比较的 `Version` 类与 `app/src/test/java/me/rerere/rikkahub/utils/VersionTest.kt`（89 行、11 个用例）；`AppModule` 的 `UpdateChecker` 绑定与 import、`ViewModelModule` 的 `updateChecker` 实参、`ChatVM` 的构造参数与 `updateState` 流、`ChatDrawer` 的更新卡片与暂停计时逻辑、`SettingPreferencesNotificationPage` 的更新提醒设置项与暂停对话框；8 个 locale 各删 13 条文案（5 个 `update_card_*`、6 个 `setting_update_reminder_*`、2 个 `setting_display_page_show_updates_*`），`setting_page_preferences_notification_desc` 去掉「更新提醒」那一段 | 更新源指向官方，下载下来是官方包，装上去只是多出第二个应用；要新版本自己去看 | 保留 `Settings.displaySetting.updateCheckDisabledUntilEpochMillis` 这类已存储字段，DataStore key 不动，只是不再有读写方。`distribution` flavor 与 `BuildConfig.IS_PLAY_STORE` 保留：那是上游的构建结构，删掉会抬高每次合并的成本，现在没有代码读这个字段。这里和 C8 一样删的是实现而不是断入口，理由见「改动尽量小，优先断入口」一节的补充说明。合并注意：上游若重构 `UpdateChecker` 或恢复正常更新流程，需要重新删除 |
| C16 | 清除导出内容里的站点水印 | `ui/pages/chat/ConversationExport.kt`（导出会话图片时左下角不再拼 `rikka-ai.com`，只留时间）、`ui/components/richtext/Mermaid.kt`（导出 Mermaid 图时不再在画布底部绘制站点地址） | 导出的内容是自己生成的，不该带上游站点痕迹 | 只删绘制水印的那几行，图表配色与布局不动，`onBackground` 仍被 mermaid 自身的配置使用。合并注意：上游若再加回水印或其他署名，重新删掉 |
| C17 | 冻结官方备份的结构快照 | 新增 `data/sync/official/`：`OfficialDatabaseSchema`（14 张表、106 个列、14 个索引、5 个外键、3 处自增）、`OfficialBackupLayout`（归档条目名、为兼容保留的 WAL 侧条目、`launchCount`、四个附件目录与其中两个嵌套目录），以及三个校验测试。配套改动：`BackupManager` 新增 `SETTINGS_ENTRY` 常量并让写入、恢复与 `PendingRestore` 三条路径共用它，另把 `ATTACHMENT_FOLDERS`、`NESTED_ATTACHMENT_FOLDERS`、`LAUNCH_COUNT_KEY` 从 private 放宽到 internal 以便对账 | 导入器和官方兼容导出都需要一份「官方那侧认识什么」的描述，且不能随我们自己的 Room 定义漂，否则一旦我们改结构，导入器就读不懂官方库 | 快照只描述官方那侧，数据从 `app/schemas/.../32.json` 生成。`message_fts*` 这类由手写 SQL 建的全文索引表不在其中：`AppDatabaseFactory` 打开数据库时只保证表存在，索引内容要靠 `ConversationRepository.rebuildAllIndexes` 重建，所以导入完成后要跑一次重建，否则搜索是空的。快照测试与 `32.json` 逐表、逐列、逐索引、逐外键对账，并断言仓库里最高的 Room schema 版本等于快照版本；布局测试与 `BackupManager`、`DatabaseBackup` 的常量对账；settings 测试钉住官方解码器的 `ignoreUnknownKeys` 行为。官方升级会触发版本断言，我们自己改了归档布局会触发布局断言；自家 schema 与官方分叉后版本那条要重做。导入器当时还没实现，官方兼容导出的骨架由 C18 落地 |
| C18 | 新增官方兼容的导出出口 | 新增 `BackupTarget`（`SELF` / `OFFICIAL`）、`OfficialDatabaseCompatibility`（判定我们的库还在不在官方形状上，比较版本与 identity hash，配 `OfficialDatabaseCompatibilityTest`）、`OfficialDatabaseAdapter`（一致时原样返回数据库文件，分叉时抛 `OfficialExportUnsupportedException`）；`BackupManager.createBackup` 与 `WebDavSync.prepareBackupFile` 加目标参数，`BackupVM.exportToFile` 透传，只有 `SELF` 会更新备份提醒的时间戳；`ImportExportTab` 的导出分组加第二个入口，与自家导出的区别只在数据库，目标用 `rememberSaveable` 存，避免选择文件期间被回收后退回自家格式；8 个 locale 新增 `backup_page_export_official`、`backup_page_export_official_desc`、`backup_page_export_failed`、`backup_page_export_schema_diverged`；`BackupManagerTest` 补一条官方目标的 androidTest | 改结构之后自家备份官方读不了，需要一个能把数据交回官方的出口；它和自家导出并列，不是把现有导出改掉 | 当前双方 schema 一致，两个出口的产物几乎相同：适配层只做「一致就原样返回、分叉就失败」，裁剪逻辑等真改结构时再补。失败是刻意的，静默产出官方打不开的备份要等到恢复时才发现；分叉走单独的提示文案，技术原因只进日志。导出失败时会尝试删掉选择位置时建出的空文档，尽量不留 0 字节文件（删除本身可能被 provider 拒绝，是 best-effort）。`settings.json` 不裁，官方解码器的 `ignoreUnknownKeys` 会忽略多余字段，这一点由 `OfficialSettingsCompatibilityTest` 钉住。WebDAV 出口暂时只产出 `SELF` 格式。合并注意：上游若改 `createBackup` 的签名或备份页的导出分组，第二个入口要跟着调整 |
| C19 | 附件按替换语义导入，导入的原始备份留底 | `PendingRestore.buildEntries` 与新增的 `buildRemovalEntries`（对归档里实际存在的附件目录，为 live 上有、payload 里没有的文件合成 `install = false` 的删除条目，复用现有 journal 与 rollback；只在数据库也一起替换时才做）；新增 `ImportedBackupArchive`（原子写入 `noBackupFilesDir/imported-backups/`，`stageRestore` 成功后才归档）；`WebDavSync.restoreFromBackupFile` 接上归档；`PendingRestoreTest` 补五条、新增 `ImportedBackupArchiveTest` 四条 | 导入官方备份时附件是合并不是替换，目标目录里多出来的文件永远留着，启动时 `syncManagedFiles` 还会把它们重新登记进 `managed_files`，技能目录里的旧技能也会被扫到；另外导入完原始 zip 就没了，写坏数据没有回退来源 | 删除条目只用文件级条目，不做目录级 rename：journal 与 rollback 都按路径记录单个文件，目录条目得为它们另加一套状态。`install = false` 的条目让 apply 把目标移进 `originals/`、rollback 再移回来，现有 journal 一行都不用改。只导附件时保持叠加语义，因为现有库还引用着 live 上的文件。空目录无法表达、`upload`/`fonts` 的平铺与递归不对称，都记在 `importer.md` 的已知限制里。归档放 `noBackupFilesDir` 而不是 `filesDir`，后者整个目录被 web 路由暴露。导入器本体当时还没实现，见 `importer.md` |
| C20 | 导入器读取层 | 新增 `data/sync/official/OfficialDatabaseReader.kt`（按冻结快照只读打开官方库，打开时核对 `user_version`、每张表、每列，行值按 affinity 读出并校验实际存储类型）、`OfficialSchemaVerifier.kt`（结构与类型判断抽成纯函数，不依赖 Android）；`OfficialSchemaVerifierTest` 七条 JVM 测试、`OfficialDatabaseReaderTest` 七条 androidTest | 导入器要用裸 SQL 读官方库，不能与我们的 Room schema 耦合；这是 `importer.md` 分步计划的第一步 | 读取层本身不接入口；写入与分流由 C23 接上。合并注意：四个 Kotlin 文件都是新增，上游不会碰；上游升 schema 后要重新生成 `OfficialDatabaseSchema` 快照，读取层会跟着新快照读。设备限制见 `importer.md` 的已知限制，样本缺口见 `OfficialDatabaseReaderTest` 的 KDoc |
| C21 | 导入器写入层 | 新增 `data/sync/official/OfficialTableOrder.kt`（从快照的外键推出写入顺序，循环外键报错）、`OfficialDatabaseWriter.kt`（一个事务把读出的行写进新建的空库，行级照抄不动 JSON 字段）、`OfficialUrlRewriter.kt`（把官方备份里带包名的绝对 `file://` 附件引用重定向到当前安装）；`OfficialUrlRewriterTest` 七条 JVM 测试、`OfficialTableOrderTest` 四条 JVM 测试、`OfficialDatabaseWriterTest` 四条 androidTest | 读取层只负责读，导入器还要把数据写进我们自己结构的库 | 只写新建的空库，不接入口、不碰正在用的数据。合并注意：这几个文件都是新增，上游不会碰。设备上跑 androidTest 前要先 `rm -f` 目标文件再 `adb push`，否则推的是旧字节 |
| C22 | 导入器支持多版本官方快照 | 新增 `OfficialSnapshots`（版本到快照的注册表）、`OfficialSchemaTypes`（表/列/索引/外键的类型，抽出来避免快照与注册表循环初始化）、`OfficialSnapshotV31` 与 `OfficialSnapshotV32`（从各自的 schema json 生成）；`OfficialDatabaseSchema` 收缩成「当前版本 + v32 的别名」；读取层按库里的 `user_version` 选快照，不在清单里的版本明确失败；`OfficialSchemaVerifier` 改成接受选中的表定义 | 读取层要能读官方 3.1.0（v31）的备份，单版本快照做不到；验收也要用同一套读取逻辑比对两份样本。恢复路径上 v31 备份走 Room 迁移链（版本比我们低），快照服务的是读取层、验收与之后的字段映射层 | v31 的 identity 是 `a428043e...`，与用户那份官方 3.1.0 备份完全一致，逐表对比 14 张表的列零差异。v16（官方 2.1.6）明确不支持：它和 v31 之间隔了十几个版本。合并注意：两个快照文件由 `tools/gen-official-snapshot.ps1` 生成，官方升版时重新生成并在注册表加一行 |
| C23 | 导入器接上恢复流程 | 新增 `OfficialRestorePlanner`（按 `user_version` 与 schema 指纹决定迁移链 / 转换 / 拒绝，纯函数）、`OfficialDatabaseHeaders`（只读读两处元数据）、`OfficialDatabaseConverter`（官方库转成当前版本库，动手前对账目标表与列，缺表或缺列直接拒绝）；`BackupManager.stageRestore` 先 `normalize` 再分流，转换后替换暂存库并继续原流程；`OfficialRestorePlannerTest` 六条 JVM 测试、`OfficialSnapshotFilesTest` 两条（v31/v32 快照与各自 schema 文件逐列对账）、`OfficialDatabaseConverterTest` 两条 androidTest、`BackupManagerTest` 补两条（同版本不同结构先转换再发布、高版本明确拒绝）；`RikkaHubApp` 在恢复成功后后台重建全文索引；快照生成脚本移到 `tools/gen-official-snapshot.ps1`（`build/` 被 gitignore，脚本放那里不可复现） | 迁移链只能往上升，官方库版本高于我们或同版本不同结构时打不开；这是「导入器可用」的最后一段接线 | 判定在读任何业务表之前完成，`ourVersion` 与 `ourIdentity` 都用编译进应用的常量，不从活库读（升级后首次启动时活库可能还停在旧版本）。高版本一律拒绝而不是回落到迁移链：迁移链降不了版本，而官方升版通常是加列，硬写会在插入时崩。同版本但缺 schema 指纹也拒绝，来源判不出来时不猜。合并注意：`stageRestore` 是热区，上游改恢复流程时按本行复核顺序（normalize → 分流 → 转换 → 打开校验 → checkpoint） |
| C24 | 导入器验收：与独立算出的期望值逐表比对 | 新增 `tools/backup-expectations.py`（用 Python 的 sqlite3 直接读原始备份，按 schema 的列顺序与主键排序，算每张表的行数与规范化 SHA-256）、`OfficialSampleAcceptanceTest`（androidTest，两层：直接读原始备份、以及经转换器写成当前版本库后再读，各自与期望值逐表比对；没有样本就跳过）；`importer.md` 的验收一节补上规范化规则与可复制的命令 | 没有独立期望值时，「读取层读对了」只是自证；roadmap 也要求「只编译通过不算数」 | 规范化是「类型字节 + 4 字节小端长度 + 载荷」，行间 `0x1e`，两侧各实现一遍并逐字节对齐。样本含私人数据，放仓库外，测试用 `Assume` 跳过，CI 不会因此失败。v31 与 v32 两份样本都跑过（各 14 张表全过）；v16 明确不支持，不纳入样本集。合并注意：这两处都靠 `app/schemas` 的列顺序，上游升 schema 后要重新生成期望值 |
| C25 | 同版本备份也重写附件路径 | 新增 `OfficialForeignAttachmentPaths`（只探测：库里的文本列是否还带着别的安装的 `file:///data/.../files/` 前缀）；`BackupManager.stageRestore` 在判定为迁移链之后再探一次，命中就改走转换器，由写入层把路径重写成本机；`BackupManagerTest` 加一条回归用例 | 同版本、结构一致的备份走迁移链，而迁移链不改数据。官方备份、或我们自己在别的包名（debug / release、改过包名的构建）下产生的备份都带着生产者的绝对路径，装进当前安装后附件全部失配 | 探测只看快照覆盖的文本列，读不到快照就不猜、直接走迁移链。代价是命中时要把整库复制一遍（转换器不改原库），换来的是路径一定被重写 |
| C26 | 整份备份的附件验收 | `tools/backup-expectations.py` 增加 `--settings` 与 `--zip`：只统计 settings 的助手与服务商条数，并从归档里算每个附件的路径、大小、SHA-256；新增 `OfficialBackupRestoreAcceptanceTest`（androidTest）：把真实归档走一遍 `stageRestore`，逐个断言附件的存在性与内容摘要，并确认解压出来的附件不多不少；另加 `RestoreAppliedOutcomeTest`：恢复应用之后 live 的 `upload/` 必须正好是归档那一份，上一次安装残留的文件要被清掉；settings 断言补上助手与服务商的 id 集合、当前助手、当前模型 | 表数据在库里能验，附件与 settings 不行：附件是文件、settings 不在任何表里。没有这一层，导入把附件丢了或换了内容都没人发现 | 样本是完整备份（含私人数据），放仓库外、设备上，缺了就跳过。settings 的期望值必须从**同一份归档**里取：曾用另一份样本的 settings.json 算期望值，得到 11 助手 / 37 服务商，而归档本身只有 5 / 29，看起来像导入丢了数据；实际是期望值来源错了，处理链没问题 |
| C26 | 全文索引启动自检与重建完整性标记 | `MessageFtsManager.isEmpty`（索引里有没有内容）与 `message_fts_state` 表（记 `rebuild_complete`，重建开始置 0、成功收尾置 1）、`SearchIndexHealth.needsRebuild`（纯函数：有会话且（索引空或上次重建没跑完），六条 JVM 测试）、`ConversationRepository.needsSearchIndexRebuild`；`RikkaHubApp` 在恢复成功那次与常规启动都跑自检 | 恢复被中断、或全量重建中途被杀，都会留下空或残缺的索引：重建第一步就是清空，只靠「索引非空」发现不了残缺。没有自检时搜索结果会永久缺一部分，用户也不知道该重建 | 标记表与 `message_fts*` 同属 `onOpen` 里手写 SQL 建的表，不进 Room schema、不影响 identity 校验；恢复换库后若缺该行会触发一次重建，符合预期 |
| C28 | 全文索引重建的完整性标记 | `message_fts_state` 表（`MessageFtsManager` 自己建，不进 Room schema）记一个 `rebuild_complete`；重建开始时置 0、成功收尾置 1；`SearchIndexHealth.needsRebuild` 增加 `rebuildComplete` 参数，判据变成「有会话且（索引空或上次重建没跑完）」 | 原来只能发现空索引。重建是「先清空再逐会话写」，写完第一张就被杀时索引非空但缺内容，自检会跳过，搜索永久少一部分而且用户不知道 | 标记存在 fts 自己的表里，不动 Room schema 与 DataStore 键。判据仍有一处理论缺口：如果重建没跑过、但索引里恰好有内容，会重建一次，代价是多一次扫描 |

## 文案与 locale

locale 现在有 8 份资源目录：`values`、`values-ar`、`values-de`、`values-ja`、`values-ko-rKR`、`values-ru`、`values-zh`、`values-zh-rTW`。德语是上游 3.1.1 加的。`app/build.gradle.kts` 的 `localeFilters` 列了 9 项（`en`、`ar`、`de`、`ja`、`ko`、`ko-rKR`、`ru`、`zh`、`zh-rTW`），`ko` 没有对应目录，但会作为独立的 locale 配置出现在 APK 里。加语言时两处一起改。

删除或新增文案时八份都要同步。合并上游之后，先按 `values/strings.xml` 的 key 集合过滤其余七份，因为上游会给已删除的 key 补翻译，德语尤其明显（本次合并多出 331 条）。过滤后的判据是：除 `values-de` 与 `values-zh` 外，每份只剩「上游新增但还没翻译」的缺口。

品牌改写按 key 查不出来：C12 的 `app_name`、C14 的 `setting_page_web_server_desc` 与 `oauth` 的四条 `oauth_callback_*_hint`，在新增 locale 里会以官方品牌出现。合并后要逐条重放成 `RakkiHub`。判据不靠数行数，而是排除掉有意保留的几条 key 之后必须为空：

```powershell
rg -n 'RikkaHub' app/src/main/res oauth/src/main/res -g '**/values*/strings.xml' |
  Select-String -NotMatch 'name="(backup_page_import_from_official|backup_page_import_official_desc|backup_page_export_official|backup_page_export_official_desc|backup_page_export_schema_diverged)"'
```

放行的这五条都指官方应用：两条导入引导、两条导出说明，以及分叉时的失败提示。

下面清单里写「7 个 locale」或具体份数的条目，指的是做那项定制时仓库里的份数，德语是上游 3.1.1 才加的；同步这类改动时按当前的 8 份处理。

`values-de` 已经不缺条目，本分支新增的很多文案直接写在 Compose 里、还没进 `strings.xml`，其中附件清理与扫描相关的一组属于欠账。反方向还有缺口：`values-ar`、`values-ja`、`values-ko-rKR`、`values-ru`、`values-zh-rTW` 各缺 3 条上游自己没翻的压缩上下文文案（`chat_page_compress_keep_recent` 及其描述、`chat_page_compress_not_enough_messages`），那属于上游范围。新加文案时八份一起补，用 `tools/i18n`。

## 构建

本机构建需要以下几个非仓库内的前提，缺任何一项都会在特定任务上失败：

- JDK 必须是 HotSpot。默认 `JAVA_HOME` 指向 OpenJ9 的 JDK 21，KSP 会抛 `NoClassDefFoundError: jdk.jfr.Event`。改用 `E:\WorkBench\Java\jdk-23.0.2`
- `local.properties` 提供 release 签名（被 gitignore）
- `material3/material-color-utilities` 是 submodule，未初始化时 `:material3:compileDebugKotlin` 报 `Unresolved reference 'dynamiccolor'`

```powershell
$env:JAVA_HOME='E:\WorkBench\Java\jdk-23.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleGithubDebug :app:assembleGithubRelease
```

上游 3.1.1 引入 `distribution` 维度的 product flavor（`github` / `play`），任务名与产物路径随之带上渠道名。用 `github` 渠道：`play` 只给上架 Google Play 用，本仓库不需要。

产物位于 `app/build/outputs/apk/github/debug/` 与 `app/build/outputs/apk/github/release/`，各有 universal 与分 ABI 三种。debug 的 applicationId 是 `top.lemoneko.rakkihub.debug`，release 是 `top.lemoneko.rakkihub`，两者与官方版的 `me.rerere.rikkahub` 都不同，可以同时安装。只有包名相同才需要签名一致，所以 release 自签名在这里不构成与官方版共存的障碍。

## 测试环境噪声

在 Windows 上有一批测试必然失败，属于测试本身假设类 Unix 环境，与代码改动无关。判据是「没有新增失败」，不是「全部通过」。app 模块 468 个用例里失败 7 个：

| 测试类 | 失败数 | 原因 |
| --- | --- | --- |
| `data.datastore.SettingsStoreTest` | 6/12 | DataStore 在 Windows 上不能 rename 覆盖被占用的目标文件 |
| `data.ai.account.ProviderAccountManagerTest` | 1 | 同源问题，用例末尾写设置时撞上 DataStore 的 rename |

workspace 模块另有 `workspace.ExampleUnitTest`（4/10，硬编码执行 `/bin/sh`，Windows 上 `CreateProcess error=2`）与 `workspace.RootfsInstallerTest`（1/3，软链断言期望 `/bin/busybox`）失败。

上游 3.1.1 起 `highlight` 模块已并入 BetterText，原先那里由 CRLF 引发的 `LanguageFixtureTest` 整批失败不再存在。
