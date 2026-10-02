package com.pdfpageassistant.app

import com.pdfpageassistant.app.pdf.PageOption
import com.pdfpageassistant.app.pdf.PdfProcessingError
import com.pdfpageassistant.app.pdf.PdfProcessor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPageOptionInclusion() {
        // Odd Option Tests
        assertTrue(PageOption.ODD.shouldIncludePage(1))
        assertFalse(PageOption.ODD.shouldIncludePage(2))
        assertTrue(PageOption.ODD.shouldIncludePage(3))
        assertFalse(PageOption.ODD.shouldIncludePage(4))
        assertTrue(PageOption.ODD.shouldIncludePage(499))
        assertFalse(PageOption.ODD.shouldIncludePage(500))

        // Even Option Tests
        assertFalse(PageOption.EVEN.shouldIncludePage(1))
        assertTrue(PageOption.EVEN.shouldIncludePage(2))
        assertFalse(PageOption.EVEN.shouldIncludePage(3))
        assertTrue(PageOption.EVEN.shouldIncludePage(4))
        assertFalse(PageOption.EVEN.shouldIncludePage(499))
        assertTrue(PageOption.EVEN.shouldIncludePage(500))

        // All Option Tests
        assertTrue(PageOption.ALL.shouldIncludePage(1))
        assertTrue(PageOption.ALL.shouldIncludePage(2))
        assertTrue(PageOption.ALL.shouldIncludePage(500))
    }

    @Test
    fun testTargetPageCountsForVariousDocumentSizes() {
        // 2 pages
        assertEquals(1, PdfProcessor.calculateTargetPageCount(2, PageOption.ODD))
        assertEquals(1, PdfProcessor.calculateTargetPageCount(2, PageOption.EVEN))
        assertEquals(2, PdfProcessor.calculateTargetPageCount(2, PageOption.ALL))

        // 10 pages
        assertEquals(5, PdfProcessor.calculateTargetPageCount(10, PageOption.ODD))
        assertEquals(5, PdfProcessor.calculateTargetPageCount(10, PageOption.EVEN))
        assertEquals(10, PdfProcessor.calculateTargetPageCount(10, PageOption.ALL))

        // 100 pages
        assertEquals(50, PdfProcessor.calculateTargetPageCount(100, PageOption.ODD))
        assertEquals(50, PdfProcessor.calculateTargetPageCount(100, PageOption.EVEN))
        assertEquals(100, PdfProcessor.calculateTargetPageCount(100, PageOption.ALL))

        // 101 pages
        assertEquals(51, PdfProcessor.calculateTargetPageCount(101, PageOption.ODD))
        assertEquals(50, PdfProcessor.calculateTargetPageCount(101, PageOption.EVEN))
        assertEquals(101, PdfProcessor.calculateTargetPageCount(101, PageOption.ALL))

        // 200 pages
        assertEquals(100, PdfProcessor.calculateTargetPageCount(200, PageOption.ODD))
        assertEquals(100, PdfProcessor.calculateTargetPageCount(200, PageOption.EVEN))
        assertEquals(200, PdfProcessor.calculateTargetPageCount(200, PageOption.ALL))

        // 500 pages
        assertEquals(250, PdfProcessor.calculateTargetPageCount(500, PageOption.ODD))
        assertEquals(250, PdfProcessor.calculateTargetPageCount(500, PageOption.EVEN))
        assertEquals(500, PdfProcessor.calculateTargetPageCount(500, PageOption.ALL))

        // 501 pages
        assertEquals(251, PdfProcessor.calculateTargetPageCount(501, PageOption.ODD))
        assertEquals(250, PdfProcessor.calculateTargetPageCount(501, PageOption.EVEN))
        assertEquals(501, PdfProcessor.calculateTargetPageCount(501, PageOption.ALL))

        // 1000 pages
        assertEquals(500, PdfProcessor.calculateTargetPageCount(1000, PageOption.ODD))
        assertEquals(500, PdfProcessor.calculateTargetPageCount(1000, PageOption.EVEN))
        assertEquals(1000, PdfProcessor.calculateTargetPageCount(1000, PageOption.ALL))
    }

    @Test
    fun testUniqueFileNameGeneration() {
        val testDir = tempFolder.newFolder("output_test")

        val nameOdd1 = PdfProcessor.generateUniqueFileName(testDir, "كتاب.pdf", PageOption.ODD)
        assertEquals("كتاب_صفحات_فردية.pdf", nameOdd1)

        // Create the file to test conflict resolution
        File(testDir, nameOdd1).createNewFile()

        val nameOdd2 = PdfProcessor.generateUniqueFileName(testDir, "كتاب.pdf", PageOption.ODD)
        assertEquals("كتاب_صفحات_فردية_1.pdf", nameOdd2)

        File(testDir, nameOdd2).createNewFile()

        val nameOdd3 = PdfProcessor.generateUniqueFileName(testDir, "كتاب.pdf", PageOption.ODD)
        assertEquals("كتاب_صفحات_فردية_2.pdf", nameOdd3)

        // Even and All Option Naming
        val nameEven = PdfProcessor.generateUniqueFileName(testDir, "كتاب.pdf", PageOption.EVEN)
        assertEquals("كتاب_صفحات_زوجية.pdf", nameEven)

        val nameAll = PdfProcessor.generateUniqueFileName(testDir, "كتاب.pdf", PageOption.ALL)
        assertEquals("كتاب_كامل.pdf", nameAll)
    }

    @Test
    fun testArabicErrorMessages() {
        assertEquals("❌ ملف PDF غير صالح أو تالف.", PdfProcessingError.InvalidOrCorruptedPdf.getArabicMessage())
        assertEquals("❌ تعذر فتح ملف PDF.", PdfProcessingError.CannotOpenPdf.getArabicMessage())
        assertEquals("❌ لا توجد مساحة تخزين كافية.", PdfProcessingError.InsufficientStorage.getArabicMessage())
        assertEquals("❌ حدث خطأ أثناء تجهيز الملف.", PdfProcessingError.GeneralError.getArabicMessage())
    }
}
