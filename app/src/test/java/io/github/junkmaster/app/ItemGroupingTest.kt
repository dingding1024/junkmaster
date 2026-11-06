package io.github.junkmaster.app

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.ConsequenceCatalog
import io.github.junkmaster.app.scan.ItemGrouping
import io.github.junkmaster.app.scan.ResultTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扫描结果三分区 + 分组折叠（第六轮反馈第 3、4 条）。
 *
 * 关键不变量：
 *  1. 三个 Tab 互斥且并集覆盖全部项（不能有项消失，也不能重复出现）；
 *  2. 同 ruleId 归一组，组内按体积降序，组间按总体积降序（最占地方的排最前）；
 *  3. L3 / 仅报告组不可勾选；
 *  4. 组头展示的后果 id 取组内统一值，保证「同类型只说明一次」。
 */
class ItemGroupingTest {

    private fun item(
        path: String,
        size: Long = 0L,
        risk: RiskLevel = RiskLevel.L1_SAFE,
        mode: DeleteMode = DeleteMode.DIRECT_DELETE,
        ruleId: String = "l1.temp_file",
        consequenceId: String = ConsequenceCatalog.TEMP_FILE,
        category: ScanCategory = ScanCategory.TEMP_FILE,
        checked: Boolean = true,
        requiresShizuku: Boolean = false
    ) = ScanItem(
        id = path,
        path = path,
        displayName = path.substringAfterLast('/').ifBlank { path },
        sizeBytes = size,
        category = category,
        riskLevel = risk,
        deleteMode = mode,
        consequenceId = consequenceId,
        ruleId = ruleId,
        checked = checked,
        isSafe = risk == RiskLevel.L1_SAFE,
        requiresShizuku = requiresShizuku
    )

    @Test
    fun tabsAreMutuallyExclusiveAndCoverAll() {
        val items = listOf(
            item("/sdcard/Download/a.tmp", 10),
            item(
                "/sdcard/Android/data/com.x/cache/b.bin", 100,
                ruleId = "l1.app_cache",
                consequenceId = ConsequenceCatalog.APP_CACHE,
                category = ScanCategory.APP_CACHE,
                requiresShizuku = true
            ),
            item(
                "/sdcard/Download/old.log", 20,
                risk = RiskLevel.L2_CONFIRM,
                mode = DeleteMode.CONFIRM_DELETE,
                ruleId = "l2.system_log",
                consequenceId = ConsequenceCatalog.SYSTEM_LOG,
                category = ScanCategory.SYSTEM_LOG,
                checked = false
            ),
            item(
                "/sdcard/Download/big.zip", 900,
                risk = RiskLevel.L2_CONFIRM,
                mode = DeleteMode.REPORT_ONLY,
                ruleId = "report.download_large",
                consequenceId = ConsequenceCatalog.DOWNLOAD_LARGE,
                category = ScanCategory.DOWNLOAD_LARGE,
                checked = false
            ),
            item(
                "/data/data/com.x/databases/msg.db", 50,
                risk = RiskLevel.L3_FORBIDDEN,
                mode = DeleteMode.FORBIDDEN,
                ruleId = "l3.protected",
                consequenceId = ConsequenceCatalog.L3_FORBIDDEN,
                category = ScanCategory.L3_PROTECTED,
                checked = false
            )
        )

        val local = ItemGrouping.localItems(items)
        val deep = ItemGrouping.deepItems(items)
        val report = ItemGrouping.reportItems(items)

        assertEquals("免权限只含不需要 Shizuku 的 L1", 1, local.size)
        assertEquals("深度含 Shizuku 缓存与 L2 可删项", 2, deep.size)
        assertEquals("仅报告含 REPORT_ONLY 与 L3", 2, report.size)

        val union = (local + deep + report).map { it.id }.toSet()
        assertEquals("三项分区并集必须覆盖全部", items.size, union.size)
        assertEquals("三项分区数量之和必须等于总数", items.size, local.size + deep.size + report.size)
        assertTrue(ItemGrouping.reportItems(items).none { it.deleteMode.canDelete && it.riskLevel != RiskLevel.L3_FORBIDDEN })
    }

