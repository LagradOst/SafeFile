package com.lagradost.safefile

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.OutputStream

// unused wrapper for MediaStore.MediaColumns.IS_PENDING
class OutputStreamWrapper(
    val stream: OutputStream,
    private val uri: Uri,
    private val contentResolver: ContentResolver
) : OutputStream() {

    private fun setPending(pending: Boolean) {
        try {
            contentResolver.update(
                uri,
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.IS_PENDING,
                        if (pending) 1 else 0
                    )
                },
                null,
                null
            )
        } catch (t: Throwable) {
            logError(t)
        }
    }

    init {
        //setPending(true)
    }

    override fun close() {
       // setPending(false)
        stream.close()
    }

    override fun flush() {
        stream.flush()
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        stream.write(b, off, len)
    }

    override fun equals(other: Any?): Boolean {
        if (other is OutputStreamWrapper) {
            return other.stream == stream
        }
        return stream == other
    }

    override fun hashCode(): Int {
        return stream.hashCode()
    }

    override fun toString(): String {
        return stream.toString()
    }

    override fun write(b: ByteArray?) {
        stream.write(b)
    }

    override fun write(b: Int) {
        stream.write(b)
    }
}