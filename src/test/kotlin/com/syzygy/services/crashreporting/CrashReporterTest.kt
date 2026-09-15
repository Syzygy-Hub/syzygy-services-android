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

    // ------------------------------------------------------------------
    // ITEM 5 — Breadcrumb tests
    // ------------------------------------------------------------------

    @Test
    fun `leaveBreadcrumb stores breadcrumb message`() {
        val reporter = ConsoleCrashReporter()
        reporter.leaveBreadcrumb("user tapped login button")
        val crumbs = reporter.getBreadcrumbs()
        assertEquals(1, crumbs.size)
        assertEquals("user tapped login button", crumbs[0].message)
    }

    @Test
    fun `leaveBreadcrumb stores optional metadata`() {
        val reporter = ConsoleCrashReporter()
        reporter.leaveBreadcrumb("network call", mapOf("url" to "https://example.com"))
        val crumbs = reporter.getBreadcrumbs()
        assertEquals(1, crumbs.size)
        assertEquals("https://example.com", crumbs[0].metadata?.get("url"))
    }

    @Test
    fun `leaveBreadcrumb without metadata stores null metadata`() {
        val reporter = ConsoleCrashReporter()
        reporter.leaveBreadcrumb("event without metadata")
        assertNull(reporter.getBreadcrumbs()[0].metadata)
    }

    @Test
    fun `breadcrumbs are retained in insertion order`() {
        val reporter = ConsoleCrashReporter()
        reporter.leaveBreadcrumb("first")
        reporter.leaveBreadcrumb("second")
        reporter.leaveBreadcrumb("third")
        val crumbs = reporter.getBreadcrumbs()
        assertEquals(listOf("first", "second", "third"), crumbs.map { it.message })
    }

    @Test
    fun `circular buffer evicts oldest when max breadcrumbs exceeded`() {
        val reporter = ConsoleCrashReporter()
        repeat(ConsoleCrashReporter.MAX_BREADCRUMBS + 5) { i ->
            reporter.leaveBreadcrumb("crumb-$i")
        }
        val crumbs = reporter.getBreadcrumbs()
        assertEquals(ConsoleCrashReporter.MAX_BREADCRUMBS, crumbs.size)
        // The oldest 5 should be gone; first retained crumb is "crumb-5"
        assertEquals("crumb-5", crumbs[0].message)
    }

    @Test
    fun `clearBreadcrumbs removes all stored breadcrumbs`() {
        val reporter = ConsoleCrashReporter()
        reporter.leaveBreadcrumb("a")
        reporter.leaveBreadcrumb("b")
        reporter.clearBreadcrumbs()
        assertTrue(reporter.getBreadcrumbs().isEmpty())
    }

    @Test
    fun `getBreadcrumbs is empty before any leaveBreadcrumb calls`() {
        val reporter = ConsoleCrashReporter()
        assertTrue(reporter.getBreadcrumbs().isEmpty())
    }
}
