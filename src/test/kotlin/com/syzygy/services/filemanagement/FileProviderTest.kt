package com.syzygy.services.filemanagement

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileProviderTest {
    private val provider = JavaFileProvider()

    private fun tmpPath(suffix: String = ".bin"): String =
        File.createTempFile("syzygy-test", suffix).also { it.deleteOnExit() }.absolutePath

    @Test
    fun `write and read roundtrip`() {
        val path = tmpPath()
        val data = "hello syzygy".toByteArray()
        provider.write(path, data)
        assertContentEquals(data, provider.read(path))
    }

    @Test
    fun `exists returns true after write, false after delete`() {
        val path = tmpPath()
        provider.write(path, ByteArray(0))
        assertTrue(provider.exists(path))
        provider.delete(path)
        assertFalse(provider.exists(path))
    }

    @Test
    fun `createDirectory and deleteDirectory`() {
        val dir = File(provider.tempDirectory(), "syzygy-test-dir-${System.nanoTime()}")
        assertTrue(provider.createDirectory(dir.absolutePath))
        assertTrue(dir.isDirectory)
        assertTrue(provider.deleteDirectory(dir.absolutePath))
        assertFalse(dir.exists())
    }

    @Test
    fun `move relocates file`() {
        val src = tmpPath()
        val dst = File(provider.tempDirectory(), "syzygy-moved-${System.nanoTime()}.bin").absolutePath
        provider.write(src, "move me".toByteArray())
        provider.move(src, dst)
        assertFalse(provider.exists(src))
        assertTrue(provider.exists(dst))
        assertContentEquals("move me".toByteArray(), provider.read(dst))
        File(dst).deleteOnExit()
    }

    @Test
    fun `copy duplicates file`() {
        val src = tmpPath()
        val dst = File(provider.tempDirectory(), "syzygy-copy-${System.nanoTime()}.bin").absolutePath
        provider.write(src, "copy me".toByteArray())
        provider.copy(src, dst)
        assertTrue(provider.exists(src))
        assertTrue(provider.exists(dst))
        assertContentEquals(provider.read(src), provider.read(dst))
        File(dst).deleteOnExit()
    }

    @Test
    fun `tempDirectory returns non-empty path`() {
        val tmpDir = provider.tempDirectory()
        assertNotNull(tmpDir)
        assertTrue(tmpDir.isNotEmpty())
    }

    @Test
    fun `write creates parent directories automatically`() {
        val dir = File(provider.tempDirectory(), "syzygy-nested-${System.nanoTime()}")
        val path = File(dir, "deep/file.txt").absolutePath
        provider.write(path, "nested".toByteArray())
        assertTrue(provider.exists(path))
        dir.deleteRecursively()
    }
}
