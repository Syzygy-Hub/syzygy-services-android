package com.syzygy.services.crashreporting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReporterTest {
    @Test
    fun `recordError does not throw`() {
        val reporter = ConsoleCrashReporter()
        reporter.recordError(RuntimeException("boom"), mapOf("module" to "auth"))
    }

    @Test
    fun `reportCrash does not throw or terminate process`() {
        val reporter = ConsoleCrashReporter()
        reporter.reportCrash("fatal crash simulated", mapOf("screen" to "Home"))
    }

    @Test
    fun `setUserContext stores userId and email`() {
        val reporter = ConsoleCrashReporter()
        reporter.setUserContext("user-123", "user@example.com")
        assertEquals("user-123", reporter.getUserId())
        assertEquals("user@example.com", reporter.getUserEmail())
    }

    @Test
    fun `setUserContext without email stores null email`() {
        val reporter = ConsoleCrashReporter()
        reporter.setUserContext("u1")
        assertEquals("u1", reporter.getUserId())
        assertNull(reporter.getUserEmail())
    }

    @Test
    fun `setCustomKey stores metadata`() {
        val reporter = ConsoleCrashReporter()
        reporter.setCustomKey("build_flavor", "debug")
        reporter.setCustomKey("experiment", "group_b")
        val keys = reporter.getCustomKeys()
        assertEquals("debug", keys["build_flavor"])
        assertEquals("group_b", keys["experiment"])
    }

    @Test
    fun `multiple custom keys are all stored`() {
        val reporter = ConsoleCrashReporter()
        repeat(5) { reporter.setCustomKey("key$it", "val$it") }
        assertEquals(5, reporter.getCustomKeys().size)
    }

    @Test
    fun `recordError with empty metadata does not throw`() {
        val reporter = ConsoleCrashReporter()
        reporter.recordError(IllegalStateException("bad state"))
    }

    @Test
    fun `getCustomKeys is empty before any setCustomKey calls`() {
        val reporter = ConsoleCrashReporter()
        assertTrue(reporter.getCustomKeys().isEmpty())
    }
}
