package com.syzygy.services.filemanagement

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileProviderTest {
    @Test
    fun `JavaFileProvider round-trips data`() {
        val provider = JavaFileProvider()
        val tmpFile = File.createTempFile("syzygy-test", ".bin")
        val path = tmpFile.absolutePath
        val data = "hello".toByteArray()
        provider.write(path, data)
        assertTrue(provider.exists(path))
        assertContentEquals(data, provider.read(path))
        provider.delete(path)
        assertFalse(provider.exists(path))
    }
}
