package me.rerere.rikkahub.data.sync

/**
 * 备份要产出给谁读。
 *
 * 两个出口的归档布局一致，差别只在数据库：自家备份带全量数据，官方兼容备份要按
 * 官方 schema 裁剪。现在双方 schema 相同，产物几乎一样；一旦我们改动结构，两者的
 * 差异才会显现。
 */
enum class BackupTarget {
    /** 本 fork 自己 */
    SELF,

    /** 官方客户端 */
    OFFICIAL,
}
