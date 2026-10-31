# 从 app/schemas 生成某个官方版本的数据库结构快照。
#
# 用法（在仓库根）：
#   pwsh -File tools/gen-official-snapshot.ps1 -Version 32 -ObjectName OfficialSnapshotV32
#
# 生成结果放进 app/src/main/java/me/rerere/rikkahub/data/sync/official/，还要在
# OfficialSnapshots 里注册一行，读取层才会认这个版本。
param(
    [Parameter(Mandatory = $true)][int]$Version,
    [Parameter(Mandatory = $true)][string]$ObjectName
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$schema = Join-Path $root "app/schemas/me.rerere.rikkahub.data.db.AppDatabase/$Version.json"
if (-not (Test-Path $schema)) { throw "找不到 schema 文件：$schema" }
$outDir = Join-Path $root 'app/src/main/java/me/rerere/rikkahub/data/sync/official'
$outFile = Join-Path $outDir "$ObjectName.kt"

$j = Get-Content $schema -Raw | ConvertFrom-Json
$identity = $j.database.identityHash

$sb = New-Object System.Text.StringBuilder
function W($s) { [void]$sb.AppendLine($s) }

W 'package me.rerere.rikkahub.data.sync.official'
W ''
W '/**'
W (" * 官方 v$Version 的数据库结构快照，从 app/schemas/.../$Version.json 生成。")
W ' *'
W ' * 由 tools/gen-official-snapshot.ps1 产出，不要手改：官方升版时重新生成，并在'
W ' * [OfficialSnapshots] 里注册一行，读取层才会认这个版本。'
W ' */'
W "internal object $ObjectName {"
W ("    const val VERSION = " + $Version)
W ''
W ("    const val IDENTITY_HASH = `"$identity`"")
W ''
W '    val TABLES: Map<String, OfficialSchemaTypes.Table> = listOf('
foreach ($e in $j.database.entities) {
    W '        OfficialSchemaTypes.Table('
    W ('            name = "' + $e.tableName + '",')
    $pk = ($e.primaryKey.columnNames | ForEach-Object { '"' + $_ + '"' }) -join ', '
    W ('            primaryKey = listOf(' + $pk + '),')
    $auto = if ($e.primaryKey.autoGenerate) { 'true' } else { 'false' }
    W ('            autoIncrement = ' + $auto + ',')
    W '            columns = listOf('
    foreach ($f in $e.fields) {
        $nn = if ($f.notNull) { 'true' } else { 'false' }
        $line = '                OfficialSchemaTypes.Column("' + $f.columnName + '", "' + $f.affinity + '", notNull = ' + $nn
        if ($null -ne $f.defaultValue) {
            $dv = ([string]$f.defaultValue).Replace('\', '\\').Replace('"', '\"')
            $line += ', defaultValue = "' + $dv + '"'
        }
        $line += '),'
        W $line
    }
    W '            ),'
    W '            indices = listOf('
    foreach ($i in $e.indices) {
        $cols = ($i.columnNames | ForEach-Object { '"' + $_ + '"' }) -join ', '
        $orders = ($i.orders | ForEach-Object { '"' + $_ + '"' }) -join ', '
        $uq = if ($i.unique) { 'true' } else { 'false' }
        W ('                OfficialSchemaTypes.Index("' + $i.name + '", unique = ' + $uq + ', columns = listOf(' + $cols + '), orders = listOf(' + $orders + ')),')
    }
    W '            ),'
    W '            foreignKeys = listOf('
    foreach ($k in $e.foreignKeys) {
        $cols = ($k.columns | ForEach-Object { '"' + $_ + '"' }) -join ', '
        $refs = ($k.referencedColumns | ForEach-Object { '"' + $_ + '"' }) -join ', '
        W ('                OfficialSchemaTypes.ForeignKey(table = "' + $k.table + '", columns = listOf(' + $cols + '), referencedColumns = listOf(' + $refs + '), onDelete = "' + $k.onDelete + '", onUpdate = "' + $k.onUpdate + '"),')
    }
    W '            ),'
    W '        ),'
}
W '    ).associateBy { it.name }'
W '}'

[System.IO.File]::WriteAllText($outFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output "wrote $outFile"
Write-Output ("version={0} identity={1} tables={2} columns={3}" -f $Version, $identity, $j.database.entities.Count, (($j.database.entities | ForEach-Object { $_.fields.Count }) | Measure-Object -Sum).Sum)
