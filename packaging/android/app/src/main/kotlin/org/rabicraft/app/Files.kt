package org.rabicraft.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.IOException

/**
 * The app's own file list: the PDFs in `Downloads/RabiCraft` (everything this app saved), a way
 * into the system picker for anywhere else, and the two ways a copy leaves the app.
 *
 * It is built on the system's documents UI (Storage Access Framework), so "anywhere else" means
 * every provider the user has mounted — Drive, another cloud app, a USB stick — with no account,
 * no OAuth and no permission of the app's own. That is why there is no Drive code here: Android
 * already has the Drive picker, and the app only has to ask it for a file.
 */
internal class Files(private val activity: MainActivity) {
    /** The file a "save a copy" is about, once the user has chosen one. */
    private var copyOf: File? = null

    /** Show the file list: what this app saved, and the ways on from here. */
    fun sheet() {
        val saved = saved()
        val rows = saved.map { it.name }.toTypedArray()
        val builder = AlertDialog.Builder(activity).setTitle(R.string.files_title)
        if (rows.isEmpty()) {
            builder.setMessage(R.string.files_empty)
        } else {
            builder.setItems(rows) { _, which -> saved.getOrNull(which)?.let { actions(it) } }
        }
        builder.setPositiveButton(R.string.files_browse) { _, _ -> browse() }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    /** What can be done with one of the app's saves: open it here, or send a copy somewhere. */
    private fun actions(file: File) {
        val choices = arrayOf(
            activity.getString(R.string.files_open),
            activity.getString(R.string.files_send),
            activity.getString(R.string.files_save_as),
        )
        AlertDialog.Builder(activity)
            .setTitle(file.name)
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> open(file)
                    1 -> send(file)
                    2 -> saveAs(file)
                }
            }
            .show()
    }

    /** Hand a saved file to another app (mail, chat, Drive's own uploader). */
    private fun send(file: File) {
        val uri = savedUri(file) ?: run {
            toast(R.string.files_missing)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.files_send)))
        } catch (e: ActivityNotFoundException) {
            Log.e(MainActivity.TAG, "nothing can take a PDF: $e")
            toast(R.string.files_no_app)
        }
    }

    /** The system picker, for a file anywhere: read it, then open it as a tab. */
    private fun browse() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            // The browser build turns images and text into PDFs too.
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*", "text/plain"))
        }
        start(intent, OPEN)
    }

    /**
     * The system's "create a document": the user names a place, and the file is copied there. It
     * reaches Drive or a USB stick for the same reason [browse] does — the picker is the
     * provider's, not the app's.
     */
    private fun saveAs(file: File) {
        copyOf = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        start(intent, CREATE)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data ?: return
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            OPEN -> activity.readDocument(uri)
            CREATE -> copyTo(uri)
        }
    }

    /** Write [copyOf] to the file the user picked. */
    private fun copyTo(uri: Uri) {
        val source = copyOf ?: return
        copyOf = null
        try {
            val bytes = source.readBytes()
            activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IOException("the provider opened no stream")
            toast(R.string.files_copied)
        } catch (e: IOException) {
            Log.e(MainActivity.TAG, "could not copy ${source.name} to $uri: $e")
            toast(R.string.files_copy_failed)
        } catch (e: SecurityException) {
            Log.e(MainActivity.TAG, "refused writing $uri: $e")
            toast(R.string.files_copy_failed)
        }
    }

    /** Open one of the app's own saves, as a content URI like any file from another app. */
    private fun open(file: File) {
        val uri = savedUri(file) ?: run {
            toast(R.string.files_missing)
            return
        }
        activity.readDocument(uri)
    }

    /** The PDFs this app saved, newest first. MediaStore's Downloads folder is this directory. */
    private fun saved(): List<File> {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RabiCraft")
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".pdf", ignoreCase = true) } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * The MediaStore document for one of the app's saves. Below Android 10 there is no Downloads
     * collection, and a `file://` URI never leaves the app (FileUriExposedException): the caller
     * reports the file as missing instead.
     */
    private fun savedUri(file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
        val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/RabiCraft/", file.name)
        return activity.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    }

    private fun start(intent: Intent, code: Int) {
        try {
            activity.startActivityForResult(intent, code)
        } catch (e: ActivityNotFoundException) {
            Log.e(MainActivity.TAG, "no documents UI for $intent: $e")
            toast(R.string.files_no_app)
        }
    }

    private fun toast(text: Int) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }

    internal companion object {
        const val OPEN = 0x5AB2
        const val CREATE = 0x5AB3
    }
}
