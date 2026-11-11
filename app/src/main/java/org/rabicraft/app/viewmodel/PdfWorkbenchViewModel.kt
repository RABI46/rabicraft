package org.rabicraft.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rabicraft.app.data.AppLanguage
import org.rabicraft.app.data.DocumentRepository
import org.rabicraft.app.data.LanguageStore
import org.rabicraft.app.data.local.DocumentEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import org.rabicraft.app.data.PdfRepository
import org.rabicraft.app.data.ocr.OcrEngine
import org.rabicraft.app.data.ocr.OcrPageResult
import org.rabicraft.app.data.tools.ToolPlanner
import org.rabicraft.app.model.AnnotationItem
import org.rabicraft.app.model.AnnotationType
import org.rabicraft.app.model.BookmarkItem
import org.rabicraft.app.model.CommentItem
import org.rabicraft.app.model.CommentReply
import org.rabicraft.app.model.EditKind
import org.rabicraft.app.model.FieldType
import org.rabicraft.app.model.FormFieldItem
import org.rabicraft.app.model.PageEdit
import org.rabicraft.app.model.PageItem
import org.rabicraft.app.model.PageLayoutMode
import org.rabicraft.app.model.PdfDocumentInfo
import org.rabicraft.app.model.ReadingTheme
import org.rabicraft.app.model.SearchMatch
import org.rabicraft.app.model.ToolConfig
import org.rabicraft.app.model.WorkbenchMode
import java.io.File
import java.util.UUID

class PdfWorkbenchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PdfRepository(application.applicationContext)
    private val documentRepository = DocumentRepository(application.applicationContext)
    private val languageStore = LanguageStore(application.applicationContext)
    private val ocrEngine = OcrEngine()

    private val _language = MutableStateFlow(languageStore.current())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    // Objects placed in "Edit PDF" mode, keyed by page index. Flattened into the export.
    private val _edits = MutableStateFlow<Map<Int, List<PageEdit>>>(emptyMap())
    val edits: StateFlow<Map<Int, List<PageEdit>>> = _edits.asStateFlow()

    private val _selectedEditId = MutableStateFlow<String?>(null)
    val selectedEditId: StateFlow<String?> = _selectedEditId.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = documentRepository.allDocuments
    val groups: StateFlow<List<String>> = documentRepository.distinctGroups

    private val _documentInfo = MutableStateFlow<PdfDocumentInfo?>(null)
    val documentInfo: StateFlow<PdfDocumentInfo?> = _documentInfo.asStateFlow()

    private val _activePdfFile = MutableStateFlow<File?>(null)
    val activePdfFile: StateFlow<File?> = _activePdfFile.asStateFlow()

    private val _pages = MutableStateFlow<List<PageItem>>(emptyList())
    val pages: StateFlow<List<PageItem>> = _pages.asStateFlow()

    private val _pageBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = _pageBitmaps.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _workbenchMode = MutableStateFlow(WorkbenchMode.READ)
    val workbenchMode: StateFlow<WorkbenchMode> = _workbenchMode.asStateFlow()

    private val _layoutMode = MutableStateFlow(PageLayoutMode.CONTINUOUS)
    val layoutMode: StateFlow<PageLayoutMode> = _layoutMode.asStateFlow()

    private val _readingTheme = MutableStateFlow(ReadingTheme.DEFAULT)
    val readingTheme: StateFlow<ReadingTheme> = _readingTheme.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1.0f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    // Annotation tools
    private val _activeAnnotationType = MutableStateFlow(AnnotationType.INK_PEN)
    val activeAnnotationType: StateFlow<AnnotationType> = _activeAnnotationType.asStateFlow()

    private val _penColor = MutableStateFlow(0xFF8A011EL) // RabiWine
    val penColor: StateFlow<Long> = _penColor.asStateFlow()

    private val _strokeWidth = MutableStateFlow(4f)
    val strokeWidth: StateFlow<Float> = _strokeWidth.asStateFlow()

    private val _annotations = MutableStateFlow<Map<Int, List<AnnotationItem>>>(emptyMap())
    val annotations: StateFlow<Map<Int, List<AnnotationItem>>> = _annotations.asStateFlow()

    // Form fields
    private val _formFields = MutableStateFlow<List<FormFieldItem>>(emptyList())
    val formFields: StateFlow<List<FormFieldItem>> = _formFields.asStateFlow()

    // Comments
    private val _comments = MutableStateFlow<List<CommentItem>>(emptyList())
    val comments: StateFlow<List<CommentItem>> = _comments.asStateFlow()

    // Bookmarks
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchMatch>>(emptyList())
    val searchResults: StateFlow<List<SearchMatch>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(0)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Saved signature stamps
    private val _savedSignatures = MutableStateFlow<List<String>>(listOf("A. Dumas", "FranPdf Verified"))
    val savedSignatures: StateFlow<List<String>> = _savedSignatures.asStateFlow()

    init {
        loadShowcaseDocument()
    }

    fun loadShowcaseDocument() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (doc, pageList) = repository.loadShowcase()
                _documentInfo.value = doc
                val activeF = repository.getActivePdfFile()
                _activePdfFile.value = activeF
                if (activeF != null) {
                    documentRepository.ensureShowcaseDocument(activeF)
                }
                _pages.value = pageList
                _bookmarks.value = repository.sampleBookmarks
                _currentPageIndex.value = 0
                initializeSampleData()
                _edits.value = emptyMap()
                _selectedEditId.value = null
                preloadBitmaps(pageList)
                _statusMessage.value = "Loaded specimen: ${doc.name}"
            } catch (e: Throwable) {
                e.printStackTrace()
                _statusMessage.value = "Failed to load showcase: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadDocumentFromUri(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (doc, pageList) = repository.loadFromUri(uri)
                _documentInfo.value = doc
                _activePdfFile.value = repository.getActivePdfFile()
                _pages.value = pageList
                _bookmarks.value = listOf(BookmarkItem("bm-root", doc.name, 0))
                _currentPageIndex.value = 0
                _annotations.value = emptyMap()
                _comments.value = emptyList()
                _formFields.value = emptyList()
                _edits.value = emptyMap()
                _selectedEditId.value = null
                preloadBitmaps(pageList)
                _statusMessage.value = "Opened ${doc.name}"
            } catch (e: Throwable) {
                e.printStackTrace()
                _statusMessage.value = "Error opening PDF: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun preloadBitmaps(pageList: List<PageItem>) {
        viewModelScope.launch {
            val map = mutableMapOf<Int, Bitmap>()
            for (p in pageList) {
                val bmp = repository.renderPageBitmap(p.originalPageIndex, p.rotationDegrees, scale = 0.6f)
                if (bmp != null) {
                    map[p.pageIndex] = bmp
                }
            }
            _pageBitmaps.value = map
        }
    }

    private fun initializeSampleData() {
        // Initial comments
        _comments.value = listOf(
            CommentItem(
                id = "c-1",
                pageIndex = 0,
                author = "Lead Reviewer",
                text = "Specimen title and ISO compliance badges look very sharp.",
                xRatio = 0.7f,
                yRatio = 0.25f,
                replies = listOf(
                    CommentReply("cr-1", "Typography Engine", "Kerning matches the standard metrics.")
                )
            ),
            CommentItem(
                id = "c-2",
                pageIndex = 1,
                author = "Localization QA",
                text = "BiDi Arabic and Hebrew scripts properly shaped without overlaps.",
                xRatio = 0.5f,
                yRatio = 0.45f
            ),
            CommentItem(
                id = "c-3",
                pageIndex = 3,
                author = "Security Officer",
                text = "Signature block certified with SHA-256 certificate.",
                xRatio = 0.5f,
                yRatio = 0.75f,
                isResolved = true
            )
        )

        // Initial form fields for page 3
        _formFields.value = listOf(
            FormFieldItem("f-1", 3, "Full Name", FieldType.TEXT, "Alexandre Dumas", false, 0.15f, 0.25f, 0.7f, 0.05f),
            FormFieldItem("f-2", 3, "Email Address", FieldType.TEXT, "alexandre.dumas@rabicraft.org", false, 0.15f, 0.35f, 0.7f, 0.05f),
            FormFieldItem("f-3", 3, "Confirmation", FieldType.CHECKBOX, "", true, 0.15f, 0.45f, 0.05f, 0.03f),
            FormFieldItem("f-4", 3, "Signature", FieldType.SIGNATURE, "A. Dumas", false, 0.15f, 0.55f, 0.7f, 0.12f)
        )
    }

    fun setWorkbenchMode(mode: WorkbenchMode) {
        _workbenchMode.value = mode
    }

    fun setLayoutMode(mode: PageLayoutMode) {
        _layoutMode.value = mode
    }

    fun setReadingTheme(theme: ReadingTheme) {
        _readingTheme.value = theme
    }

    fun setZoom(zoom: Float) {
        _zoomLevel.value = zoom.coerceIn(0.5f, 3.0f)
    }

    fun zoomIn() {
        setZoom(_zoomLevel.value + 0.25f)
    }

    fun zoomOut() {
        setZoom(_zoomLevel.value - 0.25f)
    }

    fun resetZoom() {
        _zoomLevel.value = 1.0f
    }

    fun setCurrentPage(index: Int) {
        if (index in _pages.value.indices) {
            _currentPageIndex.value = index
        }
    }

    fun rotatePage(pageIndex: Int) {
        val currentList = _pages.value.toMutableList()
        val page = currentList.getOrNull(pageIndex) ?: return
        val newRotation = (page.rotationDegrees + 90) % 360
        currentList[pageIndex] = page.copy(rotationDegrees = newRotation)
        _pages.value = currentList

        viewModelScope.launch {
            val bmp = repository.renderPageBitmap(page.originalPageIndex, newRotation, scale = 0.6f)
            if (bmp != null) {
                val currentMap = _pageBitmaps.value.toMutableMap()
                currentMap[pageIndex] = bmp
                _pageBitmaps.value = currentMap
            }
        }
    }

    fun reorderPage(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _pages.value.indices || toIndex !in _pages.value.indices || fromIndex == toIndex) return
        val currentList = _pages.value.toMutableList()
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)

        val reindexed = currentList.mapIndexed { idx, p -> p.copy(pageIndex = idx) }
        _pages.value = reindexed

        // Refresh bitmap mapping
        viewModelScope.launch {
            val newMap = mutableMapOf<Int, Bitmap>()
            for (p in reindexed) {
                val bmp = repository.renderPageBitmap(p.originalPageIndex, p.rotationDegrees, scale = 0.6f)
                if (bmp != null) newMap[p.pageIndex] = bmp
            }
            _pageBitmaps.value = newMap
        }
    }

    fun deletePage(pageIndex: Int) {
        if (_pages.value.size <= 1) {
            _statusMessage.value = "Cannot delete the last remaining page."
            return
        }
        val currentList = _pages.value.toMutableList()
        currentList.removeAt(pageIndex)
        val reindexed = currentList.mapIndexed { idx, p -> p.copy(pageIndex = idx) }
        _pages.value = reindexed
        if (_currentPageIndex.value >= reindexed.size) {
            _currentPageIndex.value = reindexed.size - 1
        }
        preloadBitmaps(reindexed)
        _statusMessage.value = "Page ${pageIndex + 1} deleted."
    }

    fun duplicatePage(pageIndex: Int) {
        val currentList = _pages.value.toMutableList()
        val target = currentList.getOrNull(pageIndex) ?: return
        val copy = target.copy(label = "${target.label} (Copy)")
        currentList.add(pageIndex + 1, copy)
        val reindexed = currentList.mapIndexed { idx, p -> p.copy(pageIndex = idx) }
        _pages.value = reindexed
        preloadBitmaps(reindexed)
        _statusMessage.value = "Page ${pageIndex + 1} duplicated."
    }

    fun setActiveAnnotationType(type: AnnotationType) {
        _activeAnnotationType.value = type
    }

    fun setPenColor(color: Long) {
        _penColor.value = color
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

    fun addAnnotation(pageIndex: Int, annotation: AnnotationItem) {
        val currentMap = _annotations.value.toMutableMap()
        val pageList = currentMap[pageIndex]?.toMutableList() ?: mutableListOf()
        pageList.add(annotation)
        currentMap[pageIndex] = pageList
        _annotations.value = currentMap
    }

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        languageStore.set(language)
    }

    fun selectEdit(editId: String?) {
        _selectedEditId.value = editId
    }

    fun addTextEdit(pageIndex: Int, text: String, colorArgb: Long) {
        if (text.isBlank()) return
        val lines = text.trim().lines().size.coerceAtLeast(1)
        val edit = PageEdit(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            kind = EditKind.TEXT,
            xRatio = 0.12f,
            yRatio = 0.18f,
            widthRatio = 0.62f,
            heightRatio = (0.024f * 1.45f * lines).coerceAtLeast(0.035f),
            text = text.trim(),
            colorArgb = colorArgb
        )
        appendEdit(edit)
        _selectedEditId.value = edit.id
    }

    fun addShapeEdit(pageIndex: Int) {
        val edit = PageEdit(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            kind = EditKind.RECTANGLE,
            xRatio = 0.2f,
            yRatio = 0.3f,
            widthRatio = 0.4f,
            heightRatio = 0.18f,
            colorArgb = _penColor.value
        )
        appendEdit(edit)
        _selectedEditId.value = edit.id
    }

    fun addWhiteoutEdit(pageIndex: Int) {
        val edit = PageEdit(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            kind = EditKind.WHITEOUT,
            xRatio = 0.15f,
            yRatio = 0.22f,
            widthRatio = 0.5f,
            heightRatio = 0.045f
        )
        appendEdit(edit)
        _selectedEditId.value = edit.id
    }

    fun addImageEdit(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                if (bitmap == null) {
                    _statusMessage.value = "Unsupported image"
                    return@launch
                }
                val pageIndex = _currentPageIndex.value
                val page = _pages.value.getOrNull(pageIndex)
                val pageAspect = if (page != null && page.height > 0) {
                    page.width.toFloat() / page.height.toFloat()
                } else {
                    595f / 842f
                }
                val widthRatio = 0.55f
                val heightRatio = (widthRatio * bitmap.height.toFloat() / bitmap.width.toFloat() * pageAspect)
                    .coerceIn(0.05f, 0.75f)
                val edit = PageEdit(
                    id = UUID.randomUUID().toString(),
                    pageIndex = pageIndex,
                    kind = EditKind.IMAGE,
                    xRatio = 0.2f,
                    yRatio = 0.3f,
                    widthRatio = widthRatio,
                    heightRatio = heightRatio,
                    image = bitmap
                )
                appendEdit(edit)
                _selectedEditId.value = edit.id
            } catch (e: Exception) {
                _statusMessage.value = "Image import failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun appendEdit(edit: PageEdit) {
        val currentMap = _edits.value
        _edits.value = currentMap + (edit.pageIndex to (currentMap[edit.pageIndex].orEmpty() + edit))
    }

    fun moveEdit(editId: String, dx: Float, dy: Float) {
        val currentMap = _edits.value
        val pageIndex = currentMap.entries.firstOrNull { entry -> entry.value.any { it.id == editId } }?.key ?: return
        val updated = currentMap[pageIndex].orEmpty().map { edit ->
            if (edit.id != editId) {
                edit
            } else {
                edit.copy(
                    xRatio = (edit.xRatio + dx).coerceIn(0f, (1f - edit.widthRatio).coerceAtLeast(0f)),
                    yRatio = (edit.yRatio + dy).coerceIn(0f, (1f - edit.heightRatio).coerceAtLeast(0f))
                )
            }
        }
        _edits.value = currentMap + (pageIndex to updated)
    }

    fun removeSelectedEdit() {
        val editId = _selectedEditId.value ?: return
        val currentMap = _edits.value
        val pageIndex = currentMap.entries.firstOrNull { entry -> entry.value.any { it.id == editId } }?.key ?: return
        _edits.value = currentMap + (pageIndex to currentMap[pageIndex].orEmpty().filterNot { it.id == editId })
        _selectedEditId.value = null
    }

    fun clearPageEdits(pageIndex: Int) {
        if (_edits.value.containsKey(pageIndex)) {
            _edits.value = _edits.value - pageIndex
        }
        _selectedEditId.value = null
    }

    fun clearAnnotations(pageIndex: Int) {
        val currentMap = _annotations.value.toMutableMap()
        currentMap.remove(pageIndex)
        _annotations.value = currentMap
    }

    fun addComment(pageIndex: Int, text: String, xRatio: Float = 0.5f, yRatio: Float = 0.5f) {
        if (text.isBlank()) return
        val item = CommentItem(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            author = "Reviewer",
            text = text.trim(),
            xRatio = xRatio,
            yRatio = yRatio
        )
        _comments.value = _comments.value + item
        _statusMessage.value = "Comment added."
    }

    fun addCommentReply(commentId: String, replyText: String) {
        if (replyText.isBlank()) return
        val currentList = _comments.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == commentId }
        if (idx != -1) {
            val c = currentList[idx]
            val reply = CommentReply(UUID.randomUUID().toString(), "Reviewer", replyText.trim())
            currentList[idx] = c.copy(replies = c.replies + reply)
            _comments.value = currentList
        }
    }

    fun toggleCommentResolved(commentId: String) {
        val currentList = _comments.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == commentId }
        if (idx != -1) {
            val c = currentList[idx]
            currentList[idx] = c.copy(isResolved = !c.isResolved)
            _comments.value = currentList
        }
    }

    fun updateFormFieldValue(fieldId: String, value: String) {
        val currentList = _formFields.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == fieldId }
        if (idx != -1) {
            currentList[idx] = currentList[idx].copy(value = value)
            _formFields.value = currentList
        }
    }

    fun toggleFormFieldChecked(fieldId: String) {
        val currentList = _formFields.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == fieldId }
        if (idx != -1) {
            val f = currentList[idx]
            currentList[idx] = f.copy(isChecked = !f.isChecked)
            _formFields.value = currentList
        }
    }

    fun saveSignatureStamp(signatureText: String) {
        if (signatureText.isNotBlank() && !_savedSignatures.value.contains(signatureText)) {
            _savedSignatures.value = _savedSignatures.value + signatureText
        }
    }

    // Search
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        val matches = mutableListOf<SearchMatch>()
        for (page in _pages.value) {
            val text = page.textContent
            if (text.contains(query, ignoreCase = true)) {
                matches.add(
                    SearchMatch(
                        pageIndex = page.pageIndex,
                        snippet = text.take(60) + "…",
                        matchIndexInPage = 0
                    )
                )
            }
        }
        _searchResults.value = matches
        _currentMatchIndex.value = 0
        if (matches.isNotEmpty()) {
            _currentPageIndex.value = matches[0].pageIndex
        }
    }

    fun nextSearchMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        val nextIdx = (_currentMatchIndex.value + 1) % matches.size
        _currentMatchIndex.value = nextIdx
        _currentPageIndex.value = matches[nextIdx].pageIndex
    }

    fun prevSearchMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        val prevIdx = if (_currentMatchIndex.value - 1 < 0) matches.size - 1 else _currentMatchIndex.value - 1
        _currentMatchIndex.value = prevIdx
        _currentPageIndex.value = matches[prevIdx].pageIndex
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun exportDocument(onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val exportFile = File(getApplication<Application>().cacheDir, "FranPdf_Export_${System.currentTimeMillis()}.pdf")
                val success = repository.exportCurrentDocument(
                    pages = _pages.value,
                    annotations = _annotations.value,
                    edits = _edits.value,
                    formFields = _formFields.value,
                    targetFile = exportFile
                )
                if (success) {
                    _statusMessage.value = "Exported to ${exportFile.name}"
                    onComplete(exportFile)
                } else {
                    _statusMessage.value = "Failed to export document."
                    onComplete(null)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Export error: ${e.message}"
                onComplete(null)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun combineWithShowcase(onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val showcaseFile = org.rabicraft.app.data.ShowcaseGenerator.generateShowcasePdf(getApplication())
                val combinedFile = File(getApplication<Application>().cacheDir, "Combined_${System.currentTimeMillis()}.pdf")
                val success = repository.combineDocuments(_pages.value, showcaseFile, combinedFile)
                if (success) {
                    _statusMessage.value = "Combined with specimen document!"
                    loadDocumentFromUri(Uri.fromFile(combinedFile))
                    onComplete(combinedFile)
                } else {
                    _statusMessage.value = "Failed to combine."
                    onComplete(null)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Combine error: ${e.message}"
                onComplete(null)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Runs a document tool on the open document and hands the produced files back to the caller.
     * Tools that edit the document write one PDF; split and image export write several files.
     */
    fun applyTool(config: ToolConfig, onComplete: (List<File>) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val pages = _pages.value
            val title = _documentInfo.value?.name?.removeSuffix(".pdf") ?: "Document"
            val stamp = System.currentTimeMillis()
            val cacheDir = getApplication<Application>().cacheDir
            val selectionSpec = when (config) {
                is ToolConfig.PageNumbers -> config.settings.selection
                is ToolConfig.Watermark -> config.settings.selection
                is ToolConfig.HeaderFooter -> config.settings.selection
                is ToolConfig.Compress -> config.settings.selection
                is ToolConfig.Colour -> config.selection
                is ToolConfig.Crop -> config.selection
                is ToolConfig.Ocr -> config.selection
                else -> ""
            }
            val selection = ToolPlanner.parsePageSelection(selectionSpec, pages.size)
            if (selection != null && selection.isEmpty()) {
                _statusMessage.value = "No page matches \"$selectionSpec\"."
                onComplete(emptyList())
                return@launch
            }
            try {
                when (config) {
                    is ToolConfig.PageNumbers -> exportPlan(
                        ToolPlanner.pageNumberPlan(pages, config.settings, title),
                        File(cacheDir, "FranPdf_Numbers_$stamp.pdf"),
                        onComplete
                    )
                    is ToolConfig.Watermark -> exportPlan(
                        ToolPlanner.watermarkPlan(pages, config.settings),
                        File(cacheDir, "FranPdf_Watermark_$stamp.pdf"),
                        onComplete
                    )
                    is ToolConfig.HeaderFooter -> exportPlan(
                        ToolPlanner.headerFooterPlan(pages, config.settings, title),
                        File(cacheDir, "FranPdf_Header_$stamp.pdf"),
                        onComplete
                    )
                    is ToolConfig.NUp -> exportPlan(
                        ToolPlanner.nUpPlan(pages, config.settings),
                        File(cacheDir, "FranPdf_NUp_$stamp.pdf"),
                        onComplete
                    )
                    ToolConfig.Booklet -> exportPlan(
                        ToolPlanner.bookletPlan(pages),
                        File(cacheDir, "FranPdf_Booklet_$stamp.pdf"),
                        onComplete
                    )
                    is ToolConfig.Crop -> exportPlan(
                        ToolPlanner.cropPlan(pages, config.settings, config.selection),
                        File(cacheDir, "FranPdf_Cropped_$stamp.pdf"),
                        onComplete
                    )
                    is ToolConfig.Colour -> exportPlan(
                        ToolPlanner.colourPlan(pages, config.effect, config.selection),
                        File(cacheDir, "FranPdf_Colour_$stamp.pdf"),
                        onComplete
                    )
                    is ToolConfig.Compress -> exportPlan(
                        ToolPlanner.sequentialPlan(
                            pages = pages,
                            renderDpi = config.settings.maxDpi,
                            jpegQuality = config.settings.jpegQuality,
                            selection = selection
                        ),
                        File(cacheDir, "FranPdf_Compressed_$stamp.pdf"),
                        onComplete
                    )
                    is ToolConfig.BlankPages -> {
                        val ratios = repository.measureInkRatios(pages)
                        val kept = pages.filterIndexed { index, _ ->
                            !ToolPlanner.isBlank(ratios.getOrElse(index) { 1f }, config.settings)
                        }
                        val removed = pages.size - kept.size
                        if (kept.isEmpty()) {
                            _statusMessage.value = "Every page is blank; nothing was written."
                            onComplete(emptyList())
                        } else {
                            val ok = repository.exportWithPlan(
                                pages = pages,
                                plan = ToolPlanner.sequentialPlan(kept),
                                targetFile = File(cacheDir, "FranPdf_NoBlanks_$stamp.pdf")
                            )
                            _statusMessage.value = if (ok) {
                                "Removed $removed blank page(s)."
                            } else {
                                "Failed to remove blank pages."
                            }
                            onComplete(
                                if (ok) listOf(File(cacheDir, "FranPdf_NoBlanks_$stamp.pdf")) else emptyList()
                            )
                        }
                    }
                    is ToolConfig.Ocr -> {
                        val results = mutableMapOf<Int, OcrPageResult>()
                        val scope = if (selection == null) {
                            pages
                        } else {
                            pages.filterIndexed { index, _ -> index in selection }
                        }
                        for (page in scope) {
                            val bitmap = repository.renderPageBitmap(
                                page.originalPageIndex,
                                page.rotationDegrees,
                                1.6f
                            ) ?: continue
                            val result = try {
                                ocrEngine.recognize(page.pageIndex, bitmap)
                            } finally {
                                bitmap.recycle()
                            }
                            if (result != null && result.text.isNotBlank()) {
                                results[page.pageIndex] = result
                            }
                        }
                        if (results.isEmpty()) {
                            _statusMessage.value = "OCR found no text on these pages."
                            onComplete(emptyList())
                        } else {
                            // Index the recognised text so the in-app search reaches scanned pages.
                            _pages.value = _pages.value.map { page ->
                                results[page.pageIndex]?.let { page.copy(textContent = it.text) } ?: page
                            }
                            val target = File(cacheDir, "FranPdf_OCR_$stamp.pdf")
                            val ok = repository.exportWithOcrLayer(pages, results, target)
                            _statusMessage.value = if (ok) {
                                "OCR added a text layer to ${results.size} page(s)."
                            } else {
                                "Failed to write the searchable PDF."
                            }
                            onComplete(if (ok) listOf(target) else emptyList())
                        }
                    }
                    is ToolConfig.Split -> {
                        val ranges = ToolPlanner.rangesFromSpec(config.settings.ranges, pages.size)
                        val chunks = ranges ?: ToolPlanner.chunks(pages.size, config.settings.pagesPerFile)
                        val files = repository.splitIntoFiles(
                            pages = pages,
                            chunks = chunks,
                            annotations = _annotations.value,
                            edits = _edits.value,
                            formFields = _formFields.value,
                            targetDirectory = cacheDir,
                            namePrefix = "FranPdf_Split_$stamp"
                        )
                        _statusMessage.value = if (files.isEmpty()) {
                            "Nothing to split."
                        } else {
                            "Split into ${files.size} document(s)."
                        }
                        onComplete(files)
                    }
                    is ToolConfig.Extract -> {
                        val ranges = ToolPlanner.rangesFromSpec(config.ranges, pages.size)
                        val indexes = ranges?.flatten()?.distinct()?.sorted()
                        if (indexes.isNullOrEmpty()) {
                            _statusMessage.value = "Enter the pages to keep, for example 1-3,5."
                            onComplete(emptyList())
                        } else {
                            val subset = indexes.mapNotNull { pages.getOrNull(it) }
                            val target = File(cacheDir, "FranPdf_Extract_$stamp.pdf")
                            val ok = repository.exportCurrentDocument(
                                subset,
                                _annotations.value,
                                _edits.value,
                                _formFields.value,
                                target
                            )
                            _statusMessage.value = if (ok) {
                                "Extracted ${subset.size} page(s)."
                            } else {
                                "Failed to extract pages."
                            }
                            onComplete(if (ok) listOf(target) else emptyList())
                        }
                    }
                    is ToolConfig.ToImages -> {
                        val files = repository.exportPageImages(pages, config.settings, cacheDir)
                        _statusMessage.value = if (files.isEmpty()) {
                            "No page image could be written."
                        } else {
                            "Exported ${files.size} page image(s)."
                        }
                        onComplete(files)
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Tool failed: ${e.message}"
                onComplete(emptyList())
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun exportPlan(
        plan: org.rabicraft.app.data.tools.ToolExportPlan,
        targetFile: File,
        onComplete: (List<File>) -> Unit
    ) {
        if (plan.sheets.isEmpty()) {
            _statusMessage.value = "Nothing to export."
            onComplete(emptyList())
            return
        }
        val ok = repository.exportWithPlan(
            pages = _pages.value,
            plan = plan,
            targetFile = targetFile,
            annotations = _annotations.value,
            edits = _edits.value,
            formFields = _formFields.value
        )
        _statusMessage.value = if (ok) {
            "Exported ${targetFile.name} (${plan.sheets.size} page(s))"
        } else {
            "Failed to write the treated PDF."
        }
        onComplete(if (ok) listOf(targetFile) else emptyList())
    }

    /** Merges the open document with PDFs picked by the user. */
    fun mergeDocuments(uris: List<Uri>, onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val copies = mutableListOf<File>()
                for ((index, uri) in uris.withIndex()) {
                    val file = File(cacheDir, "merge_source_${System.currentTimeMillis()}_$index.pdf")
                    val copied = withContext(Dispatchers.IO) {
                        try {
                            getApplication<Application>().contentResolver
                                .openInputStream(uri)?.use { input ->
                                    file.outputStream().use { output -> input.copyTo(output) }
                                }
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    }
                    if (copied) copies.add(file)
                }
                if (copies.isEmpty()) {
                    _statusMessage.value = "No PDF could be read to merge."
                    onComplete(null)
                    return@launch
                }
                val target = File(cacheDir, "FranPdf_Merged_${System.currentTimeMillis()}.pdf")
                val ok = repository.mergeFiles(_pages.value, copies, target)
                _statusMessage.value = if (ok) {
                    "Merged ${_pages.value.size} page(s) with ${copies.size} document(s)."
                } else {
                    "Failed to merge documents."
                }
                onComplete(if (ok) target else null)
            } catch (e: Exception) {
                _statusMessage.value = "Merge failed: ${e.message}"
                onComplete(null)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun importDocument(uri: Uri, name: String? = null, group: String = "General", tags: List<String> = emptyList()) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entity = documentRepository.importDocument(uri, name, group, tags)
                val targetFile = File(entity.filePath)
                val (doc, pageList) = repository.loadFromUri(Uri.fromFile(targetFile))
                _documentInfo.value = doc.copy(name = entity.name)
                _activePdfFile.value = targetFile
                _pages.value = pageList
                _bookmarks.value = listOf(BookmarkItem("bm-root", entity.name, 0))
                _currentPageIndex.value = 0
                _workbenchMode.value = WorkbenchMode.READ
                _edits.value = emptyMap()
                _selectedEditId.value = null
                preloadBitmaps(pageList)
                _statusMessage.value = "Imported '${entity.name}' to '${entity.groupName}'"
            } catch (e: Exception) {
                _statusMessage.value = "Import failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openDocumentFromLibrary(entity: DocumentEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val file = File(entity.filePath)
                if (file.exists()) {
                    documentRepository.updateLastOpened(entity.id)
                    val (doc, pageList) = repository.loadFromUri(Uri.fromFile(file))
                    _documentInfo.value = doc.copy(name = entity.name)
                    _activePdfFile.value = file
                    _pages.value = pageList
                    _bookmarks.value = if (entity.isSample) repository.sampleBookmarks else listOf(BookmarkItem("bm-root", entity.name, 0))
                    _currentPageIndex.value = 0
                    _workbenchMode.value = WorkbenchMode.READ
                    _edits.value = emptyMap()
                _selectedEditId.value = null
                preloadBitmaps(pageList)
                    _statusMessage.value = "Opened '${entity.name}'"
                } else {
                    _statusMessage.value = "File not found: ${entity.name}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error opening document: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameDocument(id: String, newName: String) {
        viewModelScope.launch {
            try {
                documentRepository.renameDocument(id, newName)
                if (_documentInfo.value?.id == id) {
                    _documentInfo.value = _documentInfo.value?.copy(name = newName)
                }
                _statusMessage.value = "Renamed to '$newName'"
            } catch (e: Exception) {
                _statusMessage.value = "Rename failed: ${e.message}"
            }
        }
    }

    fun updateDocumentGroup(id: String, newGroup: String) {
        viewModelScope.launch {
            try {
                documentRepository.updateGroup(id, newGroup)
                _statusMessage.value = "Moved to group '$newGroup'"
            } catch (e: Exception) {
                _statusMessage.value = "Group update failed: ${e.message}"
            }
        }
    }

    fun updateDocumentTags(id: String, tags: List<String>) {
        viewModelScope.launch {
            try {
                documentRepository.updateTags(id, tags)
                _statusMessage.value = "Tags updated"
            } catch (e: Exception) {
                _statusMessage.value = "Tag update failed: ${e.message}"
            }
        }
    }

    fun toggleDocumentStarred(id: String, currentStarred: Boolean) {
        viewModelScope.launch {
            try {
                documentRepository.toggleStarred(id, currentStarred)
            } catch (e: Exception) {
                _statusMessage.value = "Star update failed: ${e.message}"
            }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            try {
                documentRepository.deleteDocument(id)
                _statusMessage.value = "Document deleted"
            } catch (e: Exception) {
                _statusMessage.value = "Delete failed: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ocrEngine.close()
        repository.closeCurrentRenderer()
    }
}
