package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分流判定的纯逻辑验证。
 *
 * 这一段决定「备份里的库交给谁」：迁移链、转换器、还是直接失败。判错的方向只有两种，都不
 * 能接受：把该转换的库交给迁移链会让恢复失败，把不认识的库硬读会写坏数据。
 */
class OfficialRestorePlannerTest {

    private val ourVersion = 32
    private val ourIdentity = OfficialDatabaseSchema.IDENTITY_HASH
    private val snapshots = OfficialSnapshots.BY_VERSION

    private fun plan(version: Int, identity: String?) = OfficialRestorePlanner.plan(
        version = version,
        identityHash = identity,
        ourVersion = ourVersion,
        ourIdentity = ourIdentity,
        snapshots = snapshots,
    )

    @Test fun `our own current backup goes through the migration chain`() {
        assertEquals(OfficialRestorePlan.Migrate, plan(ourVersion, ourIdentity))
    }

    @Test fun `an older backup goes through the migration chain`() {
        assertEquals(OfficialRestorePlan.Migrate, plan(31, "a428043e3256791d9bc0855715c6355a"))
        assertEquals(OfficialRestorePlan.Migrate, plan(16, null))
    }

    @Test fun `same version but a different schema is converted`() {
        // 官方 v32 的指纹与我们相同，所以这里用别的值模拟「同版本不同结构」
        val result = plan(ourVersion, "00000000000000000000000000000000")
        assertTrue("应当走转换，实际是 $result", result is OfficialRestorePlan.Convert)
        assertEquals(ourVersion, (result as OfficialRestorePlan.Convert).snapshot.version)
    }

    @Test fun `same version without a schema fingerprint is rejected`() {
        // 连 room_master_table 都没有，判不出来源，不能猜
        val result = plan(ourVersion, null)
        assertTrue("判不出来要明确失败，实际是 $result", result is OfficialRestorePlan.Reject)
    }

    @Test fun `a newer backup is rejected instead of falling back`() {
        // 迁移链降不了版本；官方升版是加列，硬写会在插入时崩，所以要在这里就拒绝
        val result = plan(ourVersion + 1, "00000000000000000000000000000000")
        assertTrue("高版本必须明确失败，实际是 $result", result is OfficialRestorePlan.Reject)
        assertTrue((result as OfficialRestorePlan.Reject).reason.contains("高于"))
    }

    @Test fun `the rejection explains what to do`() {
        val result = plan(ourVersion + 1, null) as OfficialRestorePlan.Reject
        assertTrue("提示要告诉用户升级应用", result.reason.contains("升级应用"))
    }
}
