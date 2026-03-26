package cn.aeolusdev.pkgpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DomainTest {
    @Test
    fun `recognize carriers by tracking rules`() {
        assertEquals("顺丰速运", CarrierRecognizer.recognize("SF1234567890123"))
        assertEquals("京东快递", CarrierRecognizer.recognize("JD1234567890123"))
        assertEquals("邮政EMS", CarrierRecognizer.recognize("EA123456789CN"))
        assertEquals("未知承运商", CarrierRecognizer.recognize("ABC"))
    }

    @Test
    fun `station open status handles overnight time`() {
        assertTrue(StationSorter.isOpen("22:00-06:00", LocalTime.of(23, 0)))
        assertTrue(StationSorter.isOpen("22:00-06:00", LocalTime.of(2, 0)))
        assertFalse(StationSorter.isOpen("22:00-06:00", LocalTime.of(12, 0)))
    }

    @Test
    fun `elapsed text uses hours and days`() {
        val now = 1000L * 60L * 60L * 30L
        assertEquals("已入站：6小时", TimeUtils.elapsedText(now - 6L * 60L * 60L * 1000L, now))
        assertEquals("已入站：2天", TimeUtils.elapsedText(now - 48L * 60L * 60L * 1000L, now))
    }

    @Test
    fun `sync status text maps to chinese labels`() {
        assertEquals("同步", SyncStatusText.homeButton(SyncStatus.IDLE))
        assertEquals("同步中", SyncStatusText.homeButton(SyncStatus.SYNCING))
        assertEquals("同步失败", SyncStatusText.homeButton(SyncStatus.FAILED))
        assertEquals("未连接网络", SyncStatusText.homeButton(SyncStatus.OFFLINE))
    }
}
