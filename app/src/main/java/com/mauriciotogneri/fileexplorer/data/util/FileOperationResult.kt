package com.mauriciotogneri.fileexplorer.data.util

/**
 * Represents the result of a file operation, either success with data or an error.
 */
sealed class FileOperationResult<out T> {
    data class Success<T>(val data: T) : FileOperationResult<T>()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val errorType: ErrorType = ErrorType.GENERIC
    ) : FileOperationResult<Nothing>()
}

/**
 * Types of errors that can occur during file operations.
 */
enum class ErrorType {
    GENERIC,
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    STORAGE_FULL,
    NAME_CONFLICT,
    OPERATION_CANCELLED
}
