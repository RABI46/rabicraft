package org.rabicraft.app.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rabicraft.app.data.local.AppDatabase
import org.rabicraft.app.data.local.DocumentDao
import org.rabicraft.app.data.local.DocumentEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocumentRepository(
    private val context: Context
) {
    private val dao: DocumentDao? by lazy {
        try {
            AppDatabase.getInstance(context).documentDao()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    // In-memory fallback if Room annotation processor isn't present
    private val fallbackList = mutableListOf<DocumentEntity>()

    private val _allDocuments = MutableStateFlow<List<DocumentEntity>>(emptyList())
    val allDocuments: StateFlow<List<DocumentEntity>> = _allDocuments.asStateFlow()

    private val _distinctGroups = MutableStateFlow<List<String>>(listOf("General", "Specimens"))
    val distinctGroups: StateFlow<List<String>> = _distinctGroups.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            refreshData()
        }
    }

    suspend fun refreshData() = withContext(Dispatchers.IO) {
        try {
            val list = dao?.allDocuments ?: fallbackList.toList()
            _allDocuments.value = list

            val groups = (dao?.distinctGroups ?: fallbackList.map { it.groupName }).distinct()
            _distinctGroups.value = (listOf("General", "Specimens") + groups).distinct()
        } catch (e: Throwable) {
            e.printStackTrace()
            _allDocuments.value = fallbackList.toList()
        }
    }

    suspend fun getDocumentById(id: String): DocumentEntity? = withContext(Dispatchers.IO) {
        try {
            dao?.getDocumentById(id) ?: fallbackList.firstOrNull { it.id == id }
        } catch (e: Throwable) {
            e.printStackTrace()
            fallbackList.firstOrNull { it.id == id }
        }
    }

    suspend fun importDocument(
        uri: Uri,
        customName: String? = null,
        groupName: String = "General",
        tags: List<String> = emptyList()
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val fileName = customName ?: getFileNameFromUri(uri) ?: "Imported_${System.currentTimeMillis()}.pdf"
        val cleanFileName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"

        val localDir = File(context.filesDir, "imported_docs").apply { mkdirs() }
        val targetFile = File(localDir, "${UUID.randomUUID()}_$cleanFileName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read from URI: $uri")

        val pageCount = try {
            val pfd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (_: Exception) {
            1
        }

        val entity = DocumentEntity(
            UUID.randomUUID().toString(),
            cleanFileName.removeSuffix(".pdf"),
            cleanFileName,
            targetFile.absolutePath,
            targetFile.length(),
            pageCount,
            groupName.ifBlank { "General" },
            tags.joinToString(", "),
            false,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            false
        )

        try {
            dao?.insertDocument(entity)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        fallbackList.add(entity)
        refreshData()
        entity
    }

    suspend fun ensureShowcaseDocument(showcaseFile: File): DocumentEntity = withContext(Dispatchers.IO) {
        val existing = getDocumentById("showcase-doc-id")
        if (existing != null && File(existing.filePath).exists()) {
            return@withContext existing
        }

        val pageCount = try {
            val pfd = ParcelFileDescriptor.open(showcaseFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (_: Exception) {
            5
        }

        val showcaseEntity = DocumentEntity(
            "showcase-doc-id",
            "RabiCraft Showcase",
            "RabiCraft_Showcase.pdf",
            showcaseFile.absolutePath,
            showcaseFile.length(),
            pageCount,
            "Specimens",
            "Specimen, Showcase, Featured, Guide",
            true,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            true
        )

        try {
            dao?.insertDocument(showcaseEntity)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        if (fallbackList.none { it.id == showcaseEntity.id }) {
            fallbackList.add(showcaseEntity)
        }
        refreshData()
        showcaseEntity
    }

    suspend fun renameDocument(id: String, newName: String) = withContext(Dispatchers.IO) {
        if (newName.isNotBlank()) {
            val cleanName = newName.trim().removeSuffix(".pdf")
            try {
                dao?.renameDocument(id, cleanName)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            val idx = fallbackList.indexOfFirst { it.id == id }
            if (idx != -1) {
                val cur = fallbackList[idx]
                fallbackList[idx] = DocumentEntity(
                    cur.id, cleanName, cur.originalFileName, cur.filePath, cur.fileSizeBytes,
                    cur.pageCount, cur.groupName, cur.tagsCsv, cur.isStarred, cur.lastOpenedTimestamp,
                    cur.createdAtTimestamp, cur.isSample
                )
            }
            refreshData()
        }
    }

    suspend fun updateGroup(id: String, newGroup: String) = withContext(Dispatchers.IO) {
        val cleanGroup = newGroup.trim().ifBlank { "General" }
        try {
            dao?.updateDocumentGroup(id, cleanGroup)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        val idx = fallbackList.indexOfFirst { it.id == id }
        if (idx != -1) {
            val cur = fallbackList[idx]
            fallbackList[idx] = DocumentEntity(
                cur.id, cur.name, cur.originalFileName, cur.filePath, cur.fileSizeBytes,
                cur.pageCount, cleanGroup, cur.tagsCsv, cur.isStarred, cur.lastOpenedTimestamp,
                cur.createdAtTimestamp, cur.isSample
            )
        }
        refreshData()
    }

    suspend fun updateTags(id: String, tags: List<String>) = withContext(Dispatchers.IO) {
        val tagsString = tags.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        try {
            dao?.updateDocumentTags(id, tagsString)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        val idx = fallbackList.indexOfFirst { it.id == id }
        if (idx != -1) {
            val cur = fallbackList[idx]
            fallbackList[idx] = DocumentEntity(
                cur.id, cur.name, cur.originalFileName, cur.filePath, cur.fileSizeBytes,
                cur.pageCount, cur.groupName, tagsString, cur.isStarred, cur.lastOpenedTimestamp,
                cur.createdAtTimestamp, cur.isSample
            )
        }
        refreshData()
    }

    suspend fun toggleStarred(id: String, currentStarred: Boolean) = withContext(Dispatchers.IO) {
        try {
            dao?.setDocumentStarred(id, !currentStarred)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        val idx = fallbackList.indexOfFirst { it.id == id }
        if (idx != -1) {
            val cur = fallbackList[idx]
            fallbackList[idx] = DocumentEntity(
                cur.id, cur.name, cur.originalFileName, cur.filePath, cur.fileSizeBytes,
                cur.pageCount, cur.groupName, cur.tagsCsv, !currentStarred, cur.lastOpenedTimestamp,
                cur.createdAtTimestamp, cur.isSample
            )
        }
        refreshData()
    }

    suspend fun updateLastOpened(id: String) = withContext(Dispatchers.IO) {
        try {
            dao?.updateLastOpened(id, System.currentTimeMillis())
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        refreshData()
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        val doc = getDocumentById(id)
        if (doc != null && !doc.isSample) {
            try {
                val f = File(doc.filePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        try {
            dao?.deleteDocumentById(id)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        fallbackList.removeAll { it.id == id }
        refreshData()
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) {
                    name = it.getString(nameIdx)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }
}
