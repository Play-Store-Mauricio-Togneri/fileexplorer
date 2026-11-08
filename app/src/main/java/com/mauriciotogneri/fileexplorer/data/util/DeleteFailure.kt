package com.mauriciotogneri.fileexplorer.data.util

import android.system.OsConstants
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.R

/**
 * Why a delete did not happen: mostly as far as the errno behind it says, and for two causes as far
 * as the walk itself saw. [SYSTEM_FOLDER] and [HIDDEN_CONTENTS] are things Android withholds from
 * this app by design; the errno they end in ("not empty", "busy") would blame items the user cannot
 * see, so the walk names them instead. See [keptByAndroid].
 *
 * One type rather than a label for analytics and a separate `when` for the message, because the two
 * would drift: the value of naming the cause at all is that the event on the dashboard and the
 * message the user read are the same claim, and a mapping kept in two places stops guaranteeing
 * that the first time one of them gains a case.
 *
 * [UNKNOWN] is not a cause but the absence of one, and stays reachable on purpose. Every delete
 * failure in this app used to report it — `File.delete()` answers a bare false — and it is still
 * the honest answer where no [android.system.ErrnoException] reached us: the JVM, which has no such
 * class, and any failure not raised by a syscall.
 */
enum class DeleteFailure(
    val analyticsLabel: String,
    @param:StringRes val messageResId: Int,
    /**
     * True for the causes that are Android's design rather than something gone wrong: what is left
     * over is what Android never lets this app remove, so the user gets the reason instead of a
     * failure count. Retrying never helps with these.
     */
    val keptByAndroid: Boolean = false
) {
    /** The volume, the parent directory, or SELinux refused. */
    PERMISSION_DENIED("permission_denied", R.string.delete_error_permission),

    /** A volume mounted read-only, which no permission this app can hold will change. */
    READ_ONLY("read_only", R.string.delete_error_read_only),

    /**
     * A directory still had entries when it was removed even though the walk could see into it —
     * typically a file written into the tree while it was being deleted. A directory the walk could
     * not list is [HIDDEN_CONTENTS] or [SYSTEM_FOLDER] instead, which is where `Android/data` and
     * `Android/obb` on Android 11+ used to land under this name.
     */
    NOT_EMPTY("not_empty", R.string.delete_error_not_empty),

    /** A mount point, or a file the kernel is executing from. */
    BUSY("busy", R.string.delete_error_busy),

    /** Removable storage unmounted mid-delete; the set [isStorageUnavailable] answers true for. */
    STORAGE_UNAVAILABLE("storage_unavailable", R.string.delete_error_storage_unavailable),

    /**
     * A syscall failed with something not listed above. Reported with the errno itself as an event
     * parameter, which is the point of keeping this apart from [UNKNOWN]: a case that turns out to
     * be common on real devices can then be given its own message, and one that never appears
     * costs nothing.
     */
    OTHER("errno_other", R.string.delete_error),

    /** No errno was attached. See the class comment. */
    UNKNOWN("unknown", R.string.delete_error),

    /**
     * The walk left a volume's `Android/data` or `Android/obb` in place, which Android 11+ never lets
     * this app remove. See [isProtectedSystemFolder].
     */
    SYSTEM_FOLDER("system_folder", R.string.delete_error_system_folder, keptByAndroid = true),

    /**
     * A folder the walk could not list would not go: Android hid its contents from this app, so the
     * folder only looked empty. The fallback for a protected folder [isProtectedSystemFolder] does
     * not know by path.
     */
    HIDDEN_CONTENTS("hidden_contents", R.string.delete_error_hidden_items, keptByAndroid = true)
}

/**
 * What the delete walk knew about the node behind a failure's errno, for the analytics event: a
 * file, a folder whose listing failed, a folder that listed empty yet would not go (entries hidden
 * from the listing itself, which the failed-listing case cannot see), or a folder with entries.
 */
enum class FailedListing(val analyticsLabel: String) {
    FILE("file"),
    UNLISTED("unlisted"),
    EMPTY("empty"),
    LISTED("listed")
}

/**
 * Everything the analytics event says about a failed delete, in one value so the screens pass it
 * through without picking it apart. Kept here rather than in the repository so that
 * [AnalyticsTracker] does not depend on `data/repository`.
 *
 * @param cause what the user was told, by [deleteFailureFor]'s precedence.
 * @param errno the errno behind the first failure, depth-first, explained or not — so data from
 * before and after [DeleteFailure.keptByAndroid] existed stays comparable.
 * @param systemFolders protected folders the walk left in place.
 * @param unlistedFolders folders it could not list that then stayed.
 * @param failedListing what the walk knew about the node behind [errno]; null when no node failed.
 * @param failedLocation roughly where that node is; null when no node failed.
 */
