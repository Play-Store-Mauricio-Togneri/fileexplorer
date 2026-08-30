package com.mauriciotogneri.fileexplorer.data.util

import android.system.OsConstants
import androidx.annotation.StringRes
import com.mauriciotogneri.fileexplorer.R

/**
 * Why a delete did not happen, as far as the errno behind it says.
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
enum class DeleteFailure(val analyticsLabel: String, @param:StringRes val messageResId: Int) {
    /** The volume, the parent directory, or SELinux refused. */
    PERMISSION_DENIED("permission_denied", R.string.delete_error_permission),

    /** A volume mounted read-only, which no permission this app can hold will change. */
    READ_ONLY("read_only", R.string.delete_error_read_only),

    /**
     * A directory still had entries when it was removed, which for this app almost always means the
     * walk could not see them rather than that it skipped them: `list()` answers null for a
     * directory it may not read — `Android/data` and `Android/obb` on Android 11+, even with All
     * Files Access — and `forEachChild` then returns as if it were empty. A tree that grew a new
     * file while it was being walked lands here too.
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
    UNKNOWN("unknown", R.string.delete_error)
}

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
 * Classifies [errno] — as [deleteReturningErrno] reports it, so null means no errno was attached
 * rather than that the delete succeeded.
 *
 * ENOENT is deliberately absent: [deleteReturningErrno] resolves an already-empty path to success
 * before this is ever consulted.
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
