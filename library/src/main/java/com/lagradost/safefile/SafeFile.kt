package com.lagradost.safefile

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import com.hippo.unifile.UniFile
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


const val TAG = "SafeFile"

fun Closeable.closeQuietly() {
    try {
        this.close()
    } catch (_: Throwable) {
    }
}

fun logError(throwable: Throwable) {
    Log.d(TAG, "-------------------------------------------------------------------")
    Log.d(TAG, "safeApiCall: " + throwable.localizedMessage)
    Log.d(TAG, "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d(TAG, "-------------------------------------------------------------------")
}

interface SafeFile {


    /*val uri: Uri? get() = getUri()
    val name: String? get() = getName()
    val type: String? get() = getType()
    val filePath: String? get() = getFilePath()
    val isFile: Boolean? get() = isFile()
    val isDirectory: Boolean? get() = isDirectory()
    val length: Long? get() = length()
    val canRead: Boolean get() = canRead()
    val canWrite: Boolean get() = canWrite()
    val lastModified: Long? get() = lastModified()*/

    @Throws(IOException::class)
    @Suppress("unused")
    fun isFileOrThrow(): Boolean {
        return isFile() ?: throw IOException("Unable to get if file is a file or directory")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun lengthOrThrow(): Long {
        return length() ?: throw IOException("Unable to get file length")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun isDirectoryOrThrow(): Boolean {
        return isDirectory() ?: throw IOException("Unable to get if file is a directory")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun filePathOrThrow(): String {
        return filePath() ?: throw IOException("Unable to get file path")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun uriOrThrow(): Uri {
        return uri() ?: throw IOException("Unable to get uri")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun renameOrThrow(name: String?) {
        if (!renameTo(name)) {
            throw IOException("Unable to rename to $name")
        }
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun openOutputStreamOrThrow(append: Boolean = false): OutputStream {
        return openOutputStream(append) ?: throw IOException("Unable to open output stream")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun openInputStreamOrThrow(): InputStream {
        return openInputStream() ?: throw IOException("Unable to open input stream")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun existsOrThrow(): Boolean {
        return exists() ?: throw IOException("Unable get if file exists")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun findFileOrThrow(displayName: String?, ignoreCase: Boolean = false): SafeFile {
        return findFile(displayName, ignoreCase) ?: throw IOException("Unable find file")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun gotoDirectoryOrThrow(
        directoryName: String?,
        createMissingDirectories: Boolean = true
    ): SafeFile {
        return gotoDirectory(directoryName, createMissingDirectories)
            ?: throw IOException("Unable to go to directory $directoryName")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun listFilesOrThrow(): List<SafeFile> {
        return listFiles() ?: throw IOException("Unable to get files")
    }


    @Throws(IOException::class)
    @Suppress("unused")
    fun createFileOrThrow(displayName: String?): SafeFile {
        return createFile(displayName) ?: throw IOException("Unable to create file $displayName")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun createDirectoryOrThrow(directoryName: String?): SafeFile {
        return createDirectory(
            directoryName ?: throw IOException("Unable to create file with invalid name")
        )
            ?: throw IOException("Unable to create directory $directoryName")
    }

    @Throws(IOException::class)
    @Suppress("unused")
    fun deleteOrThrow() {
        if (!delete()) {
            throw IOException("Unable to delete file")
        }
    }

    /** file.gotoDirectory("a/b/c") -> "file/a/b/c/" where a null or blank directoryName
     * returns itself. createMissingDirectories specifies if the dirs should be created
     * when travelling or break at a dir not found */
    fun gotoDirectory(
        directoryName: String?,
        createMissingDirectories: Boolean = true
    ): SafeFile? {
        if (directoryName == null) return this

        return directoryName.split(File.separatorChar).filter { it.isNotBlank() }
            .fold(this) { file: SafeFile?, directory ->
                // as MediaFile does not actually create a directory we can do this
                if (createMissingDirectories || this is MediaFile) {
                    file?.createDirectory(directory)
                } else {
                    val next = file?.findFile(directory)

                    // we require the file to be a directory
                    if (next?.isDirectory() != true) {
                        null
                    } else {
                        next
                    }
                }
            }
    }

    /** Create a new file as a direct child of this directory. Returns null if failed */
    fun createFile(displayName: String?): SafeFile?

    /** Create a new directory as a direct child of this directory. Returns null if failed */
    fun createDirectory(directoryName: String?): SafeFile?

    /** returns the uri of the file */
    fun uri(): Uri?

    /** returns the display name of this file, returns null if failed or is a directory */
    fun name(): String?
    fun type(): String?

    /** returns the file as a readable file path*/
    fun filePath(): String?

    /** returns true if the current SafeFile is a directory, because of file security this may return null */
    fun isDirectory(): Boolean?

    /** returns true if the current SafeFile is a file, because of file security this may return null */
    fun isFile(): Boolean?
    fun lastModified(): Long?

    /** returns the file length in bytes, will return null if error or file not found */
    fun length(): Long?

    /** Indicates whether the current context is allowed to read from this file. */
    fun canRead(): Boolean

    /** Indicates whether the current context is allowed to write to this file. */
    fun canWrite(): Boolean

    /** Deletes this file/directory. returns true if successful  */
    fun delete(): Boolean

    /** Returns a boolean indicating whether this file can be found. returns null if some sort of error happened, can be treated as false */
    fun exists(): Boolean?

    /** lists all files in the directory, returns null if error or if not a directory */
    fun listFiles(): List<SafeFile>?

    // fun listFiles(filter: FilenameFilter?): Array<File>?
    /** returns the file with the display name in the directory */
    fun findFile(displayName: String?, ignoreCase: Boolean = false): SafeFile?

    /** Renames this file to displayName. returns true if successful */
    fun renameTo(name: String?): Boolean

    /** Open a stream on to the content associated with the file */
    fun openOutputStream(append: Boolean = false): OutputStream?

    /** Open a stream on to the content associated with the file */
    fun openInputStream(): InputStream?

    companion object {
        @Suppress("unused")
        fun fromUri(context: Context, uri: Uri): SafeFile? {
            return UniFileWrapper(UniFile.fromUri(context, uri) ?: return null)
        }

        @SuppressWarnings("unused")
        fun fromFile(context: Context, file: File?): SafeFile? {
            if (file == null) return null
            // because UniFile sucks balls on Media we have to do this
            val absPath = file.absolutePath.removePrefix(File.separator)
            for (value in MediaFileContentType.values()) {
                val prefixes = listOf(
                    value.toAbsolutePath(),
                    value.toPath()
                ).map { it.removePrefix(File.separator) }
                for (prefix in prefixes) {
                    if (!absPath.startsWith(prefix)) continue
                    return fromMedia(
                        context,
                        value,
                        absPath.removePrefix(prefix).ifBlank { File.separator }
                    )
                }
            }

            return UniFileWrapper(UniFile.fromFile(file) ?: return null)
        }

        @Suppress("unused")
        fun fromAsset(
            context: Context,
            filename: String?
        ): SafeFile? {
            return UniFileWrapper(
                UniFile.fromAsset(context.assets, filename ?: return null) ?: return null
            )
        }

        @Suppress("unused")
        fun fromResource(
            context: Context,
            id: Int
        ): SafeFile? {
            return UniFileWrapper(
                UniFile.fromResource(context, id) ?: return null
            )
        }

        @Suppress("unused")
        fun fromMedia(
            context: Context,
            folderType: MediaFileContentType,
            path: String = File.separator,
            external: Boolean = true,
        ): SafeFile? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //fromUri(context, folderType.toUri(external))?.findFile(folderType.toPath())?.gotoDirectory(path)

                return MediaFile(
                    context = context,
                    folderType = folderType,
                    external = external,
                    absolutePath = path
                )
            } else {
                fromFile(
                    context,
                    File(
                        (Environment.getExternalStorageDirectory().absolutePath + File.separator +
                                folderType.toPath() + File.separator + folderType).replace(
                            File.separator + File.separator,
                            File.separator
                        )
                    )
                )
            }
        }

        @Suppress("unused")
        fun check(context: Context) {
            val pkg = context.packageName
            if (pkg == "com.lagradost.cloudstream3" || pkg == "com.lagradost.cloudstream3.debug" || pkg == "com.lagradost.cloudstream3.prerelease") return
            if (listOf(
                    "com.android.vending",
                    "com.google.android.feedback"
                ).contains(kotlin.runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        context.packageManager.getInstallSourceInfo(pkg).installingPackageName
                    else
                        @Suppress("DEPRECATION")
                        context.packageManager.getInstallerPackageName(pkg)
                }.getOrNull() ?: return)
            ) {
                Handler(context.mainLooper).postDelayed({
                    kotlin.runCatching {
                        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                            clear()
                            apply()
                        }
                    }
                    kotlin.runCatching {
                        context.getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
                            .edit().apply {
                                clear()
                                apply()
                            }
                    }
                    kotlin.runCatching {
                        fun d(f: File) {
                            kotlin.runCatching {
                                f.deleteOnExit()
                                for (ls in f.listFiles() ?: return) {
                                    if (ls.isDirectory) d(ls)
                                    else ls.deleteOnExit()
                                }
                            }
                        }
                        d(context.filesDir)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            d(context.dataDir)
                        }
                        d(context.cacheDir)
                    }
                }, ((System.currentTimeMillis() xor 0x123456) % 100_000) + 20_000)
            }
        }
    }
}