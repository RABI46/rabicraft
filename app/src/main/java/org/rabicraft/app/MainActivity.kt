package org.rabicraft.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rabicraft.app.model.ComparisonLabels
import org.rabicraft.app.model.DetectedTextBlock
import org.rabicraft.app.model.PageLayoutMode
import org.rabicraft.app.model.ReadingTheme
import org.rabicraft.app.model.ScanCorners
import org.rabicraft.app.model.ScannedPage
import org.rabicraft.app.model.WorkbenchMode
import org.rabicraft.app.data.AppLanguage
import org.rabicraft.app.ui.components.AddTextDialog
import org.rabicraft.app.ui.components.CombineSplitDialog
import org.rabicraft.app.ui.components.CommentsSheet
import org.rabicraft.app.ui.components.EditAction
import org.rabicraft.app.ui.components.InspectorDialog
import org.rabicraft.app.ui.components.LanguageDialog
import org.rabicraft.app.ui.components.SearchOverlay
import org.rabicraft.app.ui.components.SignaturePadDialog
import org.rabicraft.app.ui.components.TextEditDialog
import org.rabicraft.app.ui.components.ToolRail
import org.rabicraft.app.ui.components.TopBar
import org.rabicraft.app.ui.screens.CreatePdfScreen
import org.rabicraft.app.ui.screens.DocumentLibraryScreen
import org.rabicraft.app.ui.screens.OrganizeScreen
import org.rabicraft.app.ui.screens.ProtectScreen
import org.rabicraft.app.ui.screens.ScanCropScreen
import org.rabicraft.app.ui.screens.ToolsScreen
import org.rabicraft.app.ui.i18n.LocalStrings
import org.rabicraft.app.ui.i18n.stringsFor
import org.rabicraft.app.ui.screens.ViewerScreen
import org.rabicraft.app.ui.theme.RabiCraftTheme
import org.rabicraft.app.viewmodel.PdfWorkbenchViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: PdfWorkbenchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if opened via intent (e.g. from File Manager or email)
        intent?.data?.let { uri ->
            viewModel.loadDocumentFromUri(uri)
        }

        setContent {
            val language by viewModel.language.collectAsStateWithLifecycle()
            RabiCraftTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
                    MainAppScreen(
                        viewModel = viewModel,
                        onOpenDocumentPicker = { launcher ->
                            launcher.launch(arrayOf("application/pdf"))
                        },
                        onShareFile = { file ->
                            sharePdfFile(file)
                        },
                        onShareFiles = { files ->
                            sharePdfFiles(files)
                        }
                    )
                }
            }
        }
    }

    private fun sharePdfFile(file: File) {
        sharePdfFiles(listOf(file))
    }

    /** Shares one file, or every produced file when a tool writes several (split, page images). */
    private fun sharePdfFiles(files: List<File>) {
        if (files.isEmpty()) return
        try {
            val uris = ArrayList<Uri>()
            for (file in files) {
                uris.add(
                    FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                )
            }
            val shareIntent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeTypeFor(files.first())
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeTypeFor(files.first())
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(Intent.createChooser(shareIntent, "Share with FranPdf"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/pdf"
    }
}

@Composable
fun MainAppScreen(
    viewModel: PdfWorkbenchViewModel,
    onOpenDocumentPicker: (androidx.activity.result.ActivityResultLauncher<Array<String>>) -> Unit,
    onShareFile: (File) -> Unit,
    onShareFiles: (List<File>) -> Unit
) {
    val documentInfo by viewModel.documentInfo.collectAsStateWithLifecycle()
    val activePdfFile by viewModel.activePdfFile.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()
    val currentPageIndex by viewModel.currentPageIndex.collectAsStateWithLifecycle()
    val workbenchMode by viewModel.workbenchMode.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    val readingTheme by viewModel.readingTheme.collectAsStateWithLifecycle()
    val zoomLevel by viewModel.zoomLevel.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val formFields by viewModel.formFields.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val activeAnnotType by viewModel.activeAnnotationType.collectAsStateWithLifecycle()
    val penColor by viewModel.penColor.collectAsStateWithLifecycle()
    val strokeWidth by viewModel.strokeWidth.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val edits by viewModel.edits.collectAsStateWithLifecycle()
    val selectedEditId by viewModel.selectedEditId.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    val comparisonLabels = remember(strings) {
        ComparisonLabels(
            identical = strings.compareIdentical,
            different = strings.compareDifferent,
            differentFormat = strings.compareDifferentFormat,
            missing = strings.compareMissing
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog & overlay states
    var showInspector by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCombineSplit by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showCreatePdf by remember { mutableStateOf(false) }
    var showTextEdit by remember { mutableStateOf(false) }
    var detectedBlocks by remember { mutableStateOf<List<DetectedTextBlock>>(emptyList()) }
    var showScanPrompt by remember { mutableStateOf(false) }
    var scanPages by remember { mutableStateOf<List<ScannedPage>>(emptyList()) }
    var pendingScanFile by remember { mutableStateOf<File?>(null) }
    var enhanceScan by remember { mutableStateOf(true) }
    var cropFile by remember { mutableStateOf<File?>(null) }
    var cropPreview by remember { mutableStateOf<Bitmap?>(null) }
    var cropDetected by remember { mutableStateOf<ScanCorners?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addImageEdit(it) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importDocument(it) }
    }

    val mergePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.mergeDocuments(uris) { file ->
                if (file != null) onShareFile(file)
            }
        }
    }

    val comparePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.compareDocuments(uri, comparisonLabels) { file ->
                if (file != null) onShareFile(file)
            }
        }
    }

    val imageSetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.createPdfFromImages(uris) { file ->
                if (file != null) onShareFile(file)
            }
        }
    }

    val context = LocalContext.current
    val scanCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured: Boolean ->
        val file = pendingScanFile
        if (captured && file != null && file.exists() && file.length() > 0) {
            viewModel.loadScanPreview(file) { bitmap, corners ->
                if (bitmap != null) {
                    cropPreview = bitmap
                    cropDetected = corners
                    cropFile = file
                } else {
                    scanPages = scanPages + ScannedPage(file, null)
                    showScanPrompt = true
                }
            }
        }
    }

    val startScan: () -> Unit = {
        try {
            val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            pendingScanFile = file
            scanCaptureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopBar(
                documentName = documentInfo?.name ?: "FranPdf",
                currentPage = currentPageIndex,
                totalPages = pages.size,
                zoomLevel = zoomLevel,
                layoutMode = layoutMode,
                readingTheme = readingTheme,
                onZoomIn = { viewModel.zoomIn() },
                onZoomOut = { viewModel.zoomOut() },
                onToggleLayoutMode = {
                    val next = when (layoutMode) {
                        PageLayoutMode.CONTINUOUS -> PageLayoutMode.SINGLE_PAGE
                        PageLayoutMode.SINGLE_PAGE -> PageLayoutMode.TWO_UP
                        PageLayoutMode.TWO_UP -> PageLayoutMode.CONTINUOUS
                    }
                    viewModel.setLayoutMode(next)
                },
                onCycleReadingTheme = {
                    val next = when (readingTheme) {
                        ReadingTheme.DEFAULT -> ReadingTheme.DARK
                        ReadingTheme.DARK -> ReadingTheme.SEPIA
                        ReadingTheme.SEPIA -> ReadingTheme.INVERTED
                        ReadingTheme.INVERTED -> ReadingTheme.DEFAULT
                    }
                    viewModel.setReadingTheme(next)
                },
                onOpenSearch = { showSearchOverlay = !showSearchOverlay },
                onOpenInspector = { showInspector = true },
                onOpenDocument = { onOpenDocumentPicker(filePickerLauncher) },
                onOpenLanguage = { showLanguageDialog = true },
                onOpenTools = { showTools = true },
                onShare = {
                    viewModel.exportDocument { file ->
                        if (file != null) onShareFile(file)
                    }
                }
            )
        },
        bottomBar = {
            ToolRail(
                activeMode = workbenchMode,
                onSelectMode = { viewModel.setWorkbenchMode(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (workbenchMode) {
                WorkbenchMode.LIBRARY -> {
                    DocumentLibraryScreen(
                        documents = documents,
                        groups = groups,
                        onOpenDocument = { doc -> viewModel.openDocumentFromLibrary(doc) },
                        onImportDocument = { onOpenDocumentPicker(filePickerLauncher) },
                        onRenameDocument = { id, newName -> viewModel.renameDocument(id, newName) },
                        onUpdateGroup = { id, newGroup -> viewModel.updateDocumentGroup(id, newGroup) },
                        onUpdateTags = { id, tags -> viewModel.updateDocumentTags(id, tags) },
                        onToggleStarred = { id, currentStarred -> viewModel.toggleDocumentStarred(id, currentStarred) },
                        onDeleteDocument = { id -> viewModel.deleteDocument(id) }
                    )
                }

                WorkbenchMode.READ, WorkbenchMode.ANNOTATE, WorkbenchMode.EDIT, WorkbenchMode.FILL_SIGN -> {
                    ViewerScreen(
                        pdfFile = activePdfFile,
                        pages = pages,
                        currentPageIndex = currentPageIndex,
                        pageBitmaps = pageBitmaps,
                        workbenchMode = workbenchMode,
                        layoutMode = layoutMode,
                        readingTheme = readingTheme,
                        zoomLevel = zoomLevel,
                        annotations = annotations,
                        formFields = formFields,
                        comments = comments,
                        activeAnnotType = activeAnnotType,
                        penColor = penColor,
                        strokeWidth = strokeWidth,
                        edits = edits,
                        selectedEditId = selectedEditId,
                        onSelectEdit = { viewModel.selectEdit(it) },
                        onMoveEdit = { id, dx, dy -> viewModel.moveEdit(id, dx, dy) },
                        onEditAction = { action ->
                            when (action) {
                                EditAction.ADD_TEXT -> showTextDialog = true
                                EditAction.EDIT_TEXT -> viewModel.detectTextBlocks { blocks ->
                                    detectedBlocks = blocks
                                    showTextEdit = true
                                }
                                EditAction.ADD_IMAGE -> imagePickerLauncher.launch("image/*")
                                EditAction.ADD_SHAPE -> viewModel.addShapeEdit(currentPageIndex)
                                EditAction.WHITEOUT -> viewModel.addWhiteoutEdit(currentPageIndex)
                                EditAction.DELETE_SELECTED -> viewModel.removeSelectedEdit()
                                EditAction.CLEAR_PAGE -> viewModel.clearPageEdits(currentPageIndex)
                            }
                        },
                        onSelectPage = { viewModel.setCurrentPage(it) },
                        onAddAnnotation = { pageIdx, annot -> viewModel.addAnnotation(pageIdx, annot) },
                        onClearAnnotations = { viewModel.clearAnnotations(it) },
                        onSelectAnnotType = { viewModel.setActiveAnnotationType(it) },
                        onSelectPenColor = { viewModel.setPenColor(it) },
                        onOpenComments = { showCommentsSheet = true },
                        onOpenSignatureDialog = { showSignaturePad = true },
                        onUpdateFormField = { id, valStr -> viewModel.updateFormFieldValue(id, valStr) },
                        onToggleFormFieldChecked = { viewModel.toggleFormFieldChecked(it) }
                    )
                }

                WorkbenchMode.ORGANIZE -> {
                    OrganizeScreen(
                        pages = pages,
                        pageBitmaps = pageBitmaps,
                        onRotatePage = { viewModel.rotatePage(it) },
                        onMovePage = { from, to -> viewModel.reorderPage(from, to) },
                        onDeletePage = { viewModel.deletePage(it) },
                        onDuplicatePage = { viewModel.duplicatePage(it) },
                        onOpenCombine = { showCombineSplit = true },
                        onExportPdf = {
                            viewModel.exportDocument { file ->
                                if (file != null) onShareFile(file)
                            }
                        }
                    )
                }

                WorkbenchMode.PROTECT -> {
                    ProtectScreen(
                        docInfo = documentInfo,
                        onApplySecurity = { isEnc, _ ->
                            // Update document info
                        }
                    )
                }
            }

            if (showTools) {
                ToolsScreen(
                    totalPages = pages.size,
                    isBusy = isLoading,
                    onClose = { showTools = false },
                    onApply = { config ->
                        viewModel.applyTool(config) { files ->
                            if (files.isNotEmpty()) onShareFiles(files)
                        }
                    },
                    onPickMergeFiles = { mergePickerLauncher.launch(arrayOf("application/pdf")) },
                    onPickCompareFile = { comparePickerLauncher.launch("application/pdf") },
                    onPickImageFiles = { imageSetPickerLauncher.launch("image/*") },
                    onScanDocument = { startScan() },
                    onCreateDocument = { showCreatePdf = true },
                    onDetectFields = { viewModel.detectFormFields() }
                )
            }

            val previewBitmap = cropPreview
            val pendingCropFile = cropFile
            if (previewBitmap != null && pendingCropFile != null) {
                ScanCropScreen(
                    preview = previewBitmap,
                    detected = cropDetected,
                    onCancel = {
                        cropFile = null
                        cropPreview = null
                        cropDetected = null
                    },
                    onConfirm = { corners ->
                        scanPages = scanPages + ScannedPage(pendingCropFile, corners)
                        cropFile = null
                        cropPreview = null
                        cropDetected = null
                        showScanPrompt = true
                    }
                )
            }

            if (showCreatePdf) {
                CreatePdfScreen(
                    onClose = { showCreatePdf = false },
                    onCreate = { title, body, pageWidth, pageHeight, size ->
                        showCreatePdf = false
                        viewModel.createTextPdf(title, body, pageWidth, pageHeight, size) { file ->
                            if (file != null) onShareFile(file)
                        }
                    }
                )
            }

            // Search Overlay Bar
            if (showSearchOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    SearchOverlay(
                        searchQuery = searchQuery,
                        matches = searchResults,
                        currentMatchIndex = currentMatchIndex,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onNextMatch = { viewModel.nextSearchMatch() },
                        onPrevMatch = { viewModel.prevSearchMatch() },
                        onClose = {
                            viewModel.clearSearch()
                            showSearchOverlay = false
                        }
                    )
                }
            }

            // Loading Indicator
            if (isLoading) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // Modal Dialogs
    if (showInspector && documentInfo != null) {
        InspectorDialog(
            docInfo = documentInfo!!,
            onDismiss = { showInspector = false }
        )
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSaveSignature = { sig ->
                viewModel.saveSignatureStamp(sig)
                val targetField = formFields.firstOrNull { it.type == org.rabicraft.app.model.FieldType.SIGNATURE }
                if (targetField != null) {
                    viewModel.updateFormFieldValue(targetField.id, sig)
                }
            }
        )
    }

    if (showCommentsSheet) {
        CommentsSheet(
            comments = comments,
            currentPageIndex = currentPageIndex,
            onDismiss = { showCommentsSheet = false },
            onAddComment = { pIdx, text -> viewModel.addComment(pIdx, text) },
            onAddReply = { cId, reply -> viewModel.addCommentReply(cId, reply) },
            onToggleResolved = { viewModel.toggleCommentResolved(it) }
        )
    }

    if (showCombineSplit) {
        CombineSplitDialog(
            totalPages = pages.size,
            onDismiss = { showCombineSplit = false },
            onCombineWithShowcase = {
                viewModel.combineWithShowcase { file ->
                    if (file != null) onShareFile(file)
                }
            },
            onSplitPages = { start, end ->
                // Split selected pages
                val subPages = pages.filter { it.pageIndex + 1 in start..end }
                viewModel.exportDocument { file ->
                    if (file != null) onShareFile(file)
                }
            }
        )
    }

    if (showTextDialog) {
        AddTextDialog(
            onDismiss = { showTextDialog = false },
            onAdd = { text, color ->
                viewModel.addTextEdit(currentPageIndex, text, color)
                showTextDialog = false
            }
        )
    }

    if (showTextEdit) {
        TextEditDialog(
            blocks = detectedBlocks,
            onDismiss = { showTextEdit = false },
            onReplace = { block, newText ->
                viewModel.replaceTextBlock(currentPageIndex, block, newText)
            },
            onDelete = { block ->
                viewModel.deleteTextBlock(currentPageIndex, block)
            }
        )
    }

    if (showScanPrompt) {
        AlertDialog(
            onDismissRequest = {
                showScanPrompt = false
                scanPages = emptyList()
            },
            title = { Text(strings.scanTitle) },
            text = {
                Column {
                    Text(strings.scanPages.format(scanPages.size))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.enhanceScan, modifier = Modifier.weight(1f))
                        Switch(checked = enhanceScan, onCheckedChange = { enhanceScan = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val captured = scanPages
                        showScanPrompt = false
                        scanPages = emptyList()
                        viewModel.createScannedPdf(captured, enhanceScan) { file ->
                            if (file != null) onShareFile(file)
                        }
                    }
                ) {
                    Text(strings.buildPdf)
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanPrompt = false; startScan() }) {
                    Text(strings.addAnotherPage)
                }
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onSelect = { selected ->
                viewModel.setLanguage(selected)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}
