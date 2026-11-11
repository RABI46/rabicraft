package org.rabicraft.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rabicraft.app.model.PageLayoutMode
import org.rabicraft.app.model.ReadingTheme
import org.rabicraft.app.model.WorkbenchMode
import org.rabicraft.app.ui.components.CombineSplitDialog
import org.rabicraft.app.ui.components.CommentsSheet
import org.rabicraft.app.ui.components.InspectorDialog
import org.rabicraft.app.ui.components.SearchOverlay
import org.rabicraft.app.ui.components.SignaturePadDialog
import org.rabicraft.app.ui.components.ToolRail
import org.rabicraft.app.ui.components.TopBar
import org.rabicraft.app.ui.screens.OrganizeScreen
import org.rabicraft.app.ui.screens.ProtectScreen
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
            RabiCraftTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    onOpenDocumentPicker = { launcher ->
                        launcher.launch(arrayOf("application/pdf"))
                    },
                    onShareFile = { file ->
                        sharePdfFile(file)
                    }
                )
            }
        }
    }

    private fun sharePdfFile(file: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF with RabiCraft"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: PdfWorkbenchViewModel,
    onOpenDocumentPicker: (androidx.activity.result.ActivityResultLauncher<Array<String>>) -> Unit,
    onShareFile: (File) -> Unit
) {
    val documentInfo by viewModel.documentInfo.collectAsStateWithLifecycle()
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

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog & overlay states
    var showInspector by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCombineSplit by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadDocumentFromUri(it) }
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
                documentName = documentInfo?.name ?: "RabiCraft",
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
                WorkbenchMode.READ, WorkbenchMode.ANNOTATE, WorkbenchMode.FILL_SIGN -> {
                    ViewerScreen(
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
}
