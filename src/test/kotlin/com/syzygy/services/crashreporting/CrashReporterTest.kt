package com.syzygy.services.crashreporting

import kotlin.test.Test

class CrashReporterTest {
    @Test
    fun `ConsoleCrashReporter records error without throwing`() {
        val reporter = ConsoleCrashReporter()
        reporter.recordError(RuntimeException("test"), mapOf("ctx" to "test"))
        reporter.reportCrash("test crash", emptyMap())
    }
}
