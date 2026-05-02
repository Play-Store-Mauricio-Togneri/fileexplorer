package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationResultTest {

    @Test
    fun `Success contains correct data`() {
        val result: FileOperationResult<String> = FileOperationResult.Success("test data")

        assertTrue(result is FileOperationResult.Success)
        assertEquals("test data", (result as FileOperationResult.Success).data)
    }

    @Test
    fun `Success works with different types`() {
        val intResult: FileOperationResult<Int> = FileOperationResult.Success(42)
        val listResult: FileOperationResult<List<String>> = FileOperationResult.Success(listOf("a", "b"))

        assertEquals(42, (intResult as FileOperationResult.Success).data)
        assertEquals(listOf("a", "b"), (listResult as FileOperationResult.Success).data)
    }

    @Test
    fun `Error contains message and type`() {
        val result: FileOperationResult<String> = FileOperationResult.Error(
            message = "File not found",
            errorType = ErrorType.FILE_NOT_FOUND
        )

        assertTrue(result is FileOperationResult.Error)
        val error = result as FileOperationResult.Error
        assertEquals("File not found", error.message)
        assertEquals(ErrorType.FILE_NOT_FOUND, error.errorType)
        assertNull(error.cause)
    }

    @Test
    fun `Error contains cause when provided`() {
        val exception = IllegalStateException("Something went wrong")
        val result: FileOperationResult<String> = FileOperationResult.Error(
            message = "Operation failed",
            cause = exception,
            errorType = ErrorType.GENERIC
        )

        val error = result as FileOperationResult.Error
        assertEquals(exception, error.cause)
    }

    @Test
    fun `Error defaults to GENERIC type`() {
        val result: FileOperationResult<String> = FileOperationResult.Error(
            message = "Unknown error"
        )

        val error = result as FileOperationResult.Error
        assertEquals(ErrorType.GENERIC, error.errorType)
    }

    @Test
    fun `ErrorType enum has all expected values`() {
        val expectedTypes = listOf(
            ErrorType.GENERIC,
            ErrorType.PERMISSION_DENIED,
            ErrorType.FILE_NOT_FOUND,
            ErrorType.STORAGE_FULL,
            ErrorType.NAME_CONFLICT,
            ErrorType.OPERATION_CANCELLED
        )

        assertEquals(expectedTypes.size, ErrorType.entries.size)
        expectedTypes.forEach { type ->
            assertTrue(ErrorType.entries.contains(type))
        }
    }

    @Test
    fun `can pattern match on Success and Error`() {
        val success: FileOperationResult<Int> = FileOperationResult.Success(100)
        val error: FileOperationResult<Int> = FileOperationResult.Error("Failed", errorType = ErrorType.GENERIC)

        val successValue = when (success) {
            is FileOperationResult.Success -> success.data
            is FileOperationResult.Error -> -1
        }

        val errorValue = when (error) {
            is FileOperationResult.Success -> error.data
            is FileOperationResult.Error -> -1
        }

        assertEquals(100, successValue)
        assertEquals(-1, errorValue)
    }
}
