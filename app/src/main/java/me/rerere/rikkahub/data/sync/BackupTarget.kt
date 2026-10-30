package me.rerere.rikkahub.data.sync

/**
 * 备份要产出给谁读。
 *
 * 两个出口的归档布局一致，差别只在数据库：自家备份带全量数据，官方兼容备份要交给官方
 * 客户端。现在双方 schema 相同，产物几乎一样；一旦我们改动结构，官方出口会在导出时直接
 * 失败，直到裁剪逻辑落地。
 */
enum class BackupTarget {
    /** 本 fork 自己 */
    SELF,

    /** 官方客户端 */
    OFFICIAL,
}
