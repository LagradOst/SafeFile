package com.lagradost.safefile

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws

private fun UniFile.toFile(context: Context): SafeFile {
    return UniFileWrapper(context, this)
}

fun <T> safe(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        null
    }
}

class UniFileWrapper(
    private val context: Context, private val file: UniFile
) : SafeFile {
    private val contentResolver: ContentResolver = context.contentResolver

    @Throws
    override fun createFileOrThrow(displayName: String?): SafeFile {
        return (file.createFile(displayName) ?: throw IOException("Unable to create file")).toFile(
            context
        )
    }

    @Throws
    override fun createDirectoryOrThrow(directoryName: String?): SafeFile {
        return (file.createDirectory(directoryName)
            ?: throw IOException("Unable to create directory")).toFile(context)
    }

    @Throws
    override fun uriOrThrow(): Uri {
        return file.uri
    }

    @Throws
    override fun nameOrThrow(): String {
        return file.name ?: throw IOException("Null name")
    }

    @Throws
    override fun typeOrThrow(): String {
        return file.type ?: throw IOException("Null type")
    }

    @Throws
    override fun filePathOrThrow(): String {
        return file.filePath ?: throw IOException("Null filepath")
    }

    @Throws
    override fun isDirectoryOrThrow(): Boolean {
        return file.isDirectory
    }

    @Throws
    override fun isFileOrThrow(): Boolean {
        return file.isFile
    }

    @Throws
    override fun lastModifiedOrThrow(): Long {
        return file.lastModified()
    }

    @Throws
    override fun lengthOrThrow(): Long {
        try {
            val len = file.length()
            if (len == -1L) throw IOException("-1 Length")

            return if (len <= 1) {
                val inputStream = this.openInputStreamOrThrow()
                try {
                    inputStream.available().toLong()
                } finally {
                    inputStream.closeQuietly()
                }
            } else {
                len
            }
        } catch (t: Throwable) {
            // extra check with uri
            return safe {
                contentResolver.openFileDescriptor(uriOrThrow(), "r")
                    .use {
                        it?.statSize
                    }
            } ?: throw t
        }
    }

    @Throws
    override fun canReadOrThrow(): Boolean {
        return file.canRead()
    }

    @Throws
    override fun canWriteOrThrow(): Boolean {
        return file.canWrite()
    }

    @Throws
    override fun deleteOrThrow(): Boolean {
        try {
            return file.delete()
        } catch (t: Throwable) {
            // extra check if we can do it with the uri instead
            uri()?.let { uri ->
                return contentResolver.delete(uri, null, null) > 0
            }

            throw t
        }
    }

    @Throws
    override fun existsOrThrow(): Boolean {
        return file.exists()
    }

    @Throws
    override fun listFilesOrThrow(): List<SafeFile> {
        return file.listFiles()?.mapNotNull { it?.toFile(context) } ?: throw FileNotFoundException()
    }

    @Throws
    override fun findFileOrThrow(displayName: String?, ignoreCase: Boolean): SafeFile {
        return (file.findFile(displayName) ?: throw FileNotFoundException()).toFile(context)
    }

    @Throws
    override fun renameToOrThrow(name: String?): Boolean {
        return file.renameTo(name)
    }

    @Throws
    override fun openOutputStreamOrThrow(append: Boolean): OutputStream {
        try {
            return file.openOutputStream(append)
        } catch (t: Throwable) {
            // extra check if we can use uri instead
            uri()?.let { uri ->
                try {
                    contentResolver.openOutputStream(
                        uri,
                        if (append) "wa" else "wt"
                    )?.let { out ->
                        return out
                    }
                } catch (_: Throwable) {
                }
            }
            throw t
        }
    }

    @Throws
    override fun openInputStreamOrThrow(): InputStream {
        return try {
            file.openInputStream()
        } catch (t: Throwable) {
            // extra check if we can use the uri instead
            safe { contentResolver.openInputStream(uriOrThrow()) } ?: throw t
        }
    }

    //override fun createRandomAccessFile(mode: String?): UniRandomAccessFile? {
    //    return safe { file.createRandomAccessFile(mode) }
    //}
}
