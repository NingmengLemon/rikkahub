# 官方备份导入器

阶段 1 的主体。目标：官方客户端导出的备份，能导入本 fork，且不要求官方库的结构与本仓库的 Room schema 对得上。

## 现状与优先级

导入器本体**与第一次改 Room schema 是同一批工作**，不提前单独做。理由是一条实测事实：官方 v32 的 schema identity 与本仓库的完全相同（`c64deeb13766eaf6528a2f4f6965c836`），所以真实的官方备份进来时，分流会判定它「就是本仓库的库」，直接走现有的 `stageRestore`，导入器不会被触发。

导入器真正被需要，是在我们改动 Room schema 之后。在那之前实现它，没有真实场景可用，验收只能靠伪造样本（改掉 identity 或版本的官方备份），而伪造样本验证不了真实路径。反过来，改 schema 又要靠导入器保命，所以两者只能同一批做，顺序是导入器先落地并验证，再改结构。

不依赖分叉的部分陆续落地了：

1. **附件目录替换**：见「附件替换」一节。
2. **归档原始 zip**：见「归档」一节。
3. **读取层**：见「读取层」一节。

## 现有的恢复流程

`BackupManager.stageRestore` 在**暂存阶段**就把库打开并迁移，不是等到启动：

1. 解压归档到 `PendingRestore.createStagingDirectory()` 出来的 `preparing-*` 目录。
2. 对暂存库跑 `DatabaseBackup.normalize`，然后用 `AppDatabaseFactory.create(context, stagedDatabase.absolutePath)` 打开它。Room 在这里校验 identity hash，并沿迁移链把低版本库升到当前版本；版本高于本仓库时走 `onDowngrade` 抛错。
3. 在 `try` 里 `DatabaseBackup.checkpoint(room.openHelper.writableDatabase)` 把 WAL 合进主文件，`finally` 里 `room.close()`，最后 `removeSidecars` 清掉 `-wal`/`-shm`。
4. `PendingRestore.publish(staging)`：只把 `preparing-*` 改名为 `pending`，此时还没落盘。
5. 重启后 `PendingRestore.apply` 在 Koin、Room、SettingsStore 初始化之前建 journal、原子替换文件；失败则整体 rollback。

失败都在 publish 之前发生，staging 在 `finally` 里删掉，不会污染正在用的数据。

## 导入器要解决的问题

迁移链只能把**结构上可迁移**的库升上来。两种情况它处理不了：

- 官方库版本高于我们：没有 downgrade 路径。
- 官方库的 identity 与我们不同：Room 拒绝打开。

还有一种是它拦不住的：两边 DDL 与 identity 都一样，但我们改了某个字段的含义。这种情况 Room 会照常打开，读出来的是错的。identity 是 schema 的 hash，不含字段语义，所以它不会因为语义变化而变，**任何基于 header 的判定都识别不出这一种**。它只能靠改字段时同步更新本文件「字段映射」一节里的表来保证，属于流程约束，不是机制保证。

前两种在分叉后才会出现。现在两边一致，三种都不触发。

## 分流

分流要放在 `WebDavSync.restoreFromBackupFile`，那是本地导入与 WebDAV 导入唯一的共用入口。

判定按 `user_version` 先行，identity 只用来判来源：

```
ourVersion：编译进应用的当前 Room 版本（现在 32）
officialVersions：已知官方版本的快照集合（现在只有 v32）

version > ourVersion                    → 失败（没有 downgrade，也不该猜）
version == ourVersion 且 identity == ourIdentity → stageRestore
version ∈ officialVersions             → 导入器（用该版本的快照读）
version < ourVersion                   → stageRestore（迁移链）
其他                                   → 失败
```

两点说明：

- `ourVersion` 用编译进应用的常量，不从活库读。升级后首次启动时活库可能还停在旧版本，读它会把官方 v32 误判成「版本更高」；全新安装时活库文件可能还不存在。
- 版本先行是因为 identity 不随版本变化：官方若发布只改数据、不改 DDL 的新版本，identity 仍是同一个值，只看 identity 会把它当作已知版本放进来。

「版本低、identity 未知」这一行是明确的宽容：它可能是自家旧库，也可能是结构兼容的官方旧库，两者都交给迁移链试。迁移链打不开时会明确失败，不会写坏数据。

## 转换流程

分叉之后，导入器不改正在用的库，而是造一份结构等同于当前版本的库，再走现有安装流程：

