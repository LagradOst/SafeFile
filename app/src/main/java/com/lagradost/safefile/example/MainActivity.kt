package com.lagradost.safefile.example

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile

class MainActivity : AppCompatActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        super.onCreate(savedInstanceState)

        val context: Context = this

        // create file in internal files dir
        val createdInternal =
            null != SafeFile.fromFile(context, this.filesDir)?.createFile("HelloWorld.txt")
                ?.openOutputStream(append = false)?.use { out ->
                    out.bufferedWriter().apply {
                        write("Hello, World! from /filesDir")
                        flush()
                    }
                }

        // create file in download directory
        val createdExternal = null != SafeFile.fromMedia(context, MediaFileContentType.Downloads)
            ?.createFile("HelloWorldDownloads.txt")?.openOutputStream(append = false)?.use { out ->
                out.bufferedWriter().apply {
                    write("Hello, World! from /Download")
                    flush()
                }
            }

        // list files
        val dir = this.filesDir
        val files = SafeFile.fromFile(context, dir)?.listFiles()?.map {
            "$dir / ${it.name()} (${it.length()}bytes)"
        }

        files?.forEach { file ->
            Log.i("MainActivity", file)
        }

        findViewById<TextView>(R.id.hello)?.text =
            "Created Internal: $createdInternal\nCreated External: $createdExternal\nFiles:\n" + files?.joinToString(
                separator = "\n\n"
            )
    }
}