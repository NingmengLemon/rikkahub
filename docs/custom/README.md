# 定制分支

本仓库基于上游 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 维护一组个人定制：去掉不需要的功能，补充少量小功能。

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

### 避开上游热区

`GenerationLoop.kt`、`ChatService.kt`、`FilesPicker.kt` 最近被上游高频修改（工具调用统计、上下文压缩、输入面板），在这些文件里做定制冲突成本最高。能放到别的层就不要放这里。

上游另有两个长期未合并的重构分支 `refactor/chat-service`（停在 08-26）与 `refactor/ai-stream`（停在 08-12）。它们目前不构成冲突来源，但若上游重启这类重构，上述文件会整体重写，届时应暂停对热区的新定制。

## 定制清单

每次新增定制都往表里加一行。合并冲突时按表逐项复核，确保定制意图没有在合并中被静默丢掉。

| 编号 | 内容 | 落点 | 理由 | 合并注意 |
| --- | --- | --- | --- | --- |
| 暂无 | | | | |

## 构建

本机构建需要以下几个非仓库内的前提，缺任何一项都会在特定任务上失败：

- JDK 必须是 HotSpot。默认 `JAVA_HOME` 指向 OpenJ9 的 JDK 21，KSP 会抛 `NoClassDefFoundError: jdk.jfr.Event`。改用 `E:\WorkBench\Java\jdk-23.0.2`
- `app/google-services.json` 是占位文件（被 gitignore）。缺失时 `:app:processDebugGoogleServices` 失败。占位配置让 Firebase Analytics 与 Crashlytics 不上报，不影响应用运行
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