1. 解压到 `PendingRestore.createStagingDirectory()` 的 `preparing-*` 目录，保留 `rikka_hub-wal` 这类旧格式条目。
2. `OfficialDatabaseReader` 把官方库读成行数据。
3. 在 staging 的 `payload/database/` 下新建空的 Room 库，把行数据写进去。
   - 文件名必须是 `rikka_hub`：`PendingRestore.buildEntries` 按 `database/<name>` 识别，`targetFile` 也只接受这个名字，其他名字会在 apply 时直接报错。
   - 写完后按现有顺序收尾：`try { checkpoint } finally { close }`，再 `removeSidecars`。留着非空的 `-wal` 会让 `buildEntries` 对同一路径生成两条 entry。
4. settings 按原流程迁移、解码、重新编码，`launchCount` 一并带进 pending 的 `settings.json`。
5. `PendingRestore.publish(staging)`，重启时原子替换。

payload 的布局沿用现有约定：`payload/database/rikka_hub`、`payload/files/<附件路径>`，`settings.json` 放在 staging 根。payload 为空时 `apply` 会写一份空 journal 然后返回 true，也就是「静默成功、什么都没装」，所以导入器必须确认至少写入了数据库或附件。

## 读取层

这一节已经落地（C20）：`data/sync/official/OfficialDatabaseReader` 提供只读访问，打开时用 `PRAGMA table_info` 与 `user_version` 核对结构，结构与类型判断抽在 `OfficialSchemaVerifier` 里，不依赖 Android，可以直接跑 JVM 测试。

- `SQLiteDatabase.openDatabase(..., OPEN_READONLY)` 打开。不跑 integrity_check，那是 `DatabaseBackup.normalize` 的职责；导入器在解压后先调一次 `normalize` 把 `-wal` 合进去，再读。
- 不加载 Room、不引用本仓库的 `@Entity`，表名列名全部来自 `OfficialDatabaseSchema`。
- 只读业务表，`message_fts*` 跳过。
- 逐行迭代，不 materialize：每张表产出 `Sequence<OfficialRow>`。`OfficialRow` 携带表名与列值，列值按 affinity 在读取时转成确定的类型，行对象不依赖 cursor。读取时校验：快照里有、库里没有的列失败；类型与快照冲突失败；表缺失失败；库里多出来的列忽略并记日志。
- 附件目录与嵌套规则引用 `OfficialBackupLayout`，不要在这里重列一遍。
- 读取层里碰 SQLite 的部分只能在 androidTest 里验证，工程没有 JVM SQLite 或 Robolectric；结构与类型的判断已经抽成纯函数，那部分跑 JVM 测试。

## 写入与字段映射

写入目标是新建的空库，不是正在用的库：没有清空这一步，也没有与运行中会话抢写的问题。

- 一个事务包住全部写入，失败即整个临时库作废。
- 表顺序按外键：先父表后子表。
- `message_node` 的 `node_index`、`select_index` 与 id 原样保留。`ConversationEntity.nodes` 是遗留空列（v12 起恒为 `"[]"`，树在 `message_node` 里），照抄即可。
- 自增表（`GenMediaEntity`、`managed_files`、`usage_record`）显式写 id；SQLite 会自己把 `sqlite_sequence` 提到显式 id 之上。
- JSON 字段（`UIMessage`、`config`、`toolApprovals` 等）原样搬运，不解析再序列化。解析会丢未知字段，也会把官方的格式变化变成我们这边的转换错误。
- 官方有、我们没有的列：拒绝导入这一版，不按默认值硬填（没有默认值的 NOT NULL 列本来也插不进去）。我们有、官方没有的列按声明默认值填。
- 字段级语义：每个支持的官方版本带一份映射表，逐列写清「直接照抄」还是「需要转换」，并配一条完整性测试，保证表里覆盖了该版本快照的每一列。分叉之后只改这张表，不改读取层。现在两边没有分叉，整张表都是「直接照抄」。

## settings

官方 `settings.json` 的处理与 `stageRestore` 一致：`SettingsJsonMigrator.migrate` → `JsonInstant.decodeFromString<Settings>`，然后按现有方式重新编码写回 pending。

在解码之前加一层直接作用在原始 JSON 上的检查：把原文解析成 `JsonObject`，按官方 v32 的 key/type 形状校验（包含 `providers`、`assistants`、`displaySetting` 这些嵌套对象的形状），关键项缺失或类型不符时失败。

