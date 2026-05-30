package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.CsvMetadata
import com.mauriciotogneri.fileexplorer.data.model.EpubMetadata
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.ICalendarMetadata
import com.mauriciotogneri.fileexplorer.data.model.OfficeMetadata
import com.mauriciotogneri.fileexplorer.data.model.PdfMetadata
import com.mauriciotogneri.fileexplorer.data.model.SqliteMetadata
import com.mauriciotogneri.fileexplorer.data.model.VCardMetadata
import com.mauriciotogneri.fileexplorer.data.util.toDisplayLanguage
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 3 (Point 3): renders the real [ItemInfoContent] for the 7 metadata types that
 * [ItemInfoScreenTest] never exercises — PDF, Office, EPUB, SQLite, VCard, iCalendar, CSV.
 *
 * Notes / assumptions:
 * - Calls the production [ItemInfoContent] directly (exposed as `internal` as a test seam).
 *   Each `*MetadataSection` is gated purely on its metadata being non-null, independent of the
 *   file's type, so a neutral non-thumbnail file is used to avoid the async Coil image branch.
 * - Date-bearing fields (Office created/modified, EPUB date, iCalendar earliest/latest) flow
 *   through the screen's private `parseAndFormatDate()`. Non-parseable raw strings are passed so
 *   the value is echoed verbatim, keeping assertions deterministic across device locale/timezone.
 * - [ItemInfoContent] lays out rows in a plain `Column` + `verticalScroll` (not a lazy list), so
 *   every row is composed regardless of scroll position. Assertions use `assertExists()` rather
 *   than `assertIsDisplayed()` to stay robust against small screens where later rows scroll off.
 * - Expected plural/string values are resolved via the same resource APIs the screen uses, so
 *   the assertions match whatever formatting (grouping, locale) the device produces.
 */
