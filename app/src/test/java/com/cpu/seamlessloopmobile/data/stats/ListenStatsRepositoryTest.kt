package com.cpu.seamlessloopmobile.data.stats

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ListenStatsRepositoryTest {

    @Test
    fun recordListenDeltaNowAccumulatesTotalAndTodaysBucket() = runTest {
        val today = LocalDate.of(2026, 7, 10)
        val repository = repositoryAt(today)
        val stat = stat()

        repository.recordListenDeltaNow(stat, 1_000L)
        repository.recordListenDeltaNow(stat, 2_000L)

        val saved = repository.getByIdentityKey(stat.identityKey)!!
        assertEquals(3_000L, saved.totalListenMs)
        assertEquals(3_000L, saved.dailyListenMs[today.toString()])
    }

    @Test
    fun listenMsForUsesMondayBasedWeekAndCalendarBoundaries() {
        val stat = stat(
            totalListenMs = 99_000L,
            dailyListenMs = mapOf(
                "2026-07-05" to 100L,
                "2026-07-06" to 200L,
                "2026-07-12" to 300L,
                "2026-07-13" to 400L,
                "2026-06-30" to 500L,
                "not-a-date" to 600L
            )
        )
        val today = LocalDate.of(2026, 7, 10)

        assertEquals(0L, stat.listenMsFor(ListenStatsPeriod.DAY, today))
        assertEquals(500L, stat.listenMsFor(ListenStatsPeriod.WEEK, today))
        assertEquals(1_000L, stat.listenMsFor(ListenStatsPeriod.MONTH, today))
        assertEquals(1_500L, stat.listenMsFor(ListenStatsPeriod.YEAR, today))
    }

    @Test
    fun listenMsForAllRetainsLegacyTotalWithoutDailyHistory() {
        val stat = stat(totalListenMs = 12_345L)

        assertEquals(12_345L, stat.listenMsFor(ListenStatsPeriod.ALL, LocalDate.of(2026, 7, 10)))
        assertEquals(0L, stat.listenMsFor(ListenStatsPeriod.MONTH, LocalDate.of(2026, 7, 10)))
    }

    @Test
    fun loadsLegacyJsonWithoutDailyHistory() = runTest {
        val file = File.createTempFile("legacy_listen_stats_", ".json").apply {
            deleteOnExit()
            writeText("""[{"songId":1,"displayName":"Legacy","totalListenMs":7000,"identityKey":"legacy|1"}]""")
        }

        val repository = ListenStatsRepository(file)
        val loaded = repository.getByIdentityKey("legacy|1")!!

        assertEquals(7_000L, loaded.totalListenMs)
        assertEquals(emptyMap<String, Long>(), loaded.dailyListenMs)
    }

    @Test
    fun loadsJsonWithExplicitNullDailyHistory() = runTest {
        val file = File.createTempFile("null_daily_listen_stats_", ".json").apply {
            deleteOnExit()
            writeText("""[{"songId":1,"displayName":"Legacy","totalListenMs":7000,"identityKey":"legacy|1","dailyListenMs":null}]""")
        }

        val repository = ListenStatsRepository(file)
        val loaded = repository.getByIdentityKey("legacy|1")!!

        assertEquals(emptyMap<String, Long>(), loaded.dailyListenMs)
    }

    @Test
    fun recordListenDeltaNowSplitsAcrossLocalMidnight() = runTest {
        val zone = ZoneId.of("America/New_York")
        val end = ZonedDateTime.of(2026, 7, 11, 0, 0, 30, 0, zone)
        val file = File.createTempFile("listen_stats_", ".json").apply { deleteOnExit() }
        val repository = ListenStatsRepository(
            jsonFile = file,
            wallClockMillis = { end.toInstant().toEpochMilli() },
            zoneId = zone
        )

        repository.recordListenDeltaNow(stat(), 60_000L)

        val saved = repository.getByIdentityKey("test.mp3|1000")!!
        assertEquals(60_000L, saved.totalListenMs)
        assertEquals(30_000L, saved.dailyListenMs["2026-07-10"])
        assertEquals(30_000L, saved.dailyListenMs["2026-07-11"])
    }

    @Test
    fun recordListenDeltaNowSplitsAcrossSpringForwardDay() = runTest {
        val zone = ZoneId.of("America/New_York")
        val end = ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, zone)
        val file = File.createTempFile("listen_stats_", ".json").apply { deleteOnExit() }
        val repository = ListenStatsRepository(
            jsonFile = file,
            wallClockMillis = { end.toInstant().toEpochMilli() },
            zoneId = zone
        )

        repository.recordListenDeltaNow(stat(), 24 * 60 * 60 * 1_000L)

        val saved = repository.getByIdentityKey("test.mp3|1000")!!
        assertEquals(24 * 60 * 60 * 1_000L, saved.totalListenMs)
        assertEquals(60 * 60 * 1_000L, saved.dailyListenMs["2026-03-07"])
        assertEquals(23 * 60 * 60 * 1_000L, saved.dailyListenMs["2026-03-08"])
    }

    @Test
    fun recordListenDeltaNowUsesZoneProviderForEachRecord() = runTest {
        val instant = ZonedDateTime.of(2026, 7, 10, 2, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        var currentZone = ZoneId.of("UTC")
        val file = File.createTempFile("listen_stats_", ".json").apply { deleteOnExit() }
        val repository = ListenStatsRepository(
            jsonFile = file,
            wallClockMillis = { instant.toEpochMilli() },
            zoneIdProvider = { currentZone }
        )

        repository.recordListenDeltaNow(stat(), 1L)
        currentZone = ZoneId.of("America/Los_Angeles")
        repository.recordListenDeltaNow(stat(), 1L)

        val saved = repository.getByIdentityKey("test.mp3|1000")!!
        assertEquals(1L, saved.dailyListenMs["2026-07-10"])
        assertEquals(1L, saved.dailyListenMs["2026-07-09"])
    }

    @Test
    fun clearAllPersistsEmptyStateBeforeEmittingClearEvent() = runTest {
        val file = File.createTempFile("listen_stats_", ".json").apply { deleteOnExit() }
        val repository = ListenStatsRepository(file)
        repository.recordListenDeltaNow(stat(), 1_000L)
        val clearEvent = async { repository.clearEvents.first() }
        runCurrent()

        repository.clearAll()

        clearEvent.await()
        assertEquals(emptyList<TrackStat>(), repository.allStats.value)
        assertEquals("[]", file.readText())
    }

    private fun repositoryAt(date: LocalDate): ListenStatsRepository {
        val file = File.createTempFile("listen_stats_", ".json").apply { deleteOnExit() }
        val zone = ZoneId.of("UTC")
        return ListenStatsRepository(
            jsonFile = file,
            wallClockMillis = { date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() },
            zoneId = zone
        )
    }

    private fun stat(
        totalListenMs: Long = 0L,
        dailyListenMs: Map<String, Long> = emptyMap()
    ) = TrackStat(
        displayName = "Test song",
        fileName = "test.mp3",
        durationMs = 1_000L,
        totalListenMs = totalListenMs,
        dailyListenMs = dailyListenMs,
        identityKey = "test.mp3|1000"
    )
}
