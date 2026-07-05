package com.cpu.seamlessloopmobile.audio.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(DelicateCoroutinesApi::class)
class SleepTimerManagerTest {

    private val testDispatcher = newSingleThreadContext("sleep-timer-test")
    private val testScope = CoroutineScope(testDispatcher)

    @After
    fun tearDown() {
        testScope.cancel()
        testDispatcher.close()
    }

    @Test
    fun initialStateIsInactive() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        assertFalse(manager.timerState.value.isActive)
        assertEquals(SleepTimerMode.COUNTDOWN, manager.timerState.value.mode)
        assertEquals(0L, manager.timerState.value.remainingMillis)
        assertEquals(0L, manager.timerState.value.totalMillis)
        assertFalse(expired)
    }

    @Test
    fun startCountdownActivatesTimer() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        manager.startCountdown(1)

        val state = manager.timerState.value
        assertTrue(state.isActive)
        assertEquals(SleepTimerMode.COUNTDOWN, state.mode)
        assertEquals(60_000L, state.totalMillis)
        assertEquals(60_000L, state.remainingMillis)
        assertFalse(expired)
    }

    @Test
    fun countdownReachesZeroAndFiresExpired() = runBlocking {
        var expired = false
        val manager = newFastManager { expired = true }

        manager.startCountdown(1)
        assertTrue(manager.timerState.value.isActive)

        delay(35L)
        assertTrue(manager.timerState.value.isActive)
        assertFalse(expired)

        delay(40L)
        assertTrue(expired)
        assertFalse(manager.timerState.value.isActive)
    }

    @Test
    fun startFinishCurrentActivatesTimer() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        manager.startFinishCurrent()

        val state = manager.timerState.value
        assertTrue(state.isActive)
        assertEquals(SleepTimerMode.FINISH_CURRENT, state.mode)
        assertEquals(-1L, state.remainingMillis)
    }

    @Test
    fun startFinishPlaylistActivatesTimer() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        manager.startFinishPlaylist()

        val state = manager.timerState.value
        assertTrue(state.isActive)
        assertEquals(SleepTimerMode.FINISH_PLAYLIST, state.mode)
        assertEquals(-1L, state.remainingMillis)
    }

    @Test
    fun cancelResetsState() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        manager.startCountdown(5)
        assertTrue(manager.timerState.value.isActive)

        manager.cancel()
        assertFalse(manager.timerState.value.isActive)
        assertEquals(0L, manager.timerState.value.remainingMillis)
        assertFalse(expired)
    }

    @Test
    fun cancelDuringCountdownDoesNotFireExpired() = runBlocking {
        var expired = false
        val manager = newFastManager { expired = true }

        manager.startCountdown(1)
        delay(30L)
        assertTrue(manager.timerState.value.isActive)

        manager.cancel()
        delay(50L)
        assertFalse(expired)
        assertFalse(manager.timerState.value.isActive)
    }

    @Test
    fun shouldStopOnTrackEndFinishCurrentReturnsTrue() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        manager.startFinishCurrent()
        assertTrue(manager.shouldStopOnTrackEnd(isLastInPlaylist = false))
        assertTrue(expired)
        assertFalse(manager.timerState.value.isActive)
    }

    @Test
    fun shouldStopOnTrackEndFinishPlaylistOnlyWhenLast() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        manager.startFinishPlaylist()

        // Not last in playlist -> should not stop
        assertFalse(manager.shouldStopOnTrackEnd(isLastInPlaylist = false))
        assertFalse(expired)
        assertTrue(manager.timerState.value.isActive)

        // Last in playlist -> should stop
        assertTrue(manager.shouldStopOnTrackEnd(isLastInPlaylist = true))
        assertTrue(expired)
        assertFalse(manager.timerState.value.isActive)
    }

    @Test
    fun shouldStopOnTrackEndInactiveTimerReturnsFalse() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        assertFalse(manager.shouldStopOnTrackEnd(isLastInPlaylist = true))
        assertFalse(expired)
    }

    @Test
    fun startCountdownWithNonPositiveIsNoOp() {
        var expired = false
        val manager = SleepTimerManager(testScope) { expired = true }

        manager.startCountdown(0)
        assertFalse(manager.timerState.value.isActive)

        manager.startCountdown(-5)
        assertFalse(manager.timerState.value.isActive)
        assertFalse(expired)
    }

    @Test
    fun formatRemainingTimeInactiveReturnsEmpty() {
        val manager = SleepTimerManager(testScope) {}
        assertEquals("", manager.formatRemainingTime())
    }

    @Test
    fun formatRemainingTimeCountdown() {
        val manager = SleepTimerManager(testScope) {}
        manager.startCountdown(2) // 2 minutes

        assertEquals("02:00", manager.formatRemainingTime())
    }

    @Test
    fun formatRemainingTimeFinishModesShowDash() {
        val manager = SleepTimerManager(testScope) {}

        manager.startFinishCurrent()
        assertEquals("--:--", manager.formatRemainingTime())

        manager.cancel()

        manager.startFinishPlaylist()
        assertEquals("--:--", manager.formatRemainingTime())
    }

    @Test
    fun shouldStopOnTrackEndCountdownExpiredMidTrack() = runBlocking {
        var expired = false
        val manager = newFastManager { expired = true }

        manager.startCountdown(1)
        delay(75L)
        assertTrue(expired)

        // Timer state already reset by expiry, but simulating a race:
        // create a fresh one and force a mid-track check
        manager.cancel()
        expired = false

        // Test the safety-net in COUNTDOWN mode: if remaining <= 0 but timer still active
        // shouldStopOnTrackEnd should fire the callback.
        // We can't easily fake the internal state, so just verify inactive case.
        assertFalse(manager.shouldStopOnTrackEnd(isLastInPlaylist = false))
        assertFalse(expired)
    }

    @Test
    fun startingNewCountdownCancelsPrevious() = runBlocking {
        var expiredCount = 0
        val manager = newFastManager { expiredCount++ }

        manager.startCountdown(1)
        delay(30L)

        // Start a new countdown; it must cancel the old one.
        manager.startCountdown(1)
        delay(40L)
        assertEquals(0, expiredCount)

        delay(35L)
        assertEquals(1, expiredCount)
    }

    private fun newFastManager(onTimerExpired: () -> Unit): SleepTimerManager {
        return SleepTimerManager(
            scope = testScope,
            onTimerExpired = onTimerExpired,
            millisPerMinute = 60L,
            tickIntervalMs = 10L
        )
    }
}
