package com.syzygy.services.filemanagement

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Contract for file-system I/O operations.
 */
interface FileProvider {
    /**
     * Reads and returns the raw bytes of the file at [path].
     *
     * @throws java.io.FileNotFoundException when [path] does not exist.
     */
    fun read(path: String): ByteArray

    /**
     * Writes [data] to [path], creating parent directories and the file
     * itself if necessary.
     */
    fun write(
        path: String,
        data: ByteArray,
    )

    /** Returns `true` when a file or directory exists at [path]. */
    fun exists(path: String): Boolean

    /**
     * Deletes the file at [path].
     *
     * @return `true` when the file existed and was successfully deleted.
     */
    fun delete(path: String): Boolean

    /**
     * Creates a directory (and all necessary parent directories) at [path].
     *
     * @return `true` when the directory was created or already existed.
     */
    fun createDirectory(path: String): Boolean

    /**
     * Deletes the directory at [path] and all of its contents recursively.
     *
     * @return `true` when the directory was deleted.
     */
    fun deleteDirectory(path: String): Boolean

    /**
     * Moves the file at [sourcePath] to [destinationPath], overwriting any
     * existing file at the destination.
     */
    fun move(
        sourcePath: String,
        destinationPath: String,
    )

    /**
     * Copies the file at [sourcePath] to [destinationPath], overwriting any
     * existing file at the destination.
     */
    fun copy(
        sourcePath: String,
        destinationPath: String,
    )

    /**
     * Returns the path to the platform temporary directory.
     *
     * Callers should delete temporary files when they are no longer needed.
     */
    fun tempDirectory(): String
}

/**
 * [FileProvider] implementation backed by [java.io.File] and
 * [java.nio.file.Files].
 */
class JavaFileProvider : FileProvider {
    /** Reads all bytes from the file at [path]. */
    override fun read(path: String): ByteArray = File(path).readBytes()

    /**
     * Writes [data] to [path], creating parent directories as needed.
     */
    override fun write(
        path: String,
        data: ByteArray,
    ) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    /** Returns `true` when something exists at [path]. */
    override fun exists(path: String): Boolean = File(path).exists()

    /** Deletes the file at [path], returning `true` on success. */
    override fun delete(path: String): Boolean = File(path).delete()

    /** Creates [path] as a directory (including any missing parents). */
    override fun createDirectory(path: String): Boolean = File(path).mkdirs()

    /** Recursively deletes the directory tree rooted at [path]. */
    override fun deleteDirectory(path: String): Boolean = File(path).deleteRecursively()

    /**
     * Moves the file at [sourcePath] to [destinationPath] atomically where
     * possible.
     */
    override fun move(
        sourcePath: String,
        destinationPath: String,
    ) {
        Files.move(
            File(sourcePath).toPath(),
            File(destinationPath).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Copies the file at [sourcePath] to [destinationPath]. */
    override fun copy(
        sourcePath: String,
        destinationPath: String,
    ) {
        Files.copy(
            File(sourcePath).toPath(),
            File(destinationPath).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /**
     * Returns the JVM temporary directory path
     * (`java.io.tmpdir` system property).
     */
    override fun tempDirectory(): String = System.getProperty("java.io.tmpdir") ?: "/tmp"
}
