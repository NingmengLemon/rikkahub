# 定制分支

本仓库基于上游 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 维护一组个人定制：去掉不需要的功能，以及按需补充的小功能。

定制与上游持续同步，因此这里记录的不是一次性改动，而是长期维护所需的约定：分支模型、同步流程、改动边界、以及每项定制的落点。

## 分支模型

| 分支 | 用途 |
| --- | --- |
| `master` | 只跟随上游，不承载任何本地提交，任何时刻都应可 fast-forward |
| `custom` | 唯一工作分支，全部定制提交集中在这里 |

远端配置：

| 远端 | 仓库 | 用途 |
| --- | --- | --- |
| `origin` | NingmengLemon/rikkahub | 本仓库 fork，可推送 |
| `upstream` | rikkahub/rikkahub | 官方仓库，只读；其 push 地址已改为 `DISABLED_no_push_to_upstream`，误推会直接失败 |

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

## 定制原则

### 改动尽量小，优先断入口

删除功能时优先改条件、去掉入口，而不是删掉整段实现。上游改同一功能的实现时，条件行通常不会冲突，删光的实现则会在每次上游改动时反复冲突。

确实要删整个文件时，把路径记进下面的定制清单，合并时专门检查上游对该文件的改动。

### 不改已存储数据的结构

这条是为了让升级不触发迁移、回退不丢数据，也让与上游的数据结构演进保持可合并。

- Room：不新增表、不新增列、不改已有列含义
- DataStore / Settings：不改已有 key 的语义；新增字段必须带默认值
- 序列化模型（`UIMessage`、`Conversation`、`Assistant` 等）：可以新增带默认值的字段，旧数据仍能读出；不改已有字段含义

需要"记录额外状态"时，优先考虑运行时内存或带默认值的新字段，而不是改已有结构。

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
- 关注三件事：是否引入回归、是否触碰已存储数据的结构、是否符合本文件的定制原则（尤其是是否用了删除实现而非断入口）。
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

两个口径会给出不同结论。C1 与 C2 按全历史属于动了最密集的两个文件，按近窗口则是中等热度，而近期最热的 `ChatService.kt` 并未触碰。无论按哪个口径，这三处的具体冲突点都写在清单的「合并注意」列，合并时优先看那里。

上游另有两个长期未合并的重构分支 `refactor/chat-service`（停在 08-26）与 `refactor/ai-stream`（停在 08-12）。它们目前不构成冲突来源，但若上游重启这类重构，上述文件会整体重写，届时应暂停对热区的新定制。

## 定制清单

每次新增定制都往表里加一行。合并冲突时按表逐项复核，确保定制意图没有在合并中被静默丢掉。

| 编号 | 内容 | 落点 | 理由 | 合并注意 |
| --- | --- | --- | --- | --- |
| C1 | 移除媒体创作与生图 | `RouteActivity`（删 `Screen.ImageGen`、`Screen.MediaCreation`、`Screen.MediaCreationSessions` 及其 entry 与 intent 处理，`Screen.SettingMedia` 的 entry 保留）、`ChatDrawer`（侧边栏两个入口）、`ViewModelModule`、`RikkaHubApp`（启动恢复）、`SettingPage`（媒体设置入口）、`shortcuts.xml`、`AndroidManifest`；删除 `ui/pages/mediacreation` 与 `ui/pages/imggen` | 不使用 | 刻意保留未动：`data/model/MediaCreation.kt`、`data/files/MediaCreationFiles.kt`、`data/db` 下的实体与 DAO、`data/repository/MediaCreationRepository.kt`、`service/MediaCreationService.kt`、`service/MediaCreationForegroundService.kt`、整个 `mediagen/` 模块、manifest 的 `<service>` 声明、`Settings.mediaGenerationProviders`、`SettingMediaPage.kt` 及其路由。合并时不要因为界面上看不到就顺手删掉这些。两处冲突风险：上游若重排 `RouteActivity` 的 `Screen` 与 entry 列表；上游 `feat/mediagen` 分支（16 提交，2026-10-05 仍有活动）正在改 `MediaCreationService.kt` 与被删除的 mediacreation UI，合并时按该分支进度判断是否恢复部分实现 |
| C2 | 移除 Firebase Analytics | `app/build.gradle.kts`、`AppModule`、`ChatVM`、`ViewModelModule` | 自用不需要埋点上报 | Crashlytics 保留，`google-services` 插件与 `google-services.json` 仍需存在。上游若增删 `ChatVM` 构造参数，`ViewModelModule` 里的显式传参会冲突 |

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
| `data.datastore.SettingsStoreTest` | 6/10 | DataStore 在 Windows 上不能 rename 覆盖被占用的目标文件 |
| `workspace.ExampleUnitTest` | 4/10 | 硬编码执行 `/bin/sh`，Windows 上 `CreateProcess error=2` |
| `workspace.RootfsInstallerTest` | 1/3 | 软链断言期望 `/bin/busybox`，Windows 路径分隔符给出 `bin\busybox` |

`highlight` 的 `LanguageFixtureTest` 会因工作区文件被 checkout 成 CRLF 而整批失败。仓库的 `.gitattributes` 要求 `eol=lf`，但全局 `core.autocrlf=true` 的前提下，旧 checkout 的文件可能仍是 CRLF。表现是 30 个用例全部报 `highlight.js parity` 不匹配。修复：

```powershell
Remove-Item -Recurse -Force highlight\src\test\resources\hljs
git checkout -- highlight/src/test/resources/hljs
```
