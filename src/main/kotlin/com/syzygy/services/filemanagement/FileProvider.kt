package com.syzygy.services.filemanagement

import java.io.File

/**
 * Defines the contract for file I/O operations.
 */
interface FileProvider {
    /** Reads and returns the content of the file at [path]. */
    fun read(path: String): ByteArray

    /** Writes [data] to the file at [path], creating it if necessary. */
    fun write(
        path: String,
        data: ByteArray,
    )

    /** Deletes the file at [path]. */
    fun delete(path: String): Boolean

    /** Returns true if a file exists at [path]. */
    fun exists(path: String): Boolean
}

/**
 * A [FileProvider] backed by [java.io.File].
 */
class JavaFileProvider : FileProvider {
    override fun read(path: String): ByteArray = File(path).readBytes()

    override fun write(
        path: String,
        data: ByteArray,
    ) {
        File(path).writeBytes(data)
    }

    override fun delete(path: String): Boolean = File(path).delete()

    override fun exists(path: String): Boolean = File(path).exists()
}