    @Test
    fun tabOfRoutesEachKind() {
        assertEquals(ResultTab.LOCAL, ItemGrouping.tabOf(item("/sdcard/a.tmp")))
        assertEquals(
            ResultTab.DEEP,
            ItemGrouping.tabOf(item("/sdcard/Android/data/com.x/cache/x", requiresShizuku = true))
        )
        assertEquals(
            ResultTab.DEEP,
            ItemGrouping.tabOf(
                item("/sdcard/a.log", risk = RiskLevel.L2_CONFIRM, mode = DeleteMode.CONFIRM_DELETE)
            )
        )
        assertEquals(
            ResultTab.REPORT,
            ItemGrouping.tabOf(
                item("/sdcard/a.apk", mode = DeleteMode.REPORT_ONLY, risk = RiskLevel.L2_CONFIRM)
            )
        )
        assertEquals(
            ResultTab.REPORT,
            ItemGrouping.tabOf(
                item("/data/data/x", risk = RiskLevel.L3_FORBIDDEN, mode = DeleteMode.FORBIDDEN)
            )
        )
    }

    @Test
    fun groupsByRuleAndSortsBySize() {
        val items = listOf(
            item("/sdcard/Download/a.tmp", 10),
            item("/sdcard/Download/b.tmp", 300),
            item("/sdcard/Download/c.tmp", 50),
            item(
                "/sdcard/Download/empty1", 0,
                ruleId = "l1.empty_dir",
                consequenceId = ConsequenceCatalog.EMPTY_DIR,
                category = ScanCategory.EMPTY_DIR
            )
        )
        val groups = ItemGrouping.group(items)
        assertEquals(2, groups.size)
        // 临时文件组总体积 360 > 空目录组 0
        assertEquals("l1.temp_file", groups.first().ruleId)
        assertEquals(3, groups.first().count)
        assertEquals(360L, groups.first().totalBytes)
        assertEquals(
            "组内必须按体积降序",
            listOf(300L, 50L, 10L),
            groups.first().items.map { it.sizeBytes }
        )
    }

    @Test
    fun sameRuleKeepsSingleConsequenceId() {
        val items = (0 until 5).map {
            item(
                "/sdcard/Download/f$it.tmp", it.toLong(),
                ruleId = "l1.temp_file",
                consequenceId = ConsequenceCatalog.TEMP_FILE
            )
        }
        val groups = ItemGrouping.group(items)
        assertEquals(1, groups.size)
        assertEquals(ConsequenceCatalog.TEMP_FILE, groups.single().consequenceId)
        assertTrue("同类 5 项只渲染一份后果说明", groups.single().count == 5)
    }

    @Test
    fun checkableAndCheckedStateAreAggregated() {
        val items = listOf(
            item("/sdcard/a.tmp", checked = true),
            item("/sdcard/b.tmp", checked = false)
        )
        val g = ItemGrouping.group(items).single()
        assertTrue(g.checkable)
        assertEquals(1, g.checkedCount)
        assertFalse("部分勾选时组头不能显示全选", g.allChecked)
        assertTrue(g.partiallyChecked)

        val all = ItemGrouping.group(items.map { it.copy(checked = true) }).single()
        assertTrue(all.allChecked)
        assertFalse(all.partiallyChecked)
    }

    @Test
    fun forbiddenGroupIsNotCheckable() {
        val items = listOf(
            item(
                "/data/data/com.x/databases/a.db", 1,
                risk = RiskLevel.L3_FORBIDDEN,
                mode = DeleteMode.FORBIDDEN,
                ruleId = "l3.protected",
                consequenceId = ConsequenceCatalog.L3_FORBIDDEN,
                category = ScanCategory.L3_PROTECTED,
                checked = false
            )
        )
        val g = ItemGrouping.group(items).single()
        assertFalse("L3 组永不可勾选", g.checkable)
        assertFalse(g.allChecked)
        assertEquals(0, g.checkedCount)
    }

    @Test
    fun requiresShizukuOnlyWhenWholeGroupNeedsIt() {
        val mixed = listOf(
            item("/sdcard/a.tmp", requiresShizuku = false),
            item("/sdcard/b.tmp", requiresShizuku = true)
        )
        assertFalse("同组混有免权限项时不标整组需 Shizuku", ItemGrouping.group(mixed).single().requiresShizuku)

        val allDeep = listOf(
            item("/sdcard/Android/data/com.x/cache/a", requiresShizuku = true),
            item("/sdcard/Android/data/com.y/cache/b", requiresShizuku = true)
        )
        assertTrue(ItemGrouping.group(allDeep).single().requiresShizuku)
    }

    @Test
    fun emptyInputProducesNoGroups() {
        assertTrue(ItemGrouping.group(emptyList()).isEmpty())
        assertTrue(ItemGrouping.localItems(emptyList()).isEmpty())
    }
}
