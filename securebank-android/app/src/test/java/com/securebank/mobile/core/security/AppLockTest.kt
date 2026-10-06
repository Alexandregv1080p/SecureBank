package com.securebank.mobile.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var clock = 0L
    private var enabled = true
    private val lock = AppLock(timeoutMs = 30_000, now = { clock }, enabled = { enabled })

    @Test
    fun aQuickTripToAnotherAppDoesNotLock() {
        lock.onBackgrounded()
        clock += 10_000
        lock.onForegrounded()
        assertFalse(lock.locked.value)
    }

    @Test
    fun longAbsenceLocksAndUnlockReleases() {
        lock.onBackgrounded()
        clock += 30_000
        lock.onForegrounded()
        assertTrue(lock.locked.value)
        lock.unlock()
        assertFalse(lock.locked.value)
    }

    @Test
    fun disabledLockNeverLocks() {
        enabled = false
        lock.onBackgrounded()
        clock += 600_000
        lock.onForegrounded()
        lock.lockNow()
        assertFalse(lock.locked.value)
    }

    @Test
    fun aSessionRestoredFromDiskLocksImmediately() {
        lock.lockNow()
        assertTrue(lock.locked.value)
    }

    @Test
    fun foregroundingWithoutAPriorBackgroundIsANoOp() {
        clock += 600_000
        lock.onForegrounded() // primeira abertura da tela
        assertFalse(lock.locked.value)
    }

    @Test
    fun signingOutClearsTheLock() {
        lock.lockNow()
        lock.reset()
        assertFalse(lock.locked.value)
    }

    @Test
    fun isEnabledReflectsThePreferenceAtCallTime() {
        assertTrue(lock.isEnabled())
        enabled = false
        assertFalse(lock.isEnabled())
    }
}
