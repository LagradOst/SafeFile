package com.lagradost.safefile

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream


enum class MediaFileContentType {
    Downloads,
    Audio,
    Video,
    Images,
}

// https://developer.android.com/training/data-storage/shared/media

@SuppressWarnings("unused")
fun MediaFileContentType.toPath(): String {
    return when (this) {
        MediaFileContentType.Downloads -> Environment.DIRECTORY_DOWNLOADS
        MediaFileContentType.Audio -> Environment.DIRECTORY_MUSIC
        MediaFileContentType.Video -> Environment.DIRECTORY_MOVIES
        MediaFileContentType.Images -> Environment.DIRECTORY_PICTURES
    }
}

@Suppress("unused")
fun MediaFileContentType.defaultPrefix(): String {
    return Environment.getExternalStorageDirectory().absolutePath
}

@Suppress("unused")
fun MediaFileContentType.toAbsolutePath(): String {
    return replaceDuplicateFileSeparators(
        defaultPrefix() + File.separator +
                this.toPath()
    )
}

@Suppress("unused")
fun replaceDuplicateFileSeparators(path: String): String {
    return path.replace(Regex("${File.separator}+"), File.separator)
}

@RequiresApi(Build.VERSION_CODES.Q)
@Suppress("unused")
fun MediaFileContentType.toUri(external: Boolean): Uri {
    // https://developer.android.com/training/data-storage/shared/media#add-item
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val volume =
            if (external) MediaStore.VOLUME_EXTERNAL_PRIMARY else MediaStore.VOLUME_INTERNAL
        when (this) {
            MediaFileContentType.Downloads -> MediaStore.Downloads.getContentUri(volume)
            MediaFileContentType.Audio -> MediaStore.Audio.Media.getContentUri(volume)
            MediaFileContentType.Video -> MediaStore.Video.Media.getContentUri(volume)
            MediaFileContentType.Images -> MediaStore.Images.Media.getContentUri(volume)
        }
    } else {
        if (external) {
            when (this) {
                MediaFileContentType.Downloads -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                MediaFileContentType.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                MediaFileContentType.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaFileContentType.Images -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            when (this) {
                MediaFileContentType.Downloads -> MediaStore.Downloads.INTERNAL_CONTENT_URI
                MediaFileContentType.Audio -> MediaStore.Audio.Media.INTERNAL_CONTENT_URI
                MediaFileContentType.Video -> MediaStore.Video.Media.INTERNAL_CONTENT_URI
                MediaFileContentType.Images -> MediaStore.Images.Media.INTERNAL_CONTENT_URI
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
class MediaFile(
    private val context: Context,
    private val folderType: MediaFileContentType,
    private val external: Boolean = true,
    absolutePath: String,
) : SafeFile {
    override fun toString(): String {
        return sanitizedAbsolutePath
    }

    // this is the path relative to the download directory so "/hello/text.txt" = "hello/text.txt" is in fact "Download/hello/text.txt"
    private val sanitizedAbsolutePath: String =
        replaceDuplicateFileSeparators(absolutePath)

    // this is only a directory if the filepath ends with a /
    private val isDir: Boolean = sanitizedAbsolutePath.endsWith(File.separator)
    private val isFile: Boolean = !isDir

    // this is the relative path including the Download directory, so "/hello/text.txt" => "Download/hello"
    private val relativePath: String =
        replaceDuplicateFileSeparators(folderType.toPath() + File.separator + sanitizedAbsolutePath).substringBeforeLast(
            File.separator
        )

    // "/hello/text.txt" => "text.txt"
    private val namePath: String = sanitizedAbsolutePath.substringAfterLast(File.separator)
    private val baseUri = folderType.toUri(external)
    private val contentResolver: ContentResolver = context.contentResolver

    init {
        // some standard asserts that should always be hold or else this class wont work
        assert(!relativePath.endsWith(File.separator))
        assert(!(isDir && isFile))
        assert(!relativePath.contains(File.separator + File.separator))
        assert(!namePath.contains(File.separator))

        if (isDir) {
            assert(namePath.isBlank())
        } else {
            assert(namePath.isNotBlank())
        }
    }

    companion object {
        private fun splitFilenameExt(name: String): Pair<String, String?> {
            val split = name.indexOfLast { it == '.' }
            if (split <= 0) return name to null
            val ext = name.substring(split + 1 until name.length)
            if (ext.isBlank()) return name to null

            return name.substring(0 until split) to ext
        }

        private fun splitFilenameMime(name: String): Pair<String, String?> {
            val (display, ext) = splitFilenameExt(name)
            val mimeType = MimeTypes.fromExtToMime(ext)

            return display to mimeType
        }
    }

    private fun appendRelativePath(path: String, folder: Boolean): MediaFile? {
        if (isFile) return null

        // VideoDownloadManager.sanitizeFilename(path.replace(File.separator, ""))

        // in case of duplicate path, aka Download -> Download
        if (relativePath == path) return this

        val newPath =
            sanitizedAbsolutePath + path + if (folder) File.separator else ""

        return MediaFile(
            context = context,
            folderType = folderType,
            external = external,
            absolutePath = newPath
        )
    }

    private fun niceMime(mime: String): String {
        return when (folderType) {
            // I assert that Downloads can take any type
            MediaFileContentType.Downloads -> return mime
            MediaFileContentType.Audio -> "audio/"
            MediaFileContentType.Video -> "video/"
            MediaFileContentType.Images -> "image/"
        } + mime.substringAfter("/")
    }

    // because android is funky we do this, trying to check if mimetype changes the outcome
    private fun createUriFromContent(values: ContentValues, mime: String?): Uri? {
        val mimeTypes =
            if (mime == null) listOf(null) else listOf(mime, niceMime(mime), null).distinct()

        for (m in mimeTypes) {
            if (m != null)
                values.put(MediaStore.MediaColumns.MIME_TYPE, m)
            else values.remove(MediaStore.MediaColumns.MIME_TYPE)

            try {
                return contentResolver.insert(baseUri, values) ?: continue
            } catch (e: IllegalArgumentException) {
                logError(e)
                continue
            } catch (t: Throwable) {
                logError(t)
                break
            }
        }
        return null
    }

    private fun createUri(displayName: String? = namePath): Uri? {
        if (displayName == null) return null
        if (isFile) return null
        val (name, mime) = splitFilenameMime(displayName)

        val newFile = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.TITLE, name)

            // this just makes shit disappear and will cause bugs
            // put(MediaStore.MediaColumns.IS_PENDING, 1)

            // unspecified RELATIVE_PATH places it in the top directory
            if (relativePath.contains(File.separator)) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath + File.separator)
            }
        }

        return createUriFromContent(newFile, mime)
    }

    override fun createFile(displayName: String?): SafeFile? {
        if (isFile || displayName == null) return null
        query(displayName)?.uri ?: createUri(displayName) ?: return null
        return appendRelativePath(displayName, false) //SafeFile.fromUri(context,  ?: return null)
    }

    override fun createDirectory(directoryName: String?): SafeFile? {
        if (directoryName == null) return null
        // we don't create a dir here tbh, just fake create it
        return appendRelativePath(directoryName, true)
    }

    private data class QueryResult(
        val uri: Uri,
        val lastModified: Long,
        val length: Long,
    )

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun query(displayName: String = namePath): QueryResult? {
        try {
            //val (name, mime) = splitFilenameMime(fullName)

            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.SIZE,
            )

            val selection =
                "${MediaStore.MediaColumns.RELATIVE_PATH}='$relativePath${File.separator}' AND ${MediaStore.MediaColumns.DISPLAY_NAME}='$displayName'"

            contentResolver.query(
                baseUri,
                projection, selection, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id =
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))

                    return QueryResult(
                        uri = ContentUris.withAppendedId(
                            baseUri, id
                        ),
                        lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)),
                        length = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                    )
                }
            }
        } catch (t: Throwable) {
            logError(t)
        }

        return null
    }

    override fun uri(): Uri? {
        return query()?.uri
    }

    override fun name(): String? {
        if (isDir) return null
        return namePath
    }

    override fun type(): String? {
        return null //TODO("Not yet implemented")
    }

    override fun filePath(): String {
        return replaceDuplicateFileSeparators(relativePath + File.separator + namePath)
    }

    override fun isDirectory(): Boolean {
        return isDir
    }

    override fun isFile(): Boolean {
        return isFile
    }

    override fun lastModified(): Long? {
        if (isDir) return null
        return query()?.lastModified
    }

    override fun length(): Long? {
        if (isDir) return null
        val query = query()
        val length = query?.length ?: return null
        if (length <= 0) {
            try {
                contentResolver.openFileDescriptor(query.uri, "r")
                    .use {
                        it?.statSize
                    }?.let {
                        return it
                    }
            } catch (e: FileNotFoundException) {
                return null
            } catch (t: Throwable) {
                logError(t)
            }

            val inputStream: InputStream = openInputStream() ?: return null
            return try {
                inputStream.available().toLong()
            } catch (t: Throwable) {
                null
            } finally {
                inputStream.closeQuietly()
            }
        }
        return length
    }

    override fun canRead(): Boolean {
        return true //TODO("Not yet implemented")
    }

    override fun canWrite(): Boolean {
        return true //TODO("Not yet implemented")
    }

    private fun delete(uri: Uri): Boolean {
        return contentResolver.delete(uri, null, null) > 0
    }

    override fun delete(): Boolean {
        return if (isDir) {
            (listFiles() ?: return false).all {
                it.delete()
            }
        } else {
            delete(uri() ?: return false)
        }
    }

    override fun exists(): Boolean {
        if (isDir) return true
        return query() != null
    }

    override fun listFiles(): List<SafeFile>? {
        if (isFile) return null
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME
            )

            val selection =
                "${MediaStore.MediaColumns.RELATIVE_PATH}='$relativePath${File.separator}'"
            contentResolver.query(
                baseUri,
                projection, selection, null, null
            )?.use { cursor ->
                val out = ArrayList<SafeFile>(cursor.count)
                while (cursor.moveToNext()) {
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIdx == -1) continue
                    val name = cursor.getString(nameIdx)

                    appendRelativePath(name, false)?.let { new ->
                        out.add(new)
                    }
                }

                return out
            }
        } catch (t: Throwable) {
            logError(t)
        }
        return null
    }

    override fun findFile(displayName: String?, ignoreCase: Boolean): SafeFile? {
        if (isFile || displayName == null) return null

        val new = appendRelativePath(displayName, false) ?: return null
        if (new.exists()) {
            return new
        }

        return null//SafeFile.fromUri(context, query(displayName ?: return null)?.uri ?: return null)
    }

    override fun renameTo(name: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun openOutputStream(append: Boolean): OutputStream? {
        try {
            // use current file
            uri()?.let { uri ->
                contentResolver.openOutputStream(
                    uri,
                    if (append) "wa" else "wt"
                )?.let { out ->
                    return out
                    //return OutputStreamWrapper(out, uri, contentResolver)
                }
            }

            // create a new file if current is not found,
            // as we know it is new only write access is needed
            createUri()?.let { uri ->
                contentResolver.openOutputStream(
                    uri,
                    "w"
                )?.let { out ->
                    return out
                    //return OutputStreamWrapper(out, uri, contentResolver)
                }
            }
            return null
        } catch (t: Throwable) {
            return null
        }
    }

    override fun openInputStream(): InputStream? {
        try {
            return contentResolver.openInputStream(uri() ?: return null)
        } catch (t: Throwable) {
            return null
        }
    }
}