package com.mauriciotogneri.fileexplorer.testutil

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Well-formed fixtures for the formats the metadata extractors and thumbnail fetchers parse.
 *
 * These exist because the robustness suites could only prove "a bad file does not crash us" — a
 * guarantee an extractor rewritten to `return null` unconditionally satisfies perfectly. A positive
 * control per format is what makes those suites measure something.
 *
 * Everything here is built at runtime rather than checked in, so the fixture and the assertion
 * about it sit in the same file and cannot drift. The two formats that cannot be produced without
 * an encoder — MP3 and MP4 — are the exception and live in `androidTest/assets`, alongside the GIFs
 * [com.mauriciotogneri.fileexplorer.data.util.AppImageLoaderGifTest] already uses; reach them with
 * [copyAsset].
 */
object DocumentFixtures {

    /** Copies a fixture out of `androidTest/assets` onto disk, where the file APIs can reach it. */
    fun copyAsset(context: Context, assetName: String, dir: File, fileName: String = assetName): File {
        val file = File(dir, fileName)
        context.assets.open(assetName).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    /** A real PDF with [pageCount] pages, written by the platform's own PDF writer. */
    fun createPdf(dir: File, name: String = "document.pdf", pageCount: Int = 3): File {
        val file = File(dir, name)
        val document = PdfDocument()
        try {
            repeat(pageCount) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(200, 200, index + 1).create()
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("page ${index + 1}", 20f, 100f, Paint().apply { textSize = 16f })
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return file
    }

    /**
     * A minimal but valid EPUB: the `META-INF/container.xml` pointer the reader follows, the OPF it
     * points at, and a cover image. `EpubThumbnailFetcher` finds a cover by name, so the entry is
     * called `cover.png` rather than being declared only in the manifest.
     */
    fun createEpub(
        dir: File,
        name: String = "book.epub",
        title: String = "Fixture Title",
        creator: String = "Fixture Author",
        publisher: String = "Fixture Press",
        language: String = "en",
        withCover: Boolean = true
    ): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.write(
                "mimetype",
                "application/epub+zip"
            )
            zip.write(
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent()
            )
            zip.write(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:creator>$creator</dc:creator>
                    <dc:publisher>$publisher</dc:publisher>
                    <dc:language>$language</dc:language>
                    <dc:date>2024-01-01</dc:date>
                    <dc:description>A fixture book.</dc:description>
                  </metadata>
                  <manifest>
                    <item id="cover" href="cover.png" media-type="image/png"/>
                  </manifest>
                  <spine/>
                </package>
                """.trimIndent()
            )
            if (withCover) {
                zip.putNextEntry(ZipEntry("OEBPS/cover.png"))
                zip.write(pngBytes(64, 64, Color.rgb(0, 128, 128)))
                zip.closeEntry()
            }
        }
        return file
    }

    /**
     * A minimal Office Open XML document. `OfficeMetadataExtractor` reads only `docProps/core.xml`,
     * so that is the entry that has to be right.
     */
    fun createDocx(
        dir: File,
        name: String = "document.docx",
        title: String = "Fixture Document",
        creator: String = "Fixture Author",
        subject: String = "Fixtures"
    ): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.write(
                "docProps/core.xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties
                    xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:dcterms="http://purl.org/dc/terms/">
                  <dc:title>$title</dc:title>
                  <dc:creator>$creator</dc:creator>
                  <dc:subject>$subject</dc:subject>
                  <cp:keywords>fixture,test</cp:keywords>
                  <dcterms:created>2024-01-01T00:00:00Z</dcterms:created>
                  <dcterms:modified>2024-01-02T00:00:00Z</dcterms:modified>
                </cp:coreProperties>
                """.trimIndent()
            )
            zip.write("word/document.xml", "<document/>")
        }
        return file
    }

    /** A real SQLite database carrying [tables] tables, each with [rowsPerTable] rows. */
    fun createSqliteDb(
        dir: File,
        name: String = "data.db",
        tables: List<String> = listOf("notes", "authors"),
        rowsPerTable: Int = 2
    ): File {
        val file = File(dir, name)
        val database = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null)
        try {
            tables.forEach { table ->
                database.execSQL("CREATE TABLE $table (id INTEGER PRIMARY KEY, value TEXT)")
                repeat(rowsPerTable) { row ->
                    database.execSQL("INSERT INTO $table (value) VALUES ('row $row')")
                }
            }
        } finally {
            database.close()
        }
        return file
    }

    /**
     * A JPEG carrying the EXIF tags `ImageMetadataExtractor` reads. Written as a real bitmap first
     * because `ExifInterface` can only add tags to an image it can already parse.
     */
    fun createJpegWithExif(
        dir: File,
        name: String = "photo.jpg",
        width: Int = 80,
        height: Int = 60,
        cameraMake: String = "Fixture Optics",
        cameraModel: String = "FX-1"
    ): File {
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(200, 120, 40))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()

        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, cameraMake)
            setAttribute(ExifInterface.TAG_MODEL, cameraModel)
            setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
            setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:01:01 12:00:00")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "200")
            setAttribute(ExifInterface.TAG_F_NUMBER, "2.8")
            saveAttributes()
        }
        return file
    }

    private fun pngBytes(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun ZipOutputStream.write(entryName: String, content: String) {
        putNextEntry(ZipEntry(entryName))
        write(content.toByteArray())
        closeEntry()
    }
}