这一层不是 roadmap 要求 5 里的「独立解析器」：它只校验形状，转换仍然复用现有的 `Settings` 反序列化。它是**一部分**实现，不是全部。完整的独立解析器（冻结的 `OfficialSettingsV32` 加字段映射）与「字段映射」是同一件事，在 `Settings` 开始与官方分叉时一起做。`Settings` 顶层约 50 个字段，加上嵌套对象的形状，成本可控。

这层检查的钩子也要放在共用的恢复入口上，否则官方 v32 走 `stageRestore` 时它不会跑。

`launchCount` 不属于 `Settings`，由 `PendingRestore.apply` 在启动前与 settings 一起写入，不在运行时单独设。

## 附件替换

归档里的四个目录是 `upload/`、`skills/`、`fonts/`、`media_creation/`，其中 `skills/` 与 `media_creation/` 会带子目录。目录与嵌套规则以 `OfficialBackupLayout` 为准。

这一项已经落地。`PendingRestore.buildRemovalEntries` 对归档里实际存在的附件目录，列出 live 目录里有、payload 里没有的文件，给每个文件合成一条 `install = false, hadOriginal = true` 的条目：apply 把目标移进 `originals/`，rollback 再移回来，journal 与 rollback 都不需要改，每条也仍然可重入。

做成文件级条目而不是目录级 rename，是因为 journal 与 rollback 都按路径记录单个文件，加目录条目得为它们另加一套状态。

两个边界：

- 只在数据库也一起替换时才做。只导附件时现有库还引用着 live 上的文件，删掉它们等于制造悬空引用，所以那种组合保持叠加语义。
- 归档里「存在但为空」的目录无法表达：写归档的侧不写目录条目，读归档的侧也会跳过目录条目，所以空目录与「没有这个目录」在 payload 里长得一样。这一条记在已知限制里。

## 归档

这一项已经落地。导入入口（`WebDavSync.restoreFromBackupFile`）在 `stageRestore` 返回之后把原始 zip 复制一份到 `noBackupFilesDir/imported-backups/`，永不自动删除。它是官方自己产出的自包含文件，随时能被官方吃回去，也是导入写坏数据时唯一能回退的东西。

归档才发生在 `publish` 之后：那时库还没真正换上，安装要等下一次启动的 `apply`。所以后面那次安装失败时，归档里仍然留着这份原始备份，这正是它该在的位置。

只在 `stageRestore` 成功之后归档：失败的恢复没有改动任何数据，不需要为它留底，留着只会堆积。

`ImportedBackupArchive.store` 先写临时文件再原子改名，进程中途被杀不会留下截断的归档；文件名带毫秒并且不覆盖已有文件，同一秒内多次导入不会互相盖掉。它不依赖 Android，可直接用 JVM 测试覆盖。

放 `noBackupFilesDir` 而不是 `filesDir`：后者的整个目录被 `app` 模块的 web 路由 `GET /api/files/path/{...}` 暴露出去。

归档目前在应用内没有入口：`noBackupFilesDir` 不在 FileProvider 的共享路径里，web 路由也不服务它，所以取回需要 root 或 adb。加一个「已归档备份」的列表页是后续的事。

## 索引重建

`message_fts*` 不在 Room 的 schema 里，`AppDatabaseFactory.onOpen` 只保证表存在，内容要重建。

`PendingRestore.apply` 返回 `Boolean`，`RikkaHubApp.onCreate` 已经拿到它（`try` 块内）。要在 `startKoin` 之后触发重建，得把这个值提到 `try` 块外面，而不是在块内直接用。

只在当次启动触发不够：进程在重建完成前死掉，或重建中途失败，下次启动没有任何标记。所以除了当次触发，启动时再自检一次 `message_fts` 是否为空：为空且会话表非空时才重建。自检放在 Koin 之后的后台任务里，不阻塞启动。

重建失败时不清任何标记（本来就靠自检），下次启动会重试。

## 验收

四类样本，至少各一份：

1. 官方客户端导出的真实 v32 备份。注意这一类现在走 `stageRestore`，验证的是现有路径，不是导入器。
2. 构造样本：把官方 v32 备份的 identity 改掉，让它落进导入器分支。分叉之前这是唯一能验证导入器正向路径的办法。
3. 本 fork 旧构建导出的备份：验证迁移链那条路。
4. 版本高于我们的库，必须明确报错。

判据：