@Immutable
data class DeleteDiagnostics(
    val cause: DeleteFailure,
    val errno: Int?,
    val systemFolders: Int,
    val unlistedFolders: Int,
    val failedListing: FailedListing?,
    val failedLocation: DeleteLocation?
)

/**
 * Stands for a delete that failed without an errno to explain it, which the platform never
 * produces and a caller without [android.system.Os] does — the `removeFile` seam
 * [com.mauriciotogneri.fileexplorer.data.repository.FileRepository] takes for JVM tests.
 *
 * Zero is safe as that marker because errno 0 is success: no failing syscall can report it, so it
 * cannot collide with a real cause.
 */
const val ERRNO_UNKNOWN: Int = 0

/**
 * The errno worth putting on an analytics event, or null when the failure carried none.
 *
 * [ERRNO_UNKNOWN] is filtered rather than reported as `0`, which would read on the dashboard as a
 * cause rather than as their absence.
 */
fun reportableErrno(errno: Int?): Int? = errno?.takeIf { it != ERRNO_UNKNOWN }

/**
 * Classifies [errno] — as [removePath] reports it, so null means no errno was attached rather than
 * that the delete succeeded.
 *
 * ENOENT is deliberately absent: [removePath] resolves every path that already held nothing to
 * [RemoveOutcome.AlreadyAbsent] or [RemoveOutcome.Unresolvable] before this is ever consulted, and
 * neither carries an errno here. What does reach this from that branch is the parent's errno, so
 * the volume causes below answer for an ancestor that went as readily as for the file itself.
 *
 * Cannot be exercised as a JVM unit test, for a reason worth stating because the failure would be
 * silent rather than loud: every [OsConstants] field is a stub that reads 0 off device, so all of
 * the branches below collapse onto [ERRNO_UNKNOWN] and a test would assert that rather than the
 * real mapping. `FileAccessTest` covers it on a device.
 */
fun deleteFailureFor(errno: Int?): DeleteFailure = when (errno) {
    null, ERRNO_UNKNOWN -> DeleteFailure.UNKNOWN
    OsConstants.EACCES, OsConstants.EPERM -> DeleteFailure.PERMISSION_DENIED
    OsConstants.EROFS -> DeleteFailure.READ_ONLY
    OsConstants.ENOTEMPTY, OsConstants.EEXIST -> DeleteFailure.NOT_EMPTY
    OsConstants.EBUSY, OsConstants.ETXTBSY -> DeleteFailure.BUSY
    OsConstants.ENOTCONN,
    OsConstants.ECONNABORTED,
    OsConstants.EIO,
    OsConstants.ENODEV,
    OsConstants.ENXIO -> DeleteFailure.STORAGE_UNAVAILABLE

    else -> DeleteFailure.OTHER
}

/**
 * What to tell the user about a delete that left something behind, worked out from the walk's whole
 * result rather than from its first errno alone.
 *
 * A visible failure wins: only it is about an item the user can see, and it is what the errno-based
 * messages describe correctly. Otherwise a protected folder, then a folder the walk could not list.
 * A result with none of the three — one built without these fields — classifies its first errno, as
 * every delete did before them.
 *
 * @param unexplainedErrno the errno of the first failure the walk could not put down to Android's
 * design; [ERRNO_UNKNOWN] when that failure carried none.
 * @param firstErrno the errno of the first failure of any kind.
 */
fun deleteFailureFor(
    unexplainedErrno: Int?,
    systemFolders: Int,
    unlistedFolders: Int,
    firstErrno: Int?
): DeleteFailure = when {
    unexplainedErrno != null -> deleteFailureFor(unexplainedErrno)
    systemFolders > 0 -> DeleteFailure.SYSTEM_FOLDER
    unlistedFolders > 0 -> DeleteFailure.HIDDEN_CONTENTS
    else -> deleteFailureFor(firstErrno)
}

/**
 * Whether [errno] says the whole volume went away or is read-only. Such a failure is never put down
 * to Android hiding things, even inside a protected or unlisted folder, so a volume that drops out
 * mid-delete keeps its true message.
 */
internal fun isVolumeLevelFailure(errno: Int): Boolean =
    when (deleteFailureFor(errno)) {
        DeleteFailure.STORAGE_UNAVAILABLE, DeleteFailure.READ_ONLY -> true
        else -> false
    }
