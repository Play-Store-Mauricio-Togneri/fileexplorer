package com.mauriciotogneri.fileexplorer.data.util

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Where the MIME type a file was opened with came from: the `type_source` analytics param of
 * `file_opened` and `file_open_failed`.
 */
enum class TypeSource(val param: String) {
    /** The file's name gave the type, `application/octet-stream` with no content match included. */
    EXTENSION("extension"),

    /** The file's first bytes gave the type, always a member of [MimeTypeSniffer.TYPES]. */
    CONTENT("content"),

    /** Neither did: the file went out as the wildcard type. */
    NONE("none"),

    /** The contents were needed but could not be read in time, so the name's type went out. */
    UNREAD("unread")
}

/** The type a file is opened with, and where it came from. */
data class ResolvedType(val mimeType: String, val source: TypeSource)

/** What reading a file's first bytes concluded. */
sealed interface SniffOutcome {
    data class Match(val mimeType: String) : SniffOutcome
    data object NoMatch : SniffOutcome
    data object Unreadable : SniffOutcome
}

/**
 * Gives a file whose name says nothing about its type a type taken from its contents, so it is not
 * sent to other apps as "any type", which makes the system picker list every app on the phone.
 *
 * A name that gives a specific type always wins and is never read: [resolve] returns it at once,
 * without I/O and without suspending, so opening a named file stays exactly as it was. Only wildcard
 * and `application/octet-stream` names are read, and only when the user acts on that one file —
 * never while listing a folder. App bundles are never read either: see [APP_BUNDLE_EXTENSIONS].
 *
 * @param scope owns the read. As in `StartupFolderResolver`, it must not be the caller's own scope:
 *   the timeout has to stop the caller *waiting* while a stuck read finishes on its own thread.
 * @param dispatcher where the file is read
 * @param timeoutMs how long [resolve] waits for the read before sending the name's type
 * @param sniff reads the file; replaced by tests to control time and failures
 */
