package com.pdfpageassistant.app.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

enum class PageOption {
    ODD,
    EVEN,
    ALL;

    fun shouldIncludePage(pageNumber: Int): Boolean {
        return when (this) {
            ODD -> pageNumber % 2 != 0
            EVEN -> pageNumber % 2 == 0
            ALL -> true
        }
    }

    fun getSuffix(): String {
        return when (this) {
            ODD -> "_صفحات_فردية"
            EVEN -> "_صفحات_زوجية"
            ALL -> "_كامل"
        }
    }

    fun getDisplayNameArabic(): String {
        return when (this) {
            ODD -> "الفردية"
            EVEN -> "الزوجية"
            ALL -> "كل الصفحات"
        }
    }
}

data class PdfMetadata(
    val fileName: String,
    val totalPages: Int,
    val uri: Uri
)

data class PdfProcessingResult(
    val originalFileName: String,
    val originalPageCount: Int,
    val pageOption: PageOption,
    val newPageCount: Int,
    val newFileName: String,
    val outputFile: File
)

sealed class PdfProcessingError : Exception() {
    object InvalidOrCorruptedPdf : PdfProcessingError()
    object CannotOpenPdf : PdfProcessingError()
    object InsufficientStorage : PdfProcessingError()
    object GeneralError : PdfProcessingError()

    fun getArabicMessage(): String {
        return when (this) {
            is InvalidOrCorruptedPdf -> "❌ ملف PDF غير صالح أو تالف."
            is CannotOpenPdf -> "❌ تعذر فتح ملف PDF."
            is InsufficientStorage -> "❌ لا توجد مساحة تخزين كافية."
            is GeneralError -> "❌ حدث خطأ أثناء تجهيز الملف."
        }
    }
}

object PdfProcessor {

    private var isPdfBoxInitialized = false

    fun initPdfBox(context: Context) {
        if (!isPdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isPdfBoxInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Calculates page distribution without processing the entire document.
     */
    fun calculateTargetPageCount(totalPages: Int, pageOption: PageOption): Int {
        if (totalPages <= 0) return 0
        return when (pageOption) {
            PageOption.ALL -> totalPages
            PageOption.ODD -> (totalPages + 1) / 2
            PageOption.EVEN -> totalPages / 2
        }
    }

    /**
     * Reads PDF metadata (file name and total page count).
     */
    suspend fun getPdfMetadata(context: Context, uri: Uri, defaultFileName: String): Result<PdfMetadata> =
        withContext(Dispatchers.IO) {
            try {
                initPdfBox(context)
                val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(PdfProcessingError.CannotOpenPdf)

                inputStream.use { stream ->
                    PDDocument.load(stream).use { document ->
                        val totalPages = document.numberOfPages
                        if (totalPages <= 0) {
                            return@withContext Result.failure(PdfProcessingError.InvalidOrCorruptedPdf)
                        }
                        Result.success(PdfMetadata(fileName = defaultFileName, totalPages = totalPages, uri = uri))
                    }
                }
            } catch (e: SecurityException) {
                Result.failure(PdfProcessingError.CannotOpenPdf)
            } catch (e: Exception) {
                Result.failure(PdfProcessingError.InvalidOrCorruptedPdf)
            }
        }

    /**
     * Generates a unique output file name when a file with the target name already exists.
     */
    fun generateUniqueFileName(outputDir: File, originalFileName: String, pageOption: PageOption): String {
        val baseName = if (originalFileName.endsWith(".pdf", ignoreCase = true)) {
            originalFileName.substring(0, originalFileName.length - 4)
        } else {
            originalFileName
        }

        val suffix = pageOption.getSuffix()
        val candidateName = "${baseName}${suffix}.pdf"
        var candidateFile = File(outputDir, candidateName)

        if (!candidateFile.exists()) {
            return candidateName
        }

        var counter = 1
        while (true) {
            val indexedName = "${baseName}${suffix}_${counter}.pdf"
            candidateFile = File(outputDir, indexedName)
            if (!candidateFile.exists()) {
                return indexedName
            }
            counter++
        }
    }

    /**
     * Processes input PDF based on the requested page option, calling [onProgress] with (processedPage, totalPages).
     */
    suspend fun processPdf(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        pageOption: PageOption,
        onProgress: (processedPage: Int, totalPages: Int) -> Unit
    ): Result<PdfProcessingResult> = withContext(Dispatchers.IO) {
        initPdfBox(context)

        val outputDir = File(context.cacheDir, "prepared_pdfs").apply {
            if (!exists()) mkdirs()
        }

        val outputFileName = generateUniqueFileName(outputDir, originalFileName, pageOption)
        val outputFile = File(outputDir, outputFileName)

        var inputDoc: PDDocument? = null
        var outputDoc: PDDocument? = null

        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(PdfProcessingError.CannotOpenPdf)

            inputDoc = inputStream.use { stream -> PDDocument.load(stream) }
            val totalPages = inputDoc.numberOfPages

            if (totalPages <= 0) {
                return@withContext Result.failure(PdfProcessingError.InvalidOrCorruptedPdf)
            }

            outputDoc = PDDocument()
            var newPageCount = 0

            for (i in 0 until totalPages) {
                val pageNumber = i + 1 // 1-based page index
                if (pageOption.shouldIncludePage(pageNumber)) {
                    val page = inputDoc.getPage(i)
                    outputDoc.addPage(page)
                    newPageCount++
                }
                onProgress(i + 1, totalPages)
            }

            FileOutputStream(outputFile).use { fos ->
                outputDoc.save(fos)
            }

            Result.success(
                PdfProcessingResult(
                    originalFileName = originalFileName,
                    originalPageCount = totalPages,
                    pageOption = pageOption,
                    newPageCount = newPageCount,
                    newFileName = outputFileName,
                    outputFile = outputFile
                )
            )
        } catch (e: OutOfMemoryError) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(PdfProcessingError.InsufficientStorage)
        } catch (e: java.io.IOException) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(PdfProcessingError.CannotOpenPdf)
        } catch (e: PdfProcessingError) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(e)
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(PdfProcessingError.GeneralError)
        } finally {
            try {
                outputDoc?.close()
            } catch (_: Exception) {}
            try {
                inputDoc?.close()
            } catch (_: Exception) {}
        }
    }
}
