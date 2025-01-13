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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.jvm.Throws


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

    @Throws
    private fun appendRelativePathOrThrow(path: String, folder: Boolean): MediaFile {
        require(isDir) { "Requires this to be a directory to appendRelativePath" }

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
    @Throws
    private fun createUriFromContent(values: ContentValues, mime: String?): Uri {
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
            }
        }
        throw IOException("Illegal mime type")
    }

    @Throws
    private fun createUriOrThrow(displayName: String? = namePath): Uri {
        require(displayName != null) { "Requires non null display name" }
        require(isDir) { "Requires this to be a directory to create sub files" }
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

    @Throws
    override fun createFileOrThrow(displayName: String?): SafeFile {
        require(isDir) { "Requires a directory to createFile" }
        require(displayName != null) { "Requires non null name" }

        safe { query(displayName).uri } ?: createUriOrThrow(displayName)
        return appendRelativePathOrThrow(
            displayName,
            false
        ) //SafeFile.fromUri(context,  ?: return null)
    }

    @Throws
    override fun createDirectoryOrThrow(directoryName: String?): SafeFile {
        require(directoryName != null) { "Non null directory" }
        // we don't create a dir here tbh, just fake create it
        return appendRelativePathOrThrow(directoryName, true)
    }

    private data class QueryResult(
        val uri: Uri,
        val lastModified: Long,
        val length: Long,
    )

    @RequiresApi(Build.VERSION_CODES.Q)
    @Throws
    private fun query(displayName: String = namePath): QueryResult {
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
        throw IOException("ContentResolver Query not found")
    }

    @Throws
    override fun uriOrThrow(): Uri {
        return query().uri
    }

    @Throws
    override fun nameOrThrow(): String {
        require(isFile) { "Cant get name of a directory" }
        return namePath
    }

    @Throws
    override fun typeOrThrow(): String {
        throw NotImplementedError()
    }

    override fun filePathOrThrow(): String {
        return replaceDuplicateFileSeparators(relativePath + File.separator + namePath)
    }

    override fun isDirectoryOrThrow(): Boolean {
        return isDir
    }

    override fun isFileOrThrow(): Boolean {
        return isFile
    }

    @Throws
    override fun lastModifiedOrThrow(): Long {
        require(isFile) { "Cant get modified on a directory" }
        return query().lastModified
    }

    @Throws
    override fun lengthOrThrow(): Long {
        require(isFile) { "Cant get length on a directory" }
        val query = query()
        val length = query.length
        if (length <= 0) {
            try {
                contentResolver.openFileDescriptor(query.uri, "r")
                    .use {
                        it?.statSize
                    }?.let {
                        return it
                    }
            } catch (e: FileNotFoundException) {
                throw e
            } catch (t: Throwable) {
                logError(t)
            }

            val inputStream: InputStream = openInputStreamOrThrow()
            return try {
                inputStream.available().toLong()
            } finally {
                inputStream.closeQuietly()
            }
        }
        return length
    }

    @Throws
    override fun canReadOrThrow(): Boolean {
        throw NotImplementedError()
    }

    @Throws
    override fun canWriteOrThrow(): Boolean {
        throw NotImplementedError()
    }

    @Throws
    private fun delete(uri: Uri): Boolean {
        return contentResolver.delete(uri, null, null) > 0
    }

    @Throws
    override fun deleteOrThrow(): Boolean {
        return if (isDir) {
            (listFilesOrThrow()).all {
                it.deleteOrThrow()
            }
        } else {
            delete(uriOrThrow())
        }
    }

    override fun existsOrThrow(): Boolean {
        if (isDir) return true
        try {
            query() // will throw if does not exists
            return true
        } catch (t: Throwable) {
            return false
        }
    }

    @Throws
    override fun listFilesOrThrow(): List<SafeFile> {
        require(isDir) { "Cant list files on a file" }

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

                appendRelativePathOrThrow(name, false).let { new ->
                    out.add(new)
                }
            }

            return out
        } ?: throw IOException("Null contentResolver.query")
    }

    @Throws
    override fun findFileOrThrow(displayName: String?, ignoreCase: Boolean): SafeFile {
        require(isDir) { "Cant find a file is the current is not a directory" }
        require(displayName != null) { "Non null displayName " }

        val new = appendRelativePathOrThrow(displayName, false)
        if (new.existsOrThrow()) {
            return new
        }

        throw FileNotFoundException("No file found")//SafeFile.fromUri(context, query(displayName ?: return null)?.uri ?: return null)
    }

    @Throws
    override fun renameToOrThrow(name: String?): Boolean {
        throw NotImplementedError()
    }

    @Throws
    override fun openOutputStreamOrThrow(append: Boolean): OutputStream {
        // use current file
        uri()?.let { uri ->
            try {
                contentResolver.openOutputStream(
                    uri,
                    if (append) "wa" else "wt"
                )?.let { out ->
                    return out
                    //return OutputStreamWrapper(out, uri, contentResolver)
                }
            } catch (_: Throwable) {
            }
        }

        // create a new file if current is not found,
        // as we know it is new only write access is needed
        val uri = createUriOrThrow()
        contentResolver.openOutputStream(
            uri,
            "w"
        )?.let { out ->
            return out
            //return OutputStreamWrapper(out, uri, contentResolver)
        } ?: throw IOException("Null InputStream")
    }

    @Throws
    override fun openInputStreamOrThrow(): InputStream {
        return contentResolver.openInputStream(uriOrThrow())
            ?: throw IOException("Null InputStream")
    }
}