@RunWith(AndroidJUnit4::class)
class ItemInfoMetadataTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Neutral, non-thumbnail file: section rendering is gated on metadata, not file type.
    private val testFile = FileItem(
        path = "/storage/emulated/0/Documents/sample.bin",
        name = "sample.bin",
        isDirectory = false,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis() - 86_400_000L,
        mimeType = "application/octet-stream",
        childCount = null
    )

    private fun renderInfoContent(
        pdfMetadata: PdfMetadata? = null,
        officeMetadata: OfficeMetadata? = null,
        epubMetadata: EpubMetadata? = null,
        sqliteMetadata: SqliteMetadata? = null,
        vcardMetadata: VCardMetadata? = null,
        icalendarMetadata: ICalendarMetadata? = null,
        csvMetadata: CsvMetadata? = null
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                ItemInfoContent(
                    file = testFile,
                    folderSize = null,
                    imageMetadata = null,
                    audioMetadata = null,
                    videoMetadata = null,
                    pdfMetadata = pdfMetadata,
                    apkMetadata = null,
                    zipMetadata = null,
                    officeMetadata = officeMetadata,
                    epubMetadata = epubMetadata,
                    sqliteMetadata = sqliteMetadata,
                    vcardMetadata = vcardMetadata,
                    icalendarMetadata = icalendarMetadata,
                    csvMetadata = csvMetadata,
                    onOpenFile = {},
                    onCloseClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun string(resId: Int): String = composeTestRule.activity.getString(resId)

    private fun plural(resId: Int, quantity: Int, vararg formatArgs: Any): String =
        composeTestRule.activity.resources.getQuantityString(resId, quantity, *formatArgs)

    // ==================== PDF ====================

    @Test
    fun pdfInfo_displaysPageCount() {
        renderInfoContent(pdfMetadata = PdfMetadata(pageCount = 7))

        composeTestRule.onNodeWithText(string(R.string.info_pages)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.page_count, 7, 7)).assertExists()
    }

    @Test
    fun pdfInfo_nullPageCount_sectionHandlesGracefully() {
        renderInfoContent(pdfMetadata = PdfMetadata(pageCount = null))

        composeTestRule.onNodeWithText(string(R.string.info_pages)).assertDoesNotExist()
    }

    // ==================== Office ====================

    @Test
    fun officeInfo_displaysTitleCreatorSubjectKeywordsDates() {
        renderInfoContent(
            officeMetadata = OfficeMetadata(
                title = "Quarterly Report",
                creator = "Jane Author",
                subject = "Finance",
                keywords = "q3, revenue",
                createdDate = "OFFICE_CREATED_RAW",
                modifiedDate = "OFFICE_MODIFIED_RAW"
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_title)).assertExists()
        composeTestRule.onNodeWithText("Quarterly Report").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_creator)).assertExists()
        composeTestRule.onNodeWithText("Jane Author").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_subject)).assertExists()
        composeTestRule.onNodeWithText("Finance").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_keywords)).assertExists()
        composeTestRule.onNodeWithText("q3, revenue").assertExists()
        // info_created / info_modified labels are shared with the always-present base rows;
        // assert the (unique) verbatim-echoed values instead of the ambiguous labels.
        composeTestRule.onNodeWithText("OFFICE_CREATED_RAW").assertExists()
        composeTestRule.onNodeWithText("OFFICE_MODIFIED_RAW").assertExists()
    }

    @Test
    fun officeInfo_partialNulls_showsOnlyPresentFields() {
        renderInfoContent(
            officeMetadata = OfficeMetadata(
                title = "Only Title",
                creator = null,
                subject = null,
                keywords = null,
                createdDate = null,
                modifiedDate = null
            )
        )

        composeTestRule.onNodeWithText("Only Title").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_creator)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_subject)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_keywords)).assertDoesNotExist()
    }

    // ==================== EPUB ====================

    @Test
    fun epubInfo_displaysTitleCreatorPublisherLanguageDateDescription() {
        renderInfoContent(
            epubMetadata = EpubMetadata(
                title = "The Great Book",
                creator = "A. Writer",
                publisher = "Penguin",
                language = "en",
                date = "EPUB_DATE_RAW",
                description = "A fine read."
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_title)).assertExists()
        composeTestRule.onNodeWithText("The Great Book").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_creator)).assertExists()
        composeTestRule.onNodeWithText("A. Writer").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_publisher)).assertExists()
        composeTestRule.onNodeWithText("Penguin").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_language)).assertExists()
        // EPUB language is rendered via String.toDisplayLanguage(); recompute it the same way.
        composeTestRule.onNodeWithText("en".toDisplayLanguage()).assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_date)).assertExists()
        composeTestRule.onNodeWithText("EPUB_DATE_RAW").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_description)).assertExists()
        composeTestRule.onNodeWithText("A fine read.").assertExists()
    }

    @Test
    fun epubInfo_partialNulls_showsOnlyPresentFields() {
        renderInfoContent(
            epubMetadata = EpubMetadata(
                title = "Just A Title",
                creator = null,
                publisher = null,
                language = null,
                date = null,
                description = null
            )
        )

        composeTestRule.onNodeWithText("Just A Title").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_publisher)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_language)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_description)).assertDoesNotExist()
    }

    // ==================== SQLite ====================

    @Test
    fun sqliteInfo_displaysTableCountAndRowCount() {
        renderInfoContent(
            sqliteMetadata = SqliteMetadata(
                tableCount = 3,
                tableNames = null,
                totalRowCount = 1500L
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_tables)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.table_count, 3, 3)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_total_rows)).assertExists()
        // Production passes the Long count as the format arg (safeCount selects the plural form).
        composeTestRule.onNodeWithText(plural(R.plurals.row_count, 1500, 1500L)).assertExists()
    }

    @Test
    fun sqliteInfo_displaysTableNames() {
        renderInfoContent(
            sqliteMetadata = SqliteMetadata(
                tableCount = null,
                tableNames = listOf("users", "orders", "items"),
                totalRowCount = null
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_table_names)).assertExists()
        composeTestRule.onNodeWithText("users, orders, items").assertExists()
    }

    // ==================== VCard ====================

    @Test
    fun vcardInfo_displaysContactCount() {
        renderInfoContent(
            vcardMetadata = VCardMetadata(
                contactCount = 5,
                hasPhoneNumbers = null,
                hasEmails = null,
                hasPhotos = null
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_contacts)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.contact_count, 5, 5)).assertExists()
    }

    @Test
    fun vcardInfo_displaysBooleanCapabilities() {
        renderInfoContent(
            vcardMetadata = VCardMetadata(
                contactCount = null,
                hasPhoneNumbers = true,
                hasEmails = true,
                hasPhotos = true
            )
        )

        // The "Yes" value is shared by all three rows; assert the (unique) capability labels.
        composeTestRule.onNodeWithText(string(R.string.info_has_phone_numbers)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_has_emails)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_has_photos)).assertExists()
    }

    // ==================== iCalendar ====================

    @Test
    fun icalendarInfo_displaysEventAndTodoCounts() {
        renderInfoContent(
            icalendarMetadata = ICalendarMetadata(
                eventCount = 4,
                todoCount = 2,
                earliestDate = null,
                latestDate = null
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_events)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.event_count, 4, 4)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_todos)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.todo_count, 2, 2)).assertExists()
    }

    @Test
    fun icalendarInfo_displaysDateRange() {
        renderInfoContent(
            icalendarMetadata = ICalendarMetadata(
                eventCount = null,
                todoCount = null,
                earliestDate = "ICAL_EARLIEST_RAW",
                latestDate = "ICAL_LATEST_RAW"
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_earliest_date)).assertExists()
        composeTestRule.onNodeWithText("ICAL_EARLIEST_RAW").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_latest_date)).assertExists()
        composeTestRule.onNodeWithText("ICAL_LATEST_RAW").assertExists()
    }

    // ==================== CSV ====================

    @Test
    fun csvInfo_displaysRowAndColumnCounts() {
        renderInfoContent(csvMetadata = CsvMetadata(rowCount = 100, columnCount = 8))

        composeTestRule.onNodeWithText(string(R.string.info_rows)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.row_count, 100, 100)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_columns)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.column_count, 8, 8)).assertExists()
    }

    @Test
    fun csvInfo_nulls_handledGracefully() {
        renderInfoContent(csvMetadata = CsvMetadata(rowCount = null, columnCount = null))

        composeTestRule.onNodeWithText(string(R.string.info_rows)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_columns)).assertDoesNotExist()
    }

    // ==================== Gating ====================

    @Test
    fun metadataSection_onlyMatchingTypeRendered() {
        renderInfoContent(csvMetadata = CsvMetadata(rowCount = 10, columnCount = 3))

        // CSV section present...
        composeTestRule.onNodeWithText(string(R.string.info_rows)).assertExists()
        // ...while every other metadata section stays gated out.
        composeTestRule.onNodeWithText(string(R.string.info_pages)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_publisher)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_tables)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_contacts)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.info_events)).assertDoesNotExist()
    }
}