class MimeTypeResolver(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val sniff: suspend (File) -> SniffOutcome = { readAndSniff(it) }
) {
    /**
     * The type to open [path] with, given [nameType], the type its name gave.
     *
     * The read is its own coroutine in [scope] so that the timeout bounds the wait, not the read: a
     * read of a stalled volume is an uninterruptible syscall that no cancellation can stop. The
     * abandoned read finishes on its own and its answer is discarded.
     */
    suspend fun resolve(path: String, nameType: String): ResolvedType {
        if (!readsContent(path, nameType)) return ResolvedType(nameType, nameOnlySource(nameType))

        val read = scope.async(dispatcher) { guarded { sniff(File(path)) } }
        val outcome = withTimeoutOrNull(timeoutMs) { read.await() }

        read.cancel()

        return when (outcome) {
            is SniffOutcome.Match -> ResolvedType(outcome.mimeType, TypeSource.CONTENT)
            SniffOutcome.NoMatch -> ResolvedType(nameType, nameOnlySource(nameType))
            SniffOutcome.Unreadable, null -> ResolvedType(nameType, TypeSource.UNREAD)
        }
    }

    companion object {
        /**
         * How long an open waits for a file's first bytes. A healthy volume answers in a few
         * milliseconds; this is the point where a slow SD card or a USB drive spinning up stops being
         * worth waiting for, and the file opens as it did before contents were read.
         */
        const val TIMEOUT_MS = 300L

        private const val ANY_TYPE = "*/*"
        private const val ZIP_TYPE = "application/zip"

        /** The longest `mimetype` entry an EPUB or ODF archive is read for. */
        private const val MIMETYPE_ENTRY_BYTES = 100

        /**
         * Split-APK bundles: ZIP archives that only a bundle installer can use. Their extensions are
         * unknown to the platform, so their name gives no type, but typed from their contents they
         * would be plain ZIPs, and a tap would open the extract dialog instead of offering the
         * installer. They keep going out as "any type", as they did before contents were read.
         */
        private val APP_BUNDLE_EXTENSIONS = setOf("xapk", "apks", "apkm")

        /** Whether [nameType] says too little about a file to open it with. */
        private fun needsContent(nameType: String): Boolean =
            nameType == ANY_TYPE || nameType == "application/octet-stream"

        /** Whether the file at [path], whose name gave [nameType], is typed from its contents. */
        private fun readsContent(path: String, nameType: String): Boolean =
            needsContent(nameType) && File(path).extension.lowercase() !in APP_BUNDLE_EXTENSIONS

        /**
         * [item] with the type its contents give, read on the calling thread with no timeout. For
         * callers already on an I/O dispatcher that are about to read the file anyway: the viewers.
         */
        fun resolveNow(item: FileItem): FileItem {
            if (!readsContent(item.path, item.mimeType)) return item

            val outcome = guarded { readAndSniff(File(item.path)) }

            return if (outcome is SniffOutcome.Match) item.withMimeType(outcome.mimeType) else item
        }

        /**
         * The `type_source` of [sentType] for a caller that did not get it from [resolve]: anything
         * other than the name's own type can only have come from the file's contents.
         */
        fun sourceOf(path: String, sentType: String): TypeSource {
            val nameType = MimeTypeUtil.getMimeType(File(path))

            return if (sentType != nameType) TypeSource.CONTENT else nameOnlySource(nameType)
        }

        private fun nameOnlySource(nameType: String): TypeSource =
            if (nameType == ANY_TYPE) TypeSource.NONE else TypeSource.EXTENSION

        /**
         * Runs [read], turning every failure into [SniffOutcome.Unreadable]. A missing file, a
         * removed SD card or a denied permission is the user's environment, not a bug, and the open
         * that follows reports its own failures, so only the unexpected is reported.
         */
        private inline fun guarded(read: () -> SniffOutcome): SniffOutcome = try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            SniffOutcome.Unreadable
        } catch (_: SecurityException) {
            SniffOutcome.Unreadable
        } catch (e: Exception) {
            ErrorReporter.warning(e.scrubbed(), "sniff_mime_type")
            SniffOutcome.Unreadable
        }

        /**
         * Reads the first [MimeTypeSniffer.PREFIX_BYTES] of [file] and names the type they prove,
         * refining a ZIP from its central directory. Blocking, and it may throw: callers go through
         * [guarded].
         *
         * Only regular files are opened: a directory has no contents, and a FIFO or device node
         * could block the open itself.
         */
        internal fun readAndSniff(file: File): SniffOutcome {
            if (!file.isFile) return SniffOutcome.Unreadable
            if (file.length() == 0L) return SniffOutcome.NoMatch

            val prefix = FileInputStream(file).use { readPrefix(it) }
            val type = MimeTypeSniffer.sniff(prefix.bytes, prefix.length, prefix.reachedEof)
                ?: return SniffOutcome.NoMatch

            return SniffOutcome.Match(if (type == ZIP_TYPE) zipType(file) else type)
        }

        /** Up to [MimeTypeSniffer.PREFIX_BYTES] from [input], and whether it ended within them. */
        internal fun readPrefix(input: InputStream): Prefix {
            val bytes = ByteArray(MimeTypeSniffer.PREFIX_BYTES)
            var length = 0

            while (length < bytes.size) {
                val read = input.read(bytes, length, bytes.size - length)
                if (read < 0) return Prefix(bytes, length, reachedEof = true)
                length += read
            }

            return Prefix(bytes, length, reachedEof = false)
        }

        /**
         * Which member of the ZIP family [file] is, from its entry names: EPUB, ODF, OOXML and APK
         * are all ZIP archives. One whose directory cannot be read — a truncated download, a corrupt
         * archive, an entry name the platform refuses — stays a plain ZIP, so the extract dialog
         * opens and reports it the way it reports any unreadable archive.
         */
        private fun zipType(file: File): String = try {
            ZipFile(file).use { zip ->
                val mimetype = zip.getEntry("mimetype")?.let { entry ->
                    zip.getInputStream(entry).use { input ->
                        val bytes = ByteArray(MIMETYPE_ENTRY_BYTES)
                        var length = 0
                        while (length < bytes.size) {
                            val read = input.read(bytes, length, bytes.size - length)
                            if (read < 0) break
                            length += read
                        }
                        String(bytes, 0, length, Charsets.US_ASCII)
                    }
                }

                MimeTypeSniffer.zipType(zip.entries().asSequence().map { it.name }, mimetype)
            }
        } catch (e: IOException) {
            if (!isUnreadableZip(e)) throw e
            ZIP_TYPE
        } catch (_: IllegalArgumentException) {
            // Older platform versions report an entry name that is not valid UTF-8 — common in
            // archives made on Windows — this way rather than as a ZipException.
            ZIP_TYPE
        }
    }

    /** The start of a file: [length] valid bytes of [bytes]. */
    internal class Prefix(val bytes: ByteArray, val length: Int, val reachedEof: Boolean)
}

/** [this] unchanged when [type] already is its type, so equality-based state and tests stay stable. */
internal fun FileItem.withMimeType(type: String): FileItem =
    if (type == mimeType) this else copy(mimeType = type)
