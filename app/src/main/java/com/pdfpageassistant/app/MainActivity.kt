package com.pdfpageassistant.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pdfpageassistant.app.pdf.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PdfProcessor.initPdfBox(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0F62FE),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEDF5FF),
                    onPrimaryContainer = Color(0xFF001D6C),
                    surface = Color.White,
                    background = Color(0xFFF4F7FB),
                    surfaceVariant = Color(0xFFE8EEF5),
                    outline = Color(0xFFD0D7DE)
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PdfAssistantApp()
                    }
                }
            }
        }
    }
}

@Composable
fun PdfAssistantApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMetadata by remember { mutableStateOf<PdfMetadata?>(null) }
    var selectedOption by remember { mutableStateOf(PageOption.ODD) }
    var isProcessing by remember { mutableStateOf(false) }
    var processedPages by remember { mutableIntStateOf(0) }
    var totalPagesToProcess by remember { mutableIntStateOf(0) }
    var processingResult by remember { mutableStateOf<PdfProcessingResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // SAF Document Open Launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri) ?: "document.pdf"
            errorMessage = null
            processingResult = null
            coroutineScope.launch {
                val result = PdfProcessor.getPdfMetadata(context, uri, fileName)
                result.onSuccess { metadata ->
                    selectedMetadata = metadata
                }.onFailure { error ->
                    selectedMetadata = null
                    errorMessage = if (error is PdfProcessingError) {
                        error.getArabicMessage()
                    } else {
                        "❌ تعذر فتح ملف PDF."
                    }
                }
            }
        }
    }

    // SAF Create Document Launcher (Save file)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        if (destinationUri != null && processingResult != null) {
            coroutineScope.launch {
                try {
                    val outputFile = processingResult!!.outputFile
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        outputFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, context.getString(R.string.file_saved_success), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ حدث خطأ أثناء حفظ الملف", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        AppHeader()

        Spacer(modifier = Modifier.height(20.dp))

        // Main Body Content
        when {
            processingResult != null -> {
                ResultView(
                    result = processingResult!!,
                    onSaveClick = {
                        createDocumentLauncher.launch(processingResult!!.newFileName)
                    },
                    onShareClick = {
                        sharePdfFile(context, processingResult!!.outputFile)
                    },
                    onResetClick = {
                        selectedMetadata = null
                        processingResult = null
                        errorMessage = null
                        selectedOption = PageOption.ODD
                    }
                )
            }
            isProcessing -> {
                ProcessingView(
                    processedPages = processedPages,
                    totalPages = totalPagesToProcess
                )
            }
            else -> {
                MainSelectionView(
                    selectedMetadata = selectedMetadata,
                    selectedOption = selectedOption,
                    errorMessage = errorMessage,
                    onSelectFileClick = {
                        openDocumentLauncher.launch(arrayOf("application/pdf"))
                    },
                    onOptionSelected = { option ->
                        selectedOption = option
                    },
                    onProcessClick = {
                        val metadata = selectedMetadata ?: return@MainSelectionView
                        isProcessing = true
                        processedPages = 0
                        totalPagesToProcess = metadata.totalPages
                        errorMessage = null

                        coroutineScope.launch {
                            val result = PdfProcessor.processPdf(
                                context = context,
                                inputUri = metadata.uri,
                                originalFileName = metadata.fileName,
                                pageOption = selectedOption,
                                onProgress = { processed, total ->
                                    processedPages = processed
                                    totalPagesToProcess = total
                                }
                            )

                            isProcessing = false
                            result.onSuccess { res ->
                                processingResult = res
                            }.onFailure { err ->
                                errorMessage = if (err is PdfProcessingError) {
                                    err.getArabicMessage()
                                } else {
                                    "❌ حدث خطأ أثناء تجهيز الملف."
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.app_subtitle),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MainSelectionView(
    selectedMetadata: PdfMetadata?,
    selectedOption: PageOption,
    errorMessage: String?,
    onSelectFileClick: () -> Unit,
    onOptionSelected: (PageOption) -> Unit,
    onProcessClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.select_file_instruction),
            fontSize = 16.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Select PDF Button
        Button(
            onClick = onSelectFileClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.btn_select_pdf),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Error Banner
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFC62828),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // File Details & Page Selection Options
        if (selectedMetadata != null) {
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(id = R.string.file_name_label),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = selectedMetadata.fileName,
                            fontSize = 15.sp,
                            color = Color.DarkGray
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(id = R.string.total_pages_label),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${selectedMetadata.totalPages} ${stringResource(id = R.string.pages_unit)}",
                            fontSize = 15.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Page Options Section Header
            Text(
                text = stringResource(id = R.string.select_pages_section),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Radio Options
            PageOptionCard(
                title = stringResource(id = R.string.option_odd_title),
                subtitle = stringResource(id = R.string.option_odd_sub),
                isSelected = selectedOption == PageOption.ODD,
                onClick = { onOptionSelected(PageOption.ODD) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            PageOptionCard(
                title = stringResource(id = R.string.option_even_title),
                subtitle = stringResource(id = R.string.option_even_sub),
                isSelected = selectedOption == PageOption.EVEN,
                onClick = { onOptionSelected(PageOption.EVEN) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            PageOptionCard(
                title = stringResource(id = R.string.option_all_title),
                subtitle = stringResource(id = R.string.option_all_sub),
                isSelected = selectedOption == PageOption.ALL,
                onClick = { onOptionSelected(PageOption.ALL) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Process Button
            Button(
                onClick = onProcessClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754))
            ) {
                Text(
                    text = stringResource(id = R.string.btn_process_pdf),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PageOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ProcessingView(
    processedPages: Int,
    totalPages: Int
) {
    val progress = if (totalPages > 0) processedPages.toFloat() / totalPages.toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.processing_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.processing_status, processedPages, totalPages),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun ResultView(
    result: PdfProcessingResult,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E7DD)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.result_success_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F5132),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Result Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(
                    label = stringResource(id = R.string.original_file_label),
                    value = result.originalFileName
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                DetailRow(
                    label = stringResource(id = R.string.original_pages_label),
                    value = "${result.originalPageCount}"
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                DetailRow(
                    label = stringResource(id = R.string.page_type_label),
                    value = result.pageOption.getDisplayNameArabic()
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                DetailRow(
                    label = stringResource(id = R.string.new_pages_label),
                    value = "${result.newPageCount}",
                    valueColor = MaterialTheme.colorScheme.primary,
                    isBold = true
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                DetailRow(
                    label = stringResource(id = R.string.new_file_label),
                    value = result.newFileName,
                    isBold = true
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Button(
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6EFD))
        ) {
            Text(
                text = stringResource(id = R.string.btn_save_file),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onShareClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C757D))
        ) {
            Text(
                text = stringResource(id = R.string.btn_share_file),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onResetClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.btn_select_another),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor
        )
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path
        val cut = name?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            name = name?.substring(cut + 1)
        }
    }
    return name
}

private fun sharePdfFile(context: Context, file: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "مشاركة ملف PDF")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "❌ تعذر مشاركة الملف", Toast.LENGTH_SHORT).show()
    }
}