- 14 张表各自的条数，在「导入完成、尚未启动」这个时点比（`managed_files` 会被首次启动的 `syncManagedFiles` 改写）。
- 每张表按主键排序后的内容 SHA-256，并写明规范化规则（列顺序、NULL 与空串、BLOB 的十六进制、Double 的格式、复合主键的排序键）。期望值由独立的 sqlite3 脚本从原始备份算出，不能由读取层自己算。
- `message_node` 的 `node_index` 与 `select_index`。
- 附件的相对路径集合与每个文件的 SHA-256，外加「首次启动后 live 的四个目录里没有归档之外的多余文件」。
- `settings` 的关键字段。
- 搜索：固定一组查询与期望的会话 id 集合，导入前后结果集相同。我们的 FTS 用 `tokenize = 'simple'`，与官方的分词不一定一致，所以这条判据要求「同一组查询在官方客户端能搜到、导入后在本 fork 也能搜到相同的会话」；需要官方客户端，是人工闸门，不进 CI。

每次改动 schema 或 `Settings` 之后要重跑全部样本，这条与 roadmap 一致。

## 分步计划

导入器本体。它与第一次改 schema 同一批做，顺序是导入器先落地并验证，再动结构：

1. 读取层：先定 fixture 与期望值（行数、列集合、内容 hash，由独立脚本算出），再写 reader，用 androidTest 跑。
2. 版本判定与分流：接到 `restoreFromBackupFile`。
3. 写入：行数据 → 新建 Room 库 → 校验能打开且各表 hash 与期望值一致。
4. settings 形状校验。附件替换天然复用（共用 `PendingRestore.buildEntries`）；归档要跟着分流走：现在的归档调用在 `stageRestore` 之后，导入器分支不走那条路，得把调用上移成两条分支共用，或在导入器分支里再调一次。
5. 验收。

现在先做的两项不依赖上面任何一步，都已经落地：

1. **归档**：`ImportedBackupArchive.store` 加 `ImportedBackupArchiveTest`（JVM，四条：落盘内容、不覆盖旧档、不留临时文件、源缺失时返回带原因的失败）。
2. **附件替换**：`PendingRestore.buildRemovalEntries` 加 `PendingRestoreTest` 的新用例（JVM，覆盖「多余附件被删」「只导数据库时 live 附件不动」「只导附件时 live 附件不动」「嵌套的 skills 目录被递归替换」「rollback 恢复被删文件，且断言删除确实发生过」）。

## 已知限制

- 数据库被替换时，导入是替换语义，不与现有数据合并。
- 附件替换只在 payload 同时带数据库时发生。只导附件的恢复是叠加：现有库还在引用 live 上的文件，删掉它们等于制造悬空引用。
- 归档里「存在但为空」的附件目录无法表达：写归档的侧不写目录条目，读归档的侧也会跳过目录条目，所以空目录与「没有这个目录」在 payload 里长得一样，替换时会退化成合并。`skills/` 被清空后留下的空目录会被 `SkillManager` 当成待清理目录。
- `upload/` 与 `fonts/` 在导出侧只收顶层文件，替换侧却是递归删除。payload 同时带数据库时，live 里这两个目录下的子目录文件会被删掉，因为它们不可能出现在任何 payload 里。这符合替换语义，但值得知道。
- `media_creation/` 下的 `.part` 临时文件导出时被跳过，替换时会当成「备份里没有」而删除。它们是半成品，删掉通常无害，但下载中的任务会受影响。
- 当前设备上 androidTest 跑不起来：测试进程解析不到读取层的新类，已确认不是代码问题（类在 APK 里、设备 APK 与构建产物 md5 一致、应用本身能正常启动）。结构与类型判断抽成纯函数之后可以走 JVM 测试，碰 SQLite 的那部分等设备问题解决再验证。
- 真正的官方备份样本还没拿到，读取层的正向测试暂时用应用自己创建的库当替身。
- live 文件名里带反斜杠会让整次导入失败。校验放在 `buildRemovalEntries` 里、journal 落地之前，所以失败会走 `failed-*` 分支：用户看到「已保留原数据」，下次启动不会重播。漏掉这层校验的话，`apply` 会反复抛异常，而 `RikkaHubApp` 只捕获 `RestoreFailedException`，结果是每次启动都崩。这种文件名不需要 root 就能产生：`FileUtils` 从 provider 的 displayName 取扩展名，`foo.\x` 会拼出带反斜杠的名字。
- 归档目前在应用内取不回来，需要 root 或 adb。
- 字段语义变化无法从 header 识别，只能靠流程。
- 附件按 hash 去重（roadmap 2.3 的候选）落地后，导入时要把按路径的附件归并成 hash。读取层只需留出「附件路径 → 目标存储」的接口。
- 官方兼容导出（要求 8）已由 C18 提供骨架，分叉时的裁剪仍待实现，不在本文件范围。
