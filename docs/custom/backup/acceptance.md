# 验收

备份导入与恢复的验收方式。判据要有独立性：期望值由仓库外的脚本从原始备份算出，不能用被测的读取层自己算。

## 样本

五类，至少各一份：

1. 官方客户端导出的真实备份。这一类走 `stageRestore`，验证的是现有路径，不是导入器。内容要覆盖多分支会话与重新生成、带附件与工具调用的消息、多 provider 与自定义 assistant、自定义主题与提示词。
2. 构造样本：把官方备份的 identity 改掉，让它落进导入器分支。分叉之前这是唯一能验证导入器正向路径的办法。
3. 本 fork 旧构建导出的备份：验证迁移链那条路，也就是「旧应用导出、新应用导入」里的另一种来源。
4. 版本高于我们的库，必须明确报错。
5. 官方兼容出口（要求 8）产出的备份，交给官方客户端恢复。判据在官方那侧：恢复成功，且会话条数与关键字段对得上。裁剪逻辑落地之前这一类不适用。

样本含私人数据，放仓库外、放设备上；测试用 `Assume` 跳过，缺哪份就跳过哪份，CI 不会因此失败。本文件只记判据与版本类别，不记样本的体积、条数与内容。

## 判据

- 每张表按主键排序后的行数与内容 SHA-256，规范化规则见下。
- 附件的相对路径集合与每个文件的 SHA-256，外加「首次启动后 live 的四个目录里没有归档之外的多余文件」。
- settings 的关键字段：助手与服务商的条数与 id 集合、当前助手、当前模型。期望值必须从同一份归档里取，跨归档借 `settings.json` 会把期望值算成另一份样本的数，表现为导入像丢了数据。
- 搜索：固定一组查询与期望的会话 id 集合，导入前后结果集相同。**对比时要把搜索范围调成同一个**：搜索页有「当前助手 / 全部助手」两种范围，范围不同结果集不可比。基准取恢复之前（或官方客户端手动重建索引之后）的状态：官方客户端恢复后不重建全文索引，同一份备份在官方那边恢复出来会搜不到本该有的结果，那是索引没建，不是数据丢了。我们的 FTS 用 `tokenize = 'simple'`，与官方的分词不一定一致，所以判据要求「同一组查询在官方客户端能搜到、导入后在本 fork 也能搜到相同的会话」。这条需要官方客户端，是人工闸门，不进 CI。

## 规范化

每个单元格编码成「类型字节 + 4 字节小端长度 + 载荷」：`N` NULL 空载荷、`I` 十进制 ASCII、`R` IEEE754 双精度 8 字节大端、`T` UTF-8、`B` 原始字节。行之间写一个 `0x1e`，单元格顺序取快照里的列顺序，行顺序按主键升序。

规则由 `tools/backup-expectations.py` 与 `OfficialSampleAcceptanceTest` 各实现一遍，两边逐字节一致。期望值对附件绝对路径做前缀归一（换成 `file://<files>/`），否则「路径被正确重写」会被当成「内容不一致」。

## 跑法

```bash
# 1. 从原始备份里取出库，算出期望值
python tools/backup-expectations.py \
    --schema app/schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json \
    --db <备份解压出来的 rikka_hub.db> \
    --out <样本目录>/backup-expectations-v32.json

# 2. 推到设备，先删同名文件
adb shell rm -f /data/local/tmp/rikkahub-sample-v32.db /data/local/tmp/backup-expectations-v32.json
adb push <备份解压出来的 rikka_hub.db> /data/local/tmp/rikkahub-sample-v32.db
adb push <样本目录>/backup-expectations-v32.json /data/local/tmp/backup-expectations-v32.json

# 3. 跑验收
adb shell am instrument -w \
    -e class me.rerere.rikkahub.data.sync.official.OfficialSampleAcceptanceTest \
    <包名>.test/androidx.test.runner.AndroidJUnitRunner
```

## 覆盖范围

按样本类别：

| 类别 | 样本或测试 | 状态 |
| --- | --- | --- |
| 官方客户端导出 | v32（官方 3.1.1） | 全量验过 |
| 构造样本 | `BackupManagerTest` 里改 identity 的用例 | 覆盖分流与转换的接线，无独立样本 |
| 本 fork 旧构建 | v31（产出自 3.1.0 (193)，包名还是 `me.rerere.rikkahub`） | 表数据、内容摘要与 settings 验过；附件未逐个比 |
| 高版本库 | `BackupManagerTest` 的拒绝用例 | 验过 |
| 官方兼容出口 | 无 | 未做，裁剪逻辑还没实现 |

表数据这一层：v31 与 v32 各 14 张表全部通过。两层都验，读取层直接读原始备份，转换层把样本经转换器写成当前版本库再读。转换层只比对源版本也有的列，因为转换后是当前版本结构；另有一条断言确认转换结果里没有残留指向别的安装的附件路径。

整份归档这一层：附件逐个比对路径、大小与内容摘要且不多不少（`OfficialBackupRestoreAcceptanceTest`），恢复后 live 目录正好是归档那一份（`RestoreAppliedOutcomeTest`），settings 的助手与服务商条数、id 集合、当前助手与当前模型。

v31 的附件没有逐个在设备上比：整包推上设备再解压不划算，它覆盖的代码路径与 v32 相同。v16（官方 2.1.6）明确不支持，不纳入样本集。

每次改动 schema 或 `Settings` 结构之后重跑全部样本。只编译通过不算数。
