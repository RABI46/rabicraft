package org.rabicraft.app.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object ShowcaseGenerator {

    fun generateShowcasePdf(context: Context): File {
        val targetFile = File(context.cacheDir, "RabiCraft_Showcase.pdf")
        if (targetFile.exists() && targetFile.length() > 500) {
            var isValid = false
            try {
                val pfd = android.os.ParcelFileDescriptor.open(targetFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val testRenderer = android.graphics.pdf.PdfRenderer(pfd)
                if (testRenderer.pageCount >= 5) {
                    isValid = true
                }
                testRenderer.close()
                pfd.close()
            } catch (_: Throwable) {
                targetFile.delete()
            }
            if (isValid) {
                return targetFile
            }
        }

        val tempFile = File(context.cacheDir, "RabiCraft_Showcase.pdf.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val pdfDocument = PdfDocument()
        val pageWidth = 595 // Standard A4 width at 72 dpi
        val pageHeight = 842 // Standard A4 height at 72 dpi

        // Page 1: Cover Page
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        drawCoverPage(page1.canvas, pageWidth, pageHeight)
        pdfDocument.finishPage(page1)

        // Page 2: Scripts of the World (Multilingual Typography)
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        drawScriptsPage(page2.canvas, pageWidth, pageHeight)
        pdfDocument.finishPage(page2)

        // Page 3: Architecture & Security
        val pageInfo3 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
        val page3 = pdfDocument.startPage(pageInfo3)
        drawArchitecturePage(page3.canvas, pageWidth, pageHeight)
        pdfDocument.finishPage(page3)

        // Page 4: Interactive Forms & Signatures
        val pageInfo4 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 4).create()
        val page4 = pdfDocument.startPage(pageInfo4)
        drawFormsPage(page4.canvas, pageWidth, pageHeight)
        pdfDocument.finishPage(page4)

        // Page 5: Vector Graphics & Color Standards
        val pageInfo5 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 5).create()
        val page5 = pdfDocument.startPage(pageInfo5)
        drawVectorStandardsPage(page5.canvas, pageWidth, pageHeight)
        pdfDocument.finishPage(page5)

        try {
            FileOutputStream(tempFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
        } catch (e: Throwable) {
            e.printStackTrace()
            try { pdfDocument.close() } catch (_: Throwable) {}
        }

        return targetFile
    }

    private fun drawCoverPage(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Clean background
        canvas.drawColor(Color.parseColor("#F9F9FB"))

        // Top brand accent header
        paint.color = Color.parseColor("#8A011E")
        canvas.drawRect(0f, 0f, width.toFloat(), 18f, paint)

        // Logo Tile Box
        paint.color = Color.parseColor("#8A011E")
        val tileRect = RectF(60f, 100f, 150f, 190f)
        canvas.drawRoundRect(tileRect, 16f, 16f, paint)

        // Emblem Inside Logo Tile
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("R", tileRect.centerX(), tileRect.centerY() + 18f, paint)

        // Title
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#18181B")
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RabiCraft", 170f, 140f, paint)

        paint.textSize = 14f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#B70228")
        canvas.drawText("THE OPEN-SOURCE PDF WORKBENCH", 170f, 170f, paint)

        // Subtitle & Intro Box
        paint.color = Color.parseColor("#FFFFFF")
        val boxRect = RectF(60f, 230f, width - 60f, 520f)
        canvas.drawRoundRect(boxRect, 12f, 12f, paint)

        paint.color = Color.parseColor("#E4E4E7")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(boxRect, 12f, 12f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#27272A")
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Document Specimen & Feature Showcase", 84f, 275f, paint)

        paint.color = Color.parseColor("#52525B")
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        val introLines = listOf(
            "Welcome to RabiCraft on Android. This specimen document demonstrates",
            "the core capabilities of the native PDF workbench:",
            "",
            "• Faithful Rendering: Kerning, ligatures, RTL and complex scripts.",
            "• Fearless Editing: Atomic saves, non-destructive undo, page organize.",
            "• High-Precision Annotation: Ink pen, smart highlighter, text callouts.",
            "• Fill & Sign: Interactive form filling and digital signature stamping.",
            "• Document Security: Encryption metadata, permission levels, redact.",
            "",
            "Tap any tool in the workbench rail to explore and interact."
        )

        var textY = 310f
        for (line in introLines) {
            canvas.drawText(line, 84f, textY, paint)
            textY += 20f
        }

        // Specs badges at bottom
        val badgeRect1 = RectF(60f, 550f, 210f, 620f)
        val badgeRect2 = RectF(225f, 550f, 375f, 620f)
        val badgeRect3 = RectF(390f, 550f, width - 60f, 620f)

        paint.color = Color.parseColor("#FFFFFF")
        canvas.drawRoundRect(badgeRect1, 8f, 8f, paint)
        canvas.drawRoundRect(badgeRect2, 8f, 8f, paint)
        canvas.drawRoundRect(badgeRect3, 8f, 8f, paint)

        paint.color = Color.parseColor("#E4E4E7")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(badgeRect1, 8f, 8f, paint)
        canvas.drawRoundRect(badgeRect2, 8f, 8f, paint)
        canvas.drawRoundRect(badgeRect3, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#8A011E")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("ISO 32000-2", 76f, 580f, paint)
        canvas.drawText("OFFLINE FIRST", 241f, 580f, paint)
        canvas.drawText("NO TELEMETRY", 406f, 580f, paint)

        paint.color = Color.parseColor("#71717A")
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("PDF 1.7 / 2.0 Standard", 76f, 602f, paint)
        canvas.drawText("100% Private to device", 241f, 602f, paint)
        canvas.drawText("No cloud, zero tracking", 406f, 602f, paint)

        // Footer
        paint.color = Color.parseColor("#A1A1AA")
        paint.textSize = 10f
        canvas.drawText("RabiCraft Specimen v1.0 • Cover Page (i)", 60f, height - 40f, paint)
    }

    private fun drawScriptsPage(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        // Header
        paint.color = Color.parseColor("#8A011E")
        paint.textSize = 24f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Scripts of the World & Typography", 60f, 90f, paint)

        paint.color = Color.parseColor("#71717A")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Testing bidirectional text, Unicode glyphs, ligatures, and font metric rendering.", 60f, 115f, paint)

        // Script rows
        val samples = listOf(
            Triple("Latin / English", "The quick brown fox jumps over the lazy dog.", "Standard typography, kerning, ligatures ff, fi, fl"),
            Triple("Arabic / العربية", "كل إنسان يولد حراً ومتساوياً في الكرامة والحقوق.", "Right-to-left cursive shaping and contextual glyphs"),
            Triple("Hebrew / עברית", "כל בני אדם נולדו בני חורין ושווים בערכם ובזכויותיהם.", "BiDi paragraph direction and Hebrew vowels"),
            Triple("Greek / Ελληνικά", "Όλοι οι άνθρωποι γεννιούνται ελεύθεροι και ίσοι.", "Classical Greek diacritics and glyph precision"),
            Triple("Cyrillic / Русский", "Все люди рождаются свободными и равными.", "Cyrillic upright and italic character rendering"),
            Triple("Japanese / 日本語", "すべての人間は、生まれながらにして自由であり。", "Kanji, Hiragana, Katakana and vertical orientation"),
            Triple("Korean / 한국어", "모든 인간은 태어날 때부터 자유로우며.", "Hangul syllable blocks and modern font spacing"),
            Triple("Devanagari / हिन्दी", "सभी मनुष्यों को गौरव और अधिकारों के मामले में.", "Complex conjunct consonants and matras")
        )

        var cardY = 150f
        for ((title, text, note) in samples) {
            paint.color = Color.parseColor("#F8F9FA")
            val r = RectF(60f, cardY, width - 60f, cardY + 68f)
            canvas.drawRoundRect(r, 8f, 8f, paint)

            paint.color = Color.parseColor("#8A011E")
            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(title, 76f, cardY + 22f, paint)

            paint.color = Color.parseColor("#18181B")
            paint.textSize = 13f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(text, 76f, cardY + 44f, paint)

            paint.color = Color.parseColor("#71717A")
            paint.textSize = 10f
            canvas.drawText(note, 76f, cardY + 60f, paint)

            cardY += 76f
        }

        // Footer
        paint.color = Color.parseColor("#A1A1AA")
        paint.textSize = 10f
        canvas.drawText("RabiCraft Specimen v1.0 • Page 2 of 5", 60f, height - 40f, paint)
    }

    private fun drawArchitecturePage(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        paint.color = Color.parseColor("#8A011E")
        paint.textSize = 24f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Native Architecture & Security", 60f, 90f, paint)

        paint.color = Color.parseColor("#71717A")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Robust PDF processing, memory safety, and zero telemetry by design.", 60f, 115f, paint)

        // Code Listing Box (Dark themed)
        paint.color = Color.parseColor("#19191C")
        val codeBox = RectF(60f, 150f, width - 60f, 380f)
        canvas.drawRoundRect(codeBox, 10f, 10f, paint)

        // Code Header Bar
        paint.color = Color.parseColor("#27272A")
        val codeHeader = RectF(60f, 150f, width - 60f, 185f)
        canvas.drawRoundRect(codeHeader, 10f, 10f, paint)
        canvas.drawRect(60f, 175f, width - 60f, 185f, paint)

        paint.color = Color.parseColor("#A1A1AA")
        paint.textSize = 11f
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText("crates/engine/src/document.rs", 76f, 172f, paint)

        // Code Content
        val codeLines = listOf(
            "pub fn open_document<P: AsRef<Path>>(path: P) -> Result<Document> {",
            "    let bytes = std::fs::read(path)?;",
            "    // Verify PDF header magic bytes %PDF-1.x",
            "    let header = parse_pdf_header(&bytes)?;",
            "    let xref = read_cross_reference_table(&bytes)?;",
            "    let catalog = parse_root_catalog(&bytes, &xref)?;",
            "    Ok(Document { header, xref, catalog, permissions: Default::default() })",
            "}"
        )

        paint.textSize = 11f
        var codeY = 215f
        for (line in codeLines) {
            paint.color = if (line.contains("fn ") || line.contains("let ") || line.contains("pub ")) {
                Color.parseColor("#F43F5E")
            } else if (line.contains("//")) {
                Color.parseColor("#71717A")
            } else {
                Color.parseColor("#E4E4E7")
            }
            canvas.drawText(line, 76f, codeY, paint)
            codeY += 20f
        }

        // Principles Cards
        val principles = listOf(
            "Fail with Result: Non-test code returns errors gracefully through explicit types.",
            "No Crashes: Page isolation prevents hostile or corrupted PDF streams from failing.",
            "Atomic Operations: Modifying pages or annotations preserves original file integrity."
        )

        var pY = 410f
        for (p in principles) {
            paint.color = Color.parseColor("#F4F4F5")
            val r = RectF(60f, pY, width - 60f, pY + 54f)
            canvas.drawRoundRect(r, 8f, 8f, paint)

            paint.color = Color.parseColor("#B70228")
            canvas.drawCircle(82f, pY + 27f, 5f, paint)

            paint.color = Color.parseColor("#27272A")
            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(p, 102f, pY + 31f, paint)
            pY += 66f
        }

        // Footer
        paint.color = Color.parseColor("#A1A1AA")
        paint.textSize = 10f
        canvas.drawText("RabiCraft Specimen v1.0 • Page 3 of 5", 60f, height - 40f, paint)
    }

    private fun drawFormsPage(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        paint.color = Color.parseColor("#8A011E")
        paint.textSize = 24f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Interactive Forms & Fill & Sign", 60f, 90f, paint)

        paint.color = Color.parseColor("#71717A")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("AcroForm interactive fields, signature blocks, and form calculation support.", 60f, 115f, paint)

        // Form Container
        val formBox = RectF(60f, 145f, width - 60f, 680f)
        paint.color = Color.parseColor("#FAFAFA")
        canvas.drawRoundRect(formBox, 12f, 12f, paint)
        paint.color = Color.parseColor("#E4E4E7")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(formBox, 12f, 12f, paint)
        paint.style = Paint.Style.FILL

        // Form Fields
        var fieldY = 180f

        // Full Name Field
        paint.color = Color.parseColor("#3F3F46")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Full Name / Nom complet", 84f, fieldY, paint)
        fieldY += 16f
        val nameBox = RectF(84f, fieldY, width - 84f, fieldY + 36f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(nameBox, 6f, 6f, paint)
        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(nameBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#18181B")
        paint.textSize = 13f
        canvas.drawText("Alexandre Dumas", 96f, fieldY + 23f, paint)

        fieldY += 56f
        // Email Field
        paint.color = Color.parseColor("#3F3F46")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Email Address", 84f, fieldY, paint)
        fieldY += 16f
        val emailBox = RectF(84f, fieldY, width - 84f, fieldY + 36f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(emailBox, 6f, 6f, paint)
        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(emailBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#18181B")
        paint.textSize = 13f
        canvas.drawText("alexandre.dumas@rabicraft.org", 96f, fieldY + 23f, paint)

        fieldY += 56f
        // Checkboxes
        val checkRect = RectF(84f, fieldY, 104f, fieldY + 20f)
        paint.color = Color.parseColor("#8A011E")
        canvas.drawRoundRect(checkRect, 4f, 4f, paint)
        paint.color = Color.WHITE
        paint.textSize = 13f
        canvas.drawText("✓", 88f, fieldY + 15f, paint)

        paint.color = Color.parseColor("#18181B")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("I confirm that this document is ready for signing and archiving.", 114f, fieldY + 15f, paint)

        fieldY += 46f
        // Signature Block Area
        paint.color = Color.parseColor("#3F3F46")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Digital Signature & Verification Stamp", 84f, fieldY, paint)
        fieldY += 16f
        val sigBox = RectF(84f, fieldY, width - 84f, fieldY + 100f)
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(sigBox, 8f, 8f, paint)
        paint.color = Color.parseColor("#94A3B8")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(sigBox, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Simulated handwritten signature stroke
        paint.color = Color.parseColor("#1E3A8A")
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        canvas.drawText("A. Dumas", 120f, fieldY + 58f, paint)

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Digitally certified by RabiCraft Trust Store • Nov 11, 2026", 120f, fieldY + 84f, paint)

        // Footer
        paint.color = Color.parseColor("#A1A1AA")
        paint.textSize = 10f
        canvas.drawText("RabiCraft Specimen v1.0 • Page 4 of 5", 60f, height - 40f, paint)
    }

    private fun drawVectorStandardsPage(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        paint.color = Color.parseColor("#8A011E")
        paint.textSize = 24f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Vector Graphics & Color Precision", 60f, 90f, paint)

        paint.color = Color.parseColor("#71717A")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("RGB, CMYK, spot separations, opacity blending, and vector path accuracy.", 60f, 115f, paint)

        // Color Swatches
        val swatches = listOf(
            Pair("Rabi Wine", "#8A011E"),
            Pair("Crimson", "#B70228"),
            Pair("Charcoal", "#19191C"),
            Pair("Gold Leaf", "#C99700"),
            Pair("Slate 800", "#1E293B"),
            Pair("Emerald", "#059669")
        )

        var swatchX = 60f
        val swatchWidth = (width - 120f - (5 * 12f)) / 6f
        for ((name, hex) in swatches) {
            paint.color = Color.parseColor(hex)
            val rect = RectF(swatchX, 150f, swatchX + swatchWidth, 220f)
            canvas.drawRoundRect(rect, 8f, 8f, paint)

            paint.color = Color.parseColor("#18181B")
            paint.textSize = 10f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(name, swatchX, 238f, paint)
            paint.color = Color.parseColor("#71717A")
            paint.textSize = 9f
            canvas.drawText(hex, swatchX, 252f, paint)

            swatchX += swatchWidth + 12f
        }

        // Circular Overlapping Blend Shapes
        paint.color = Color.parseColor("#808A011E") // 50% opacity
        canvas.drawCircle(190f, 380f, 75f, paint)

        paint.color = Color.parseColor("#800284C7") // 50% opacity cyan
        canvas.drawCircle(270f, 380f, 75f, paint)

        paint.color = Color.parseColor("#80C99700") // 50% opacity gold
        canvas.drawCircle(230f, 440f, 75f, paint)

        paint.color = Color.parseColor("#18181B")
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Transparency & Alpha Compositing", 60f, 550f, paint)

        paint.color = Color.parseColor("#52525B")
        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("PDF 1.4+ transparency group models and soft masks render seamlessly.", 60f, 570f, paint)

        // Footer
        paint.color = Color.parseColor("#A1A1AA")
        paint.textSize = 10f
        canvas.drawText("RabiCraft Specimen v1.0 • Page 5 of 5", 60f, height - 40f, paint)
    }
}